"""Robot Framework keywords for running RelayForge delivery acceptance tests."""

from __future__ import annotations

import json
import os
import subprocess
import time
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen
from uuid import uuid4


class RelayForgeLibrary:
    """Control the local Docker stack and verify API-to-receiver delivery flows."""

    ROBOT_LIBRARY_SCOPE = "SUITE"

    def __init__(self) -> None:
        self.project_root = Path(__file__).resolve().parents[2]
        self.api_url = os.getenv("RELAYFORGE_API_URL", "http://localhost:8081").rstrip("/")
        self.receiver_url = os.getenv("RELAYFORGE_RECEIVER_URL", "http://localhost:9091").rstrip("/")
        self.endpoint_url_base = os.getenv(
            "RELAYFORGE_RECEIVER_ENDPOINT_BASE", "http://receiver:9090"
        ).rstrip("/")

    def start_local_stack(self) -> None:
        """Build and start PostgreSQL, the API, Console, and test receiver."""
        self._run_compose("up", "--detach", "--build")
        self._wait_for("/api/v1/dashboard/summary", "RelayForge API")
        self._wait_for_receiver()

    def stop_local_stack(self) -> None:
        """Stop local containers while preserving the database volume."""
        self._run_compose("down")

    def set_receiver_response(self, status: int) -> str:
        """Return the test receiver URL that responds with the requested HTTP status."""
        status = int(status)
        if status < 200 or status > 599:
            raise ValueError("Receiver status must be between 200 and 599")
        if status == 200:
            path = "/accept"
        elif status == 503:
            path = "/fail"
        else:
            path = f"/status/{status}"
        return f"{self.endpoint_url_base}{path}"

    def clear_receiver_requests(self) -> None:
        self._request(self.receiver_url, "/requests", method="DELETE")

    def configure_endpoint_for_event(self, event_type: str, status: int) -> dict[str, Any]:
        """Create or reuse a test endpoint and its one-second retry subscription."""
        url = self.set_receiver_response(status)
        endpoint = self.register_endpoint_url(url, f"Robot {event_type}")

        if not endpoint["active"]:
            endpoint = self.set_endpoint_active(endpoint["id"], True)

        self.ensure_subscription(endpoint["id"], event_type, 1)
        return endpoint

    def register_endpoint_url(self, url: str, name: str = "Robot endpoint") -> dict[str, Any]:
        """Create or reuse an endpoint with the supplied URL."""
        endpoints = self._request(self.api_url, "/api/v1/endpoints")
        endpoint = next((item for item in endpoints if item["url"] == url), None)
        if endpoint is None:
            endpoint = self._request(
                self.api_url,
                "/api/v1/endpoints",
                method="POST",
                payload={
                    "name": name,
                    "url": url,
                    "description": "Managed by the RelayForge Robot Framework suite",
                },
            )
        return endpoint

    def set_endpoint_active(self, endpoint_id: str, active: bool) -> dict[str, Any]:
        """Activate or deactivate an endpoint."""
        return self._request(
            self.api_url,
            f"/api/v1/endpoints/{endpoint_id}/status",
            method="PATCH",
            payload={"active": active},
        )

    def ensure_subscription(
        self, endpoint_id: str, event_type: str, retry_interval_seconds: int | None = 1
    ) -> dict[str, Any] | None:
        """Create a subscription or update its retry interval when requested."""
        subscriptions_path = f"/api/v1/endpoints/{endpoint_id}/subscriptions"
        subscriptions = self._request(self.api_url, subscriptions_path)
        subscription = next(
            (item for item in subscriptions if item["eventType"] == event_type), None
        )
        if subscription is None:
            payload: dict[str, Any] = {"eventType": event_type}
            if retry_interval_seconds is not None:
                payload["retryIntervalSeconds"] = retry_interval_seconds
            return self._request(
                self.api_url,
                subscriptions_path,
                method="POST",
                payload=payload,
            )
        if (
            retry_interval_seconds is not None
            and subscription["retryIntervalSeconds"] != retry_interval_seconds
        ):
            self._request(
                self.api_url,
                f"{subscriptions_path}/{quote(event_type, safe='')}",
                method="PATCH",
                payload={"retryIntervalSeconds": retry_interval_seconds},
            )
        return subscription

    def create_subscription_with_default_retry(
        self, endpoint_id: str, event_type: str
    ) -> dict[str, Any]:
        """Create a subscription without an interval and return its API response."""
        subscription = self.ensure_subscription(endpoint_id, event_type, None)
        if subscription is None:
            raise AssertionError("Subscription was not created")
        return subscription

    def create_event(
        self, event_type: str, idempotency_key: str | None = None
    ) -> dict[str, Any]:
        """Create a uniquely identified event for the configured subscription."""
        run_id = idempotency_key or str(uuid4())
        return self._request(
            self.api_url,
            "/api/v1/events",
            method="POST",
            headers={"Idempotency-Key": f"robot-{run_id}"},
            payload={"eventType": event_type, "payload": {"robotRunId": run_id}},
        )

    def create_unique_event_type(self, prefix: str) -> str:
        return f"{prefix}.{uuid4().hex}"

    def create_missing_event_id(self) -> str:
        return str(uuid4())

    def wait_for_event_deliveries(self, event_id: str) -> dict[str, Any]:
        """Wait until the event and every delivery job reach terminal states."""
        deadline = time.monotonic() + 60
        event = None
        deliveries: list[dict[str, Any]] = []
        while time.monotonic() < deadline:
            event = self._request(self.api_url, f"/api/v1/events/{event_id}")
            deliveries = self._request(
                self.api_url, f"/api/v1/events/{event_id}/deliveries"
            )
            terminal = all(
                item["status"] in {"DELIVERED", "FAILED", "CANCELLED"}
                for item in deliveries
            )
            if event["status"] == "DONE" and terminal:
                break
            time.sleep(0.25)
        if event is None:
            raise AssertionError(f"Event {event_id} was not found")
        return {
            "event_status": event["status"],
            "delivery_count": len(deliveries),
            "delivery_statuses": [item["status"] for item in deliveries],
            "deliveries": deliveries,
        }

    def event_deliveries_http_status(self, event_id: str) -> int:
        """Return the HTTP status for looking up the event's deliveries."""
        request = Request(
            f"{self.api_url}/api/v1/events/{event_id}/deliveries",
            headers={"Accept": "application/json"},
        )
        try:
            with urlopen(request, timeout=10) as response:
                return response.status
        except HTTPError as error:
            error.close()
            return error.code

    def endpoint_registration_http_status(self, url: str) -> int:
        """Return the HTTP status when registering a test endpoint URL."""
        request = Request(
            f"{self.api_url}/api/v1/endpoints",
            data=json.dumps({"name": "Robot invalid URL", "url": url}).encode("utf-8"),
            headers={"Accept": "application/json", "Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urlopen(request, timeout=10) as response:
                return response.status
        except HTTPError as error:
            error.close()
            return error.code

    def wait_for_delivery(self, event_id: str, endpoint_id: str) -> dict[str, Any]:
        """Wait for a terminal delivery and return status and captured-request counts."""
        deadline = time.monotonic() + 60
        delivery = None
        event = None
        captured: list[dict[str, Any]] = []
        while time.monotonic() < deadline:
            deliveries = self._request(
                self.api_url, f"/api/v1/events/{event_id}/deliveries"
            )
            delivery = next(
                (item for item in deliveries if item["endpointId"] == endpoint_id), None
            )
            event = self._request(self.api_url, f"/api/v1/events/{event_id}")
            requests = self._request(self.receiver_url, "/requests")
            delivery_id = delivery["id"] if delivery else None
            captured = [
                item
                for item in requests
                if item.get("headers", {}).get("x-relayforge-delivery") == delivery_id
            ]
            if (
                delivery
                and delivery["status"] in {"DELIVERED", "FAILED", "CANCELLED"}
                and event["status"] == "DONE"
            ):
                break
            time.sleep(0.35)

        if delivery is None or event is None:
            raise AssertionError(f"No delivery was created for event {event_id}")
        return {
            "event_status": event["status"],
            "delivery_status": delivery["status"],
            "attempt_count": delivery["attemptCount"],
            "captured_request_count": len(captured),
            "delivery_id": delivery["id"],
        }

    def _run_compose(self, *args: str) -> None:
        subprocess.run(
            ["docker", "compose", *args],
            cwd=self.project_root,
            check=True,
        )

    def _wait_for(self, path: str, label: str, timeout: int = 120) -> None:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                self._request(self.api_url, path)
                print(f"{label} is ready")
                return
            except (AssertionError, OSError, URLError):
                time.sleep(1)
        raise TimeoutError(f"{label} did not become ready; check `./rf logs`")

    def _wait_for_receiver(self, timeout: int = 120) -> None:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                self._request(self.receiver_url, "/health")
                print("Retry receiver is ready")
                return
            except (AssertionError, OSError, URLError):
                time.sleep(1)
        raise TimeoutError("Retry receiver did not become ready; check `./rf logs receiver`")

    @staticmethod
    def _request(
        base_url: str,
        path: str,
        method: str = "GET",
        payload: dict[str, Any] | None = None,
        headers: dict[str, str] | None = None,
    ) -> Any:
        request_headers = {"Accept": "application/json", **(headers or {})}
        data = None
        if payload is not None:
            data = json.dumps(payload).encode("utf-8")
            request_headers["Content-Type"] = "application/json"
        request = Request(
            f"{base_url}{path}", data=data, headers=request_headers, method=method
        )
        try:
            with urlopen(request, timeout=10) as response:
                response_body = response.read()
        except HTTPError as error:
            response_body = error.read()
            try:
                detail = json.loads(response_body).get("message", response_body.decode())
            except (ValueError, AttributeError):
                detail = response_body.decode(errors="replace")
            raise AssertionError(f"{method} {path} returned HTTP {error.code}: {detail}") from error
        return json.loads(response_body) if response_body else None

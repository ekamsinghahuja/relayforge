# RelayForge Robot Framework tests

These are Python-based acceptance tests written in Robot Framework. The keyword library uses Python's standard library for HTTP requests; Robot Framework is the only Python dependency.

```sh
./mvnw test        # Java unit and API tests
./rf test all       # Start/build Docker, then run the acceptance suite
./rf test accept    # Verify HTTP 200 is delivered once
./rf test retry     # Verify HTTP 503 retries end in FAILED
```

The first test run creates `tools/robot/.venv` and installs the pinned dependency from `requirements.txt`. Robot's `output.xml`, `log.html`, and `report.html` are written under `tools/robot/results/`.

The suite setup runs `docker compose up --detach --build` and waits for the API and receiver. The stack remains running after the tests. Use `./rf down` when you want to stop it; this preserves the PostgreSQL volume.

## Reusable keywords

`RelayForgeLibrary.py` exposes Robot keywords to start/stop the stack, choose the test receiver's response, clear captured requests, configure endpoint subscriptions, create events, and wait for delivery outcomes. For example, `Set Receiver Response  200` returns the URL ending in `/accept`; `Set Receiver Response  503` returns `/fail`; another status from 200 through 599 uses `/status/<code>`. The response is selected by the endpoint URL, so different endpoints can return different statuses at the same time.

The acceptance suite covers successful delivery, retry exhaustion, non-retryable HTTP 400, fan-out to multiple endpoints, inactive endpoint filtering, unmatched events, idempotency, the default retry interval, receiver URL validation, and 404 for unknown events. Delivery scenarios verify that event status reaches `DONE` and captured receiver requests match recorded attempt counts.

Docker publishes the API at `http://localhost:8081`, the receiver at `http://localhost:9091`, and the Console at `http://localhost:3000`. The API container reaches the receiver at `http://receiver:9090`. Host ports 8080 and 9090 are left available for host-run services.

To test against a host-run API while keeping the receiver in Docker, run:

```sh
RELAYFORGE_API_URL=http://localhost:8080 \
RELAYFORGE_RECEIVER_ENDPOINT_BASE=http://localhost:9091 \
./rf test all
```

The Docker API enables the `local-test` profile. That profile allows only the test receiver's specific hosts, ports, and paths; do not enable it in deployed environments.

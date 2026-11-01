# RelayForge

RelayForge accepts JSON events, routes each event to active endpoints subscribed to its exact event type, and stores durable delivery jobs in PostgreSQL. A scheduled worker claims jobs with `FOR UPDATE SKIP LOCKED`; event ingestion commits before HTTP delivery begins.

```mermaid
flowchart LR
  API[Spring MVC API] --> DB[(PostgreSQL / Flyway)]
  DB --> Worker[Scheduled delivery worker]
  Worker -->|HTTP POST| Destination[Subscribed endpoint]
```

## Local setup

Requires Java 21, Maven wrapper, and Docker Compose. Start PostgreSQL and pgAdmin with `docker compose up -d postgres pgadmin`, then run `./mvnw spring-boot:run`. The database is available at `localhost:5432`; pgAdmin is at `http://localhost:5050`. Development database credentials are defined in `compose.yaml` and can be overridden for the application with `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`. Set production secrets outside tracked files.

Flyway applies migrations automatically at startup. The current schema includes V1–V7; never edit an applied migration, add the next version instead.

## API

Create endpoint, then subscribe it to event types:

```sh
curl -i -X POST http://localhost:8080/api/v1/endpoints \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.org/hooks/relayforge","description":"Example receiver"}'
curl -i -X POST http://localhost:8080/api/v1/endpoints/ENDPOINT_UUID/subscriptions \
  -H 'Content-Type: application/json' -d '{"eventType":"payment.completed"}'
```

Create idempotent events and inspect delivery jobs:

```sh
curl -i -X POST http://localhost:8080/api/v1/events \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: payment-123' \
  -d '{"eventType":"payment.completed","payload":{"paymentId":"pay_123","amount":500}}'
curl 'http://localhost:8080/api/v1/events?eventType=payment.completed&page=0&size=20'
curl http://localhost:8080/api/v1/events/EVENT_UUID
curl http://localhost:8080/api/v1/events/EVENT_UUID/deliveries
curl 'http://localhost:8080/api/v1/deliveries?status=RETRY_WAIT&page=0&size=20'
curl http://localhost:8080/api/v1/deliveries/DELIVERY_UUID
curl http://localhost:8080/api/v1/deliveries/DELIVERY_UUID/attempts
curl -X POST http://localhost:8080/api/v1/deliveries/DELIVERY_UUID/retry
curl http://localhost:8080/api/v1/dashboard/summary
```

Event types match exactly. Subscription changes affect newly created events; existing delivery jobs remain. Event creation and the initial delivery set are transactional. Optional idempotency keys are unique; reusing a key with different event input returns 409. Delivery is at least once: a receiver may have accepted a request before the worker loses its lease, so receivers should deduplicate by `X-RelayForge-Delivery` when available.

Delivery states are PENDING, PROCESSING, RETRY_WAIT, DELIVERED, FAILED, and CANCELLED. Retryable outcomes are connection/timeouts, HTTP 408, 429 and 5xx; other HTTP statuses fail permanently. `RELAYFORGE_MAX_ATTEMPTS`, `RELAYFORGE_INITIAL_BACKOFF_MS`, and `RELAYFORGE_MAX_BACKOFF_MS` configure bounded exponential backoff. Manual retry resets the current run's attempt counter to zero; prior attempt rows remain as the lifetime audit.

To enable endpoint signing, set `RELAYFORGE_ENCRYPTION_KEY` to the base64 encoding of a random 32-byte AES key, then register an endpoint with `{"signingEnabled":true,"signingSecret":"..."}`. The secret is encrypted with AES-256-GCM at rest and omitted from API responses. Signed requests include event and delivery UUID headers, `X-RelayForge-Timestamp` (Unix seconds), and `X-RelayForge-Signature: sha256=<hex>`. Verify HMAC-SHA256 over the UTF-8 bytes of `<timestamp>.<raw request body>` using the endpoint secret; compare signatures in constant time and reject old timestamps to limit replays. A new encryption key cannot decrypt secrets encrypted with a previous key; rotate by updating secrets before removing the old key.

## Security and limitations

The worker validates endpoint URLs at registration and before delivery, rejects credentials and non-public IP ranges, and disables redirects. DNS is validated separately from the HTTP client's eventual resolution, so DNS rebinding is not fully prevented. Do not use this as a production security boundary without pinning the validated address in the actual connection. Management APIs currently have no authentication and must not be exposed publicly. Do not submit secrets in URLs.

## Configuration and tests

Worker defaults: 5-second polling, batch size 25, 60-second lease. Override with `relayforge.delivery.*` properties. CORS defaults to `http://localhost:5173` and can be changed using `RELAYFORGE_CORS_ORIGIN`.

For local delivery testing, run `node tools/retry-test-receiver/server.mjs` in one terminal and start the backend with `SPRING_PROFILES_ACTIVE=local-test ./mvnw spring-boot:run` in another. Register `http://localhost:9090/accept` to always receive HTTP 200 or `http://localhost:9090/fail` to always receive HTTP 503 and exercise retries. See [the receiver guide](tools/retry-test-receiver/README.md) to inspect captured requests. The local-test profile allows only those exact URLs through the destination validator and must not be used in deployed environments.

Run `./mvnw test` or `./mvnw clean package`. Integration tests use the configured local PostgreSQL database and exercise its locking and migrations. The implementation currently has no production authentication, DNS pinning, response-body capture, or Retry-After support.

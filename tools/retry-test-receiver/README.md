# Local webhook test receiver

This dependency-free Node.js server listens only on `127.0.0.1:9090`. Choose its behavior by registering a different endpoint URL:

- `http://localhost:9090/accept` always responds with HTTP 200.
- `http://localhost:9090/fail` always responds with HTTP 503, which exercises RelayForge's retry policy.

## Run

From the repository root:

```sh
node tools/retry-test-receiver/server.mjs
```

Start Spring Boot with the local test profile so the endpoint validator permits only those exact receiver URLs:

```sh
SPRING_PROFILES_ACTIVE=local-test ./mvnw spring-boot:run
```

Keep the profile local; do not enable it in deployed environments.

## Inspect captured requests

```sh
curl http://localhost:9090/health
curl http://localhost:9090/requests
curl -i -X DELETE http://localhost:9090/requests
```

Captured requests include their URL path, response status, body, and the RelayForge event, delivery, and signing headers. They are stored in memory and cleared when the process exits. To stop the receiver, press Ctrl+C.

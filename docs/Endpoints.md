# Endpoints (Draft)

## Protection

Endpoits should be protected:
 * Only authenticated and authorisaded clients and users should be able to access resources
 * Authentication endpoints should have rate limiting and brute-force protection
 * See [MaxActiveRequests](https://http4s.org/v1/docs/server-middleware.html#maxactiverequests) or [Throttle](https://http4s.org/v1/docs/server-middleware.html#maxactiverequests)
 * [EntityLimiter]I(https://http4s.org/v1/docs/server-middleware.html#entitylimiter) ensures the request body is under a specific length. Could be useful to prevent huge documents being uploaded to the storage.

## Metrics

Shall we use [http4s-prometheus-metrics](https://http4s.github.io/http4s-prometheus-metrics/) to collect metrics?
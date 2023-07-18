# Endpoints (Draft)

## Protection

Endpoits should be protected:
 * Only authenticated and authorisaded clients and users should be able to access resources
 * Authentication endpoints should have rate limiting and brute-force protection
 * See [MaxActiveRequests](https://http4s.org/v1/docs/server-middleware.html#maxactiverequests) or [Throttle](https://http4s.org/v1/docs/server-middleware.html#maxactiverequests)
 * [EntityLimiter]I(https://http4s.org/v1/docs/server-middleware.html#entitylimiter) ensures the request body is under a specific length. Could be useful to prevent huge documents being uploaded to the storage.
 * Codebase makes assumption on requests it will be receiving. (Examples?) Thus it might not be handling all the possible errors that might occur. Thus, it is important to make those errors (probably 500s) available somewhere for troubleshooting or auditing purposes.

## Metrics

Shall we use [http4s-prometheus-metrics](https://http4s.github.io/http4s-prometheus-metrics/) to collect metrics?
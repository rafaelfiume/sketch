# Endpoints (Draft)

## Protection

Endpoits should be protected:
 * Only authenticated and authorisaded clients and users should be able to access resources
 * Ensures the request body is under a specific length. See [EntityLimiter]I(https://http4s.org/v1/docs/server-middleware.html#entitylimiter)
 * Limit the number of active requests? Perhaps by business? See [MaxActiveRequests](https://http4s.org/v1/docs/server-middleware.html#maxactiverequests) or [Throttle](https://http4s.org/v1/docs/server-middleware.html#maxactiverequests)
 * 

## Metrics

Shall we use [http4s-prometheus-metrics](https://http4s.github.io/http4s-prometheus-metrics/) to collect metrics?
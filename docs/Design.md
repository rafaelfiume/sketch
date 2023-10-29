# Design

## Datastore

Despite of the disadvantages of storing binaries (images, pdf's, etc) in a database,
we are doing that anyway for the following reasons:

 * Easier implementation, makes it a good fit for a MVP: e.g. no need to keep pointers
 * We don't expect performance problems associated with binaries stored in the db: not too many, nor huge bins expected
 * Easier to keep them secure

## Endpoints (draft)

Endpoits should be protected:
 * Only authenticated and authorisaded clients and users should be able to access resources
 * Authentication endpoints should have rate limiting and brute-force protection
 * See [MaxActiveRequests](https://http4s.org/v1/docs/server-middleware.html#maxactiverequests) or [Throttle](https://http4s.org/v1/docs/server-middleware.html#maxactiverequests)
 * [EntityLimiter]I(https://http4s.org/v1/docs/server-middleware.html#entitylimiter) ensures the request body is under a specific length. Could be useful to prevent huge documents being uploaded to the storage.
 * Codebase makes assumption on requests it will be receiving. (Examples?) Thus it might not be handling all the possible errors that might occur. Thus, it is important to make those errors (probably 500s) available somewhere for troubleshooting or auditing purposes.

There must be metrics:
 * A good start can be [http4s-prometheus-metrics](https://http4s.github.io/http4s-prometheus-metrics/) to collect metrics.

# Error Handling (Draft)

'Error' means any kind of error, being its source customers (e.g. input validation) or system errors.

`ErrorInfo` should:
 - Be the response payload provided by endpoints in case of errors
 - Be tracked - see 'Tracking Errors' session below

Errors should always be tracked. 

Errors shouldn't be retained for too long Hopefully they will be quickly fixed or addressed in some way, and won't need to be retained for long. And if they don't, past information will likely become irrelevant.


Feito com ❤️ por Artigiani.

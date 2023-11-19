# Design

## Datastore

Despite of the disadvantages of storing binaries (images, pdf's, etc) in a database,
we are doing that anyway for the following reasons:

- Easier implementation, makes it a good fit for a MVP: e.g. no need to keep pointers
- We don't expect performance problems associated with binaries stored in the db: not too many, nor huge bins expected
- Easier to keep them secure

### Referencing Tables in Remote Database

There is a relationship between `domain.Documents` and `auth.Users` that currently is easy to manage by way of foreign keys.
The idea is to migrate `auth` schema to a separate database (tbc explain reasons).
A couple options then are Foreign Data Wrappers (FDW) and Cross-Database Joins.

## Layers & Models

Separation of concerns is a big deal in software development. Roughly speaking, a backend service consists of 3 layers:
presentation (http endpoints), domain (core business logic) and integration (datastore, kafka, external http resources).
Each one of these layers should have its own model.

In practice, we are starting with a model representing the request/response payloads - e.g. `DocumentResponsePayload` - and a domain model - for instance, `Document`.
As result, the integration layer (e.g. `PostgresDocumentsStore`) is implemented using the same core domain model (`Document`) in order to reduce the boiler plate and speed up development.
It is expected a `postgres.Document` will be extracted if necessary. When would it become necessary?
Changing a database table forcing changes in business domain classes is an example of
a sign a specific model for the integration layer is necessary.

Consider using [chimney](https://github.com/scalalandio/chimney) to reduce the boilerplate between `presentation model <-> domain model <-> integration model`.

Note: there are places where a presentation is implemented for a model directly by deriving circe `Encoder`s and `Decoder`s. See `ServiceStatus`.

### Presentation Model

Canonical shared objects like `ServiceStatus` might have json encoders and decoders defined without the need of an intermidiate model.

```
sketch/
├── shared/
│   ├── app/
│   │   ├── ServiceStatus.scala
│   │   |   ├── object json
│   │   |   |   └── given ...                # Canonical encoder/decoders for ServiceStatus
│   │   └── ...
│   ├── domain/
│   │   ├── documents/
|   |   |   ├── Document.scala               # No canonical encoder/decoders defined for Document.scala
│   │   |   └── ...
│   │   └── ...
│   └── ...
├── http/
│   ├── DocumentRoutes.scala
│   │   |   ├── object Model
│   │   |   |   ├── object json
│   │   |   |   |   └── given ...            # Document encoder/decoders definition
│   │   |   |   ├── object xml               # Any other format a model should be serialised to or deserialised from
│   │   |   |   |   └── ...
│   |   |   └── ...
└── ...
```

## Endpoints (draft)

Endpoits should be protected:

- Only authenticated and authorisaded clients and users should be able to access resources
- Authentication endpoints should have rate limiting and brute-force protection
- See [MaxActiveRequests](https://http4s.org/v1/docs/server-middleware.html#maxactiverequests) or [Throttle](https://http4s.org/v1/docs/server-middleware.html#maxactiverequests)
- [EntityLimiter]I(https://http4s.org/v1/docs/server-middleware.html#entitylimiter) ensures the request body is under a specific length. Could be useful to prevent huge documents being uploaded to the storage.
- Codebase makes assumption on requests it will be receiving. (Examples?) Thus it might not be handling all the possible errors that might occur. Thus, it is important to make those errors (probably 500s) available somewhere for troubleshooting or auditing purposes.

There must be metrics:

- A good start can be [http4s-prometheus-metrics](https://http4s.github.io/http4s-prometheus-metrics/) to collect metrics.

# Error Handling (Draft)

'Error' means any kind of error, being its source customers (e.g. input validation) or system errors.

`ErrorInfo` should:

- Be the response payload provided by endpoints in case of errors
- Be tracked - see 'Tracking Errors' session below

Errors should always be tracked.

Errors shouldn't be retained for too long Hopefully they will be quickly fixed or addressed in some way, and won't need to be retained for long. And if they don't, past information will likely become irrelevant.

Feito com ❤️ por Artigiani.

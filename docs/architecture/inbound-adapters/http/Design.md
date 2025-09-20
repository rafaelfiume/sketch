# Http Layer Desing

The Http layer provides public APIs that must securelly map external requests into the domain in a client-agnostic way.

**Table of Contents**

1. Goals
2. APIs
   - 2.1 REST APIs
   - 2.2 Streaming APIs
   - 2.3 Versioning
   - 2.4 Example
3. Shielding the Domain (Data Representation)
4. Error Handling
   - 4.1 Http Status Code
   - 4.2 Input Validation
5. Security
   - 5.1 Middlewares
   - 5.2 API Gateway (Future Direction)
6. Future Direction

## 1. Goals

* Expose functionalities to external services through Http API endpoints
* Support API evolution without breaking clients
* Shield the domain layer from external representations and concerns
* Handle errors gracefully, keeping the service intact and providing useful information to clients
* Ensure only authenticated and authorised requests reach the business layer (mostly through middlewares)
* Secure resources against too many requests and other security issues (through middlewares)

Note: part of the last two goals can be moved to an API gateway in the future


## 2. APIs

### 2.1 REST APIs

Functionalities must be exposed using **resource-oriented [REST](https://en.wikipedia.org/wiki/REST) endpoints**.

**Principles**:
  * **Stateless**: Requests include all the necessary information for processing
  * **Cache-friendly**: Response should be cacheable by servers or clients when appropriate.

**Conventions**:
  * Plural **nouns** to represent **resources** (e.g. `/documents`, `/users`)
  * Http **verbs** to represent **actions**
  * Http [**status codes**](https://en.wikipedia.org/wiki/List_of_HTTP_status_codes) to indicate **outcome** (see [Error Handling - Http Status Code]()).


| Verb          | Intent                                 | [Idempotency](https://en.wikipedia.org/wiki/Idempotence) |
|---------------|----------------------------------------|----------------------------------------------------------|
| `GET`         | Fetch resources                        | Required                                           |
| `POST`        | Create resources                       | Recommended - See [Stripe doc](https://stripe.com/blog/idempotency) |
| `PUT`         | Replace a resource                     | Required                                           |
| `PATCH`       | Partially updates a resource           | Required                                           |
| `DELETE`      | Remove  a resource                     | Required - Except for `204` vs. `404` status codes |

> **Idempotency:**<br>
> The capability of an operation to produce the **same outcome**, no matter how many times it is invoked with the **same input**.
> This enables **exactly-once semantics**, allowing **safe-retries** without the risk of duplicate side-effects or inconsistent states.

- - -

API endpoints must conform to **Level 2** of the [Maturity Model](https://en.wikipedia.org/wiki/Richardson_Maturity_Model#Level_2:_HTTP_verbs):
  * Do not use **RPC-style actions**:
    - Don't: `POST /getDocumentMetadata/${uuid}`
    - Do: `DELETE /removeUser/${uuid}`
  * **Level 3 ([HATEOAS](https://en.wikipedia.org/wiki/HATEOAS))** is not required, unless justified by a strong business case.

### 2.2 Streaming APIs

. . .

### 2.3 Versioning

**TLDR:**

Default to **versioning with client libraries** rather than `/v${number}` path segments unless there is a strong justification.

**Traditional Versioning:**

In any real-world software engineering, requirements change, functionalities evolve.

* Common pattern: `/v1/documents`, `/v2/users`.
* Trade-offs:
  - **Semantic Drift**: `/documents` changes meaning between `v1`, `v2`, etc. versions
  - **Cross-Channel Consistency**: Multiple channels (e.g. REST, Kafka topics) must stay in sync.
  - **Clients Migration Complexity**: Difficult to throw away old versions when clients cannot be easy updated.

**Alternative: Client Library:**

* Provide a **versioned client library** that encapsultes API calls.
* Benefits:
  - Stronger backwards compatibility guarantees
  - Simplifies version management
  - Improves developer ergonomics.

### 2.4 Example

Documents Management APIs:

| Method   | API Endpoint                 | Description         |
| -------- | ---------------------------- | ------------------- |
| `POST`   | `/documents`                 | Upload a document   |
| `GET`    | `/documents/{uuid}`          | Download a document |
| `GET`    | `/documents/{uuid}/metadata` | Fetch metadata      |
| `DELETE` | `/documents/{uuid}`          | Delete a document   |

> **Note:**<br>
> Metadata is a **sub-resource** because:
> * It is lightweight and frequently accessed independently of the full document
> * It might have different access control rules than the binary content.

> See [DocumentsRoutes](/service/src/main/scala/org/fiume/sketch/http/DocumentsRoutes.scala) and [DocumentsRoutesSpec](/service/src/test/scala/org/fiume/sketch/http/DocumentsRoutesSpec.scala).


## 3. Shielding the Domain (Data Representation)

Use [DTO](https://martinfowler.com/eaaCatalog/dataTransferObject.html)s to represent **request and response payloads**,
decoupling the business layer from external clients concerns.

> **Rule of Thumb:**<br>
> Changes in API payloads **must not leak** into the domain model, and domain changes must not break API contracts.

```
[ Client Json ] ------ (json) ------> [       Adapter        ] -------- fn(entity) -------> [ Business Layer ]
                                      1. Deserialise into DTO
                                      2. Validate input
                                      3. Converts DTO to domain entity (iff validation succeeds)
                                      4. Invoke domain logic
```

**Conventions:**

  * Request DTO: `${prefix}RequestPayload`, e.g. `MetadataRequestPayload`
  * Response DTO: `${prefix}ResponsePayload`, e.g. `MetadataResponsePayload`.

**Example Strucure:**

```
sketch/
├── shared/
│   └── domain/
│       └── documents/
│           └── Document.scala                      # Pure domain model, no encoders
├── http/
│   └── DocumentRoutes.scala
│       └── object Model
│           ├── object json                         # JSON encoders/decoders
|           |   ├── MetadataRequestPayload
|           |   ├── MetadataResponsePayload
|           |   └── . . .
│           └── object xml                          # Optional formats

```

> **Tip:**<br>
> Use [chimney](https://github.com/scalalandio/chimney) to reduce boilerplate when converting between DTOs and entities.

**Contract Tests:**

Ensure **lossless conversions** and **no breaking changes** in API contracts by testing encoders and decoders. See [ContractContext](/shared-test-components/src/main/scala/org/fiume/sketch/shared/testkit/ContractContext.scala) for more details.


## 4. Error Handling

**Errors are first-class citzens**, not execptions.

### 4.1 Http Status Codes

**Relevant Success Codes:**

| Status Code     | Meaning                           |
|-----------------|-----------------------------------|
| `200`           | Success                           |
| `201`           | Resource created                  |
| `202`           | Requested accepted - processing asynchronously |
| `204`           | Success with no response payload  |

**Relevant Error Codes:**

| Status Code     | Meaning                                              |
|-----------------|------------------------------------------------------|
| `400`           | Structurally invalid request                         |
| `401`           | Authentication failure                               |
| `403`           | Unauthorised access                                  |
| `404`           | Resource not found                                   |
| `409`           | State-machine related error                          |
| `422`           | Semantically or structurally (json) invalid request  |
| `429`           | Too many requests (rate-limiting)                    |
| `500`           | Internal server error                                |
| `503`           | Service unavailable                                  |

### 4.2 Input validation

All inputs must be validated before invoking the domain logic.

API endpoints must return a payload that corresponds to a established contract when there is a semantic error.

The `details` field is optional and must be used when helping the caller to understand the exact cause of the error:
...... also phrase this with your words: "All validation errors must be aggregated and returned in the details object to reduce client-server roundtrips."

> **Important!** <br>
> The following contract is considered experimental and subject to change.
> Main source of uncertainty is the `code` field.

```
{
  "code": "1000",
  "message": "Invalid username or password",
  "details": {
    "username.too.short": "must be at least 8 characters long",
    "username.reserved.words": "must not contain reserved words"
  }
}
```
> Source: [error.info.with.details.json](/test-contracts/src/test/resources/error-info/error.info.with.details.json)

> For a full list of errors, see [Error Codes](ErrorCodes.md).

## 5. Security

**Middlewares:**

| Feature                              | Component              | Status               | Future Directions                   |
|--------------------------------------|------------------------|----------------------|-------------------------------------|
| Authentication                       | [AuthMiddleware](/auth/src/main/scala/org/fiume/sketch/auth/http/middlewares/Auth0Middlware.scala) | Implemented - see [doc](/auth/README.md) |
| CORS                                 | [CORS](https://http4s.org/v1/docs/cors.html)                                             | Partially implemented - missing proper configuration |
| CSRF                                 | [CSFR](https://http4s.org/v1/docs/csrf.html#)                                            | Not implemented     |
| Rate limiting                        | [MaxActiveRequests](https://http4s.org/v1/docs/server-middleware.html#maxactiverequests) | Not implemented     |
| Throttling                           | [Throttle](https://http4s.org/v1/docs/server-middleware.html#maxactiverequests)          | Not implemented     |
| Request size limits                  | [EntityLimiter](https://http4s.org/v1/docs/server-middleware.html#entitylimiter)         | Not implemented     |

> **Future Gateway Work:**<br>
> All of the above are candidates to be moved to a dedicated API Gateway.

> **Note on Authorisation (Access Control):**<br>
> Handled in the application layer, e.g. [UsersManager]().
.....rephrasing "Authorisation note" as: "Fine-grained authorisation is handled at the application layer (e.g., UsersManager). The HTTP layer only authenticates and routes."


## 6. Future Directions

 * Improved Client Experience: Client guidelines, pagination support
 * Observability: Request ID's, tracing, tracking, metrics

# HTTP Inbound Adapters - Design Guidelines

The HTTP layer provides public APIs that must securely map external requests into the domain in a client-agnostic way.

**Table of Contents**

1. [Goals](#1-goals)
2. [APIs](#2-apis)
   - 2.1 [REST APIs](#21-rest-apis)
   - 2.2 [Streaming APIs](#22-streaming-apis)
       - 2.2.1 [Binary Downloads](#221-binary-downloads)
       - 2.2.2 [Newline-delimited JSON (NDJSON)](#222-newline-delimited-json-ndjson)
   - 2.3 [Example](#23-example)
   - 2.4 [Client Libraries](#24-client-libraries)
   - 2.5 [Versioning](#25-versioning)
       - 2.5.1 [URL Versioning](#251-url-versioning)
       - 2.5.2 [Client Library Versioning](#252-client-library-versioning)
       - 2.5.3 [Cross-Channel Divergence](#253-cross-channel-divergence)
       - 2.5.4 [Semantic Drift](#254-semantic-drift)
3. [Shielding the Domain (Data Representation)](#3-shielding-the-domain-data-representation)
4. [Error Handling](#4-error-handling)
   - 4.1 [HTTP Status Code](#41-http-status-codes)
   - 4.2 [Input Validation](#42-input-validation)
5. [Security](#5-security)
6. [Future Directions](#6-future-directions)

## 1. Goals

* Expose functionalities to external services through HTTP API endpoints
* Support API evolution without breaking existing clients
* Isolate the domain layer from external representations and protocols
* Handle errors gracefully, maintaining service integrity and delivering clean and actionable feedback to clients
* Ensure that only authenticated and authorised requests reach the business layer
* Protect the system against abuse, such as excessive requests or malicious input


## 2. APIs

### 2.1 REST APIs

Functionalities can be exposed using **resource-oriented [REST](https://en.wikipedia.org/wiki/REST) endpoints**.

**Principles**:
  * **Stateless**: Requests include all the necessary information for processing
  * **Cache-friendly**: Response should be cacheable by servers or clients when appropriate.

**Conventions**:
  * Plural **nouns** to represent **resources** (e.g. `/documents`, `/users`)
  * HTTP **verbs** to represent **actions**
  * HTTP [**status codes**](https://en.wikipedia.org/wiki/List_of_HTTP_status_codes) to indicate **outcome** (see [Error Handling - HTTP Status Code](#41-http-status-codes)).

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
    - Do: `DELETE /users/${uuid}`
  * **Level 3 ([HATEOAS](https://en.wikipedia.org/wiki/HATEOAS))** is not required, unless justified by a strong business case.

### 2.2 Streaming APIs

Stream the data directly to client when **content is too large** to be buffered in memory or **naturally incremental**.

Streamable data include:
  * Large binary files (e.g. PDFs, images, videos)
  * Large list of resources
  * Real-time or incremental content.

#### 2.2.1 Binary Downloads

**Example - Streaming Binary Documents:**

A stream is processed chunk by chunk, avoiding loading the whole content into memory.

```scala
case GET -> Root / "documents" / DocumentIdVar(uuid) as user =>
  for res <- accessControl
      .canAccess(user.uuid, uuid)
      .commit()
      .ifM(
        ifTrue = Ok(store.documentStream(uuid).commitStream(), `Content-Disposition`("attachment", Map.empty)),
        ifFalse = Forbidden()
      )
  yield res

```

#### 2.2.2 Newline-delimited JSON (NDJSON)

**Example - Streaming Document Resource List:**

Consider using NDJSON for large lists of resources. It provides lower memory overhead in both clients and server, and clients can start processing data immediately.

```scala
case GET -> Root / "documents" as user =>
  val stream = accessControl
    .fetchAllAuthorisedEntityIds(user.uuid, "DocumentEntity")
    .through(store.fetchDocuments)
    .commitStream()
    .map(_.asResponsePayload.asJson)
    .map(Line(_))
    .intersperse(Linebreak)
  Ok(stream, Header.Raw(ci"Content-Type", "application/x-ndjson"))
```

> **See:** [NewlineDelimitedJson](/shared-components/src/main/scala/org/fiume/sketch/shared/common/http/json/NewlineDelimitedJson.scala).

### 2.3 Example

Documents Management APIs:

| Method   | API Endpoint                 | Description         |
| -------- | ---------------------------- | ------------------- |
| `POST`   | `/documents`                 | Upload a document   |
| `GET`    | `/documents/{uuid}`          | Download a document |
| `GET`    | `/documents/{uuid}/metadata` | Fetch metadata      |
| `DELETE` | `/documents/{uuid}`          | Delete a document   |

> **Note:** Metadata is a **sub-resource** because:
> * It is lightweight and frequently accessed independently of the full document
> * It may have different access control rules than the binary content.

> See [DocumentsRoutes](/service/src/main/scala/org/fiume/sketch/http/DocumentsRoutes.scala) and [DocumentsRoutesSpec](/service/src/test/scala/org/fiume/sketch/http/DocumentsRoutesSpec.scala).

### 2.4 Client Libraries

Provide a client library that:
  * **Abstracts** and **encapsulates** HTTP API calls
  * Works as a **compatibility boundary**, shielding clients from server changes behind stable functions.

**Example - [HttpAuthClient](/shared-auth/src/main/scala/org/fiume/sketch/shared/auth/http/HttpAuthClient.scala)**:


```scala
authClient = HttpAuthClient.make(config, client)

authClient.login(username, password)
```

**Benefits:**
  * **Developer Ergonomics**:
    - Strong typing for requests and responses
    - Autocompletion and IDE support
    - Rich and structured error handling
  * **Hassle-Free Integration** - Near plug-and-play usage with:
    - No HTTP quirks
    - No manual serialisation/deserialisation
    - No header or query parameters handling
  * **Operational Consistency**:
    - Built-in retry logic with exponential backoff
    - Standardised logging and telemetry
    - Uniform and consistent behaviour

### 2.5 Versioning

| Context                          | **URL Versioning** | **Client Library Versioning** |
| -------------------------------- | ------------------ | ----------------------------- |
| Internal clients                 | **✗**  Avoid       | **✓** Prefer                  |
| Public API, uncontrolled clients | **✓** Recommended  | **✓** Optional                |
| Strict regulatory requirements   | **✓** Recommended  | **✓** Optional                |


#### 2.5.1 URL Versioning

Example:

```
/v1/documents
/v2/documents
```

Trade-offs:
  * **Semantic Drift**: Same endpoint (`/documents`) may have different behaviour in v1 vs. v2, increasing cognitive load and risk of bugs - see the [Semantic Drift](#254-semantic-drift) section
  * **Clients Migration Complexity**: Old versions may linger for years, increasing long-term maintenance costs.

**Use it** only for:
  * **Public APIs** where clients upgrades are outside your control
  * **Strict regulatory requirements** where historical behaviour must be preserved.

#### 2.5.2 Client Library Versioning

Client libraries can be used as **versioning boundary** between clients and the server.

**Benefits**:
  * **Uniformity**: Maintain a single version of HTTP API endpoints
  * **Clean Compatibility Boundary**: Keeps versioning concerns within the library - breaking changes are handled within the client and coordinated releases
  * **Semantic Versioning**: Provides clear visibility into updates and their impact when combined with package managers.

**Cons**:
  * Requires **strong release discipline**, ensuring old releases are replaced quickly, avoiding complexities with multiple SDK versions used in production
  * Clients may still **hit the HTTP endpoint directly**
  * Polyglot environments require **one library per supported language**.

**Use it** whenever there is a degree of **control over client upgrades**.


#### 2.5.3 Cross-Channel Divergence

When the **same functionality is exposed through multiple channels** (REST APIs, Kafka topics, gRPC), ensuring **consistent behaviour across these data sources** is essential, regardless of the versioning approach.

Example Scenario:
  * Documents are exposed via both:
    - `/documents` HTTP endpoint
    - `documents-v1` Kafka topic
  * A new Kafka topic, `document-v2`, is introduced
  * Problem:
    - Which version does `/documents` represent now?
    - Applications expecting equivalent data from both channels may present unexpected or inconsistent behaviour when consuming data from different sources.

Recommendations:
  * Establish clear contracts between service and client teams
  * Service-side:
    - Evolution Policy: Set clear expectations on when and how different channels are updated
  * Client-side:
    - Single source of truth: Avoid consuming data from multiple channels, unless there is a clear and well-defined reason to do so
    - Reconciliation: When channels become divergent, use the client's inbound adapters to detect and resolve difference between data streams.

#### 2.5.4 Semantic Drift

Using a version bump (`v3` -> `v4`) for a significant semantic shift hides the magnitude of the change. As a result:
  * Clients may expect that only adjustments to a new schema are needed and underestimate the broader impact of upgrades
  * Downstream services may begin to behave unpredictably.

| Type of Change                | Example                                   | Recommendation      | Rationale            |
|-------------------------------|-------------------------------------------|---------------------|----------------------|
| Structural (schema evolution) | A field is added, removed or replaced     | Bump up the version (`v3` -> `v4`) | Mechanical, structural changes required |
| Semantic (meaning shift)      | * A business concept is changed or introduced<br>* Data sourced by a new pipeline | New name (`/documents` -> `/submission`) | New business logic required |

> **Rule of Thumb**:<br>
> Use a new name when changes potentially require new business logic on clients to adapt.


## 3. Shielding the Domain (Data Representation)

Use [DTO](https://martinfowler.com/eaaCatalog/dataTransferObject.html)s to represent **request and response payloads**,
decoupling the business layer from external clients concerns.

> **Rule of Thumb:**<br>
> Changes in API payloads **must not leak** into the domain model, and domain changes must not break API contracts.

```
[   Client   ] ------ (json) ------> [       Adapter        ] -------- fn(entity) -------> [ Business Layer ]
                                      1. Deserialise into DTO
                                      2. Validate input
                                      3. Converts DTO to domain entity (iff validation succeeds)
                                      4. Invoke domain logic
```

**Conventions:**

  * Request DTO: `${prefix}RequestPayload`, e.g. `MetadataRequestPayload`
  * Response DTO: `${prefix}ResponsePayload`, e.g. `MetadataResponsePayload`.

**Example Structure:**

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

**Errors are first-class citizens**, not exceptions.

### 4.1 HTTP Status Codes

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

When validation fails, endpoints must return a payload that corresponds to a established contract:

| Field     | Required | Status       | Purpose                                                          |
| --------- | -------- |--------------|----------------------------------------------------------------- |
| `code`    | Yes      | Experimental | Unique, machine-readable code                                    |
| `message` | Yes      | Stable       | Provide enough context to support callers to handle errors       |
| `details` | Optional | Stable       | Aggregates errors to prevent client-server roundtrips.           |

> See [Error Codes](ErrorCodes.md) for a complete list of errors.

**Example:**

```json
{
  "code": "1000",
  "message": "Invalid username or password",
  "details": {
    "username.too.short": "must be at least 8 characters long",
    "username.reserved.words": "must not contain reserved words"
  }
}
```

> **Error Message Schema**:<br>
> The schema is defined in [error.info.with.details.json](/test-contracts/src/test/resources/error-info/error.info.with.details.json), also used for **contract tests**. See [ErrorInfoSpec](/shared-components/src/test/scala/org/fiume/sketch/shared/common/troubleshooting/ErrorInfoSpec.scala).


## 5. Security

The HTTP layer acts as a security filter, rejecting invalid requests before they reach business logic and protecting system availability.

| Concern                              | Status               | Ownership       |
|--------------------------------------|----------------------|-------------------------------------|
| Authentication ([doc](/auth/README.md)) | Implemented          | **Current**: [HTTP Layer](/auth/src/main/scala/org/fiume/sketch/auth/http/middlewares/Auth0Middlware.scala) <br>**Future**: API Gateway |
| Authorisation ([doc](/shared-access-control//README.md)) | Implemented          | [Application Layer](/shared-access-control/src/main/scala/org/fiume/sketch/shared/authorisation/AccessControl.scala) |
| CORS                 .md                | Partially implemented (missing proper configuration) | [HTTP Layer](https://http4s.org/v1/docs/cors.html) |
| CSRF                                 | Not implemented      | [HTTP Layer](https://http4s.org/v1/docs/csrf.html#) |
| Rate limiting                        | Not implemented      | API Gateway |
| Throttling                           | Not implemented      | API Gateway |
| Request size limits                  | Not implemented      | [HTTP Layer](https://http4s.org/v1/docs/server-middleware.html#entitylimiter) |


## 6. Future Directions

 * Improved Client Experience: Client guidelines, pagination support
 * Observability: Request ID's, tracing, tracking, metrics
 * Move security features to a dedicated API Gateway.

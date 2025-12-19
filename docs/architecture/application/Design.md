# Application Layer - Design Guidelines

The application layer implements business use-cases, orchestrating domain components and uses external capabilities through [ports](/docs/architecture/domain/Design.md#54-ports--algebras) (algebras). It checks authorisation, coordinates multiple domain tasks into atomic operations, handles retries and supports application monitoring.


> This document is part of the project's [Architecture Guidelines](/README.md#62-architecture).

```
[ Inbound Adapters ] --> [ Application Layer (you're here) ] --> [ Domain Layer ] <-- [ Outbound Adapters ]
```


**Table of Contents**

1. [Goals](#1-goals)
2. [Principles](#2-principles)
3. [Keep the Application Layer Free From](#3-keep-the-application-layer-free-from)
4. [Cross-Cutting Concerns](#4-cross-cutting-concerns)
5. [Error Handling](#5-error-handling)
    - 5.1 [Business Errors](#51-business-errors)
    - 5.2 [Infrastructure Errors](#52-infrastructure-errors)
    - 5.3 [A Note About Raised Errors](#53-a-note-about-raised-errors)
6. [Example - Create User Account](#6-example---create-user-account)
7. [Pragmatic Compromises](#7-pragmatic-compromises)
    - 7.1 [Skip Application Layer If Use Case Is Trivial](#71-skip-application-layer-if-use-case-is-trivial)
    - 7.2 [Domain Objects Exposed to Adapters](#72-domain-objects-exposed-to-adapters)
    - 7.3 [Domain Errors Exposed to Adapters](#73-domain-errors-exposed-to-adapters)
    - 7.4 [Input Validation Delegated to Inbound-Adapters (via Domain Factories)](#74-input-validation-delegated-to-inbound-adapters-via-domain-factories)
8. [Future Directions](#8-future-directions)


## 1. Goals

* Implement **business workflows** by composing [domain primitives](/docs/architecture/domain/Design.md)
* Ensure **only authorised actors** can access data or trigger workflows
* Maintain **system integrity**, preserving invariants and preventing invalid state
* Provide **operational and business insights**.


## 2. Principles

* **Orchestrate, Don't Own Business Logic**: Compose business use-cases from domain models and rules
* **Enforce authorisation at the boundaries**: Check authorisation before initiating the workflow
* **Guarantee data consistency**: Define atomic or idempotent operations
* **Stay stateless and scalable**: Do not retain state in memory to enable horizontal scaling and fault tolerance
* **Avoid Side-Effects**: Push side-effects to the system edges, freeing the application layer from external details, increasing testability and maintainability.


## 3. Keep the Application Layer Free From

| Concern                          | Proper Layer                | Rationale   |
|----------------------------------|-----------------------------|-----------------------------------------|
| **✗** State                      | Infrastructure Layer        | Preserving in-memory state between process hurts scalability |
| **✗** Domain logic               | Domain Layer                | - Prevents duplication of core business modes and rules<br> - Centralised code favours composition, testability and correctness |
| **✗** Data Representation        | Inbound / Outbound Adapters | Keeps application focused on business workflows rather than data transmission machinery |
| **✗** Infrastructure concerns    | Outbound Adapters           | Keeps application components free from data transmission or persistency machinery, increasing testability and maintainability |


# 4. Cross-Cutting Concerns

| Concern                              | Rationale                 |
|--------------------------------------|---------------------------|
| **Access Control**                   | Prevents unauthorised requests from having access to business workflows |
| **Transaction Boundaries**           | Prevents data inconsistencies |
| **Logging / Metrics**                | Provides operational insights, facilitates troubleshooting, tracks business KPI's (Key Performance Indicators) |


## 5. Error Handling

| Responsibility                                                          | Layer                  |
|-------------------------------------------------------------------------|------------------------|
| Return **business error when invariants are not met**                   | Domain                 |
| Ensure a **transaction is rolled back**                                 | Application            |
| Route **undelivered messages to DLQ**                                   | Application            |
| **Convert any error** to a protocol-specific response                   | Inbound Adapters       |
| Raise **errors due to infrastructure issues** such as connectivity timeout | Outbound Adapters      |

This project distinguishes **recoverable business errors** from **unrecoverable infrastructure errors**.

### 5.1 Business errors

Business errors represent situations where the workflow can respond gracefully. They are explicitly modelled with `Either`.

Example:

```scala
F[Either[AccessDenied.type | ActivateAccountError, Account]]
```
This computation can:
 * **Succeed:** returning a valid `Account`
 * **Fail:** returning `AccessDenied` or a domain error like `ActivateAccountError`.

> **Note:** See [[Types -> Compile Time Invariants](/docs/best-practices/Applied-Theory.md#1-types---compile-time-invariants)] for an overview on Union types (`|`).

### 5.2 Infrastructure Errors

Infrastructure errors (e.g. network timeouts) are considered unrecoverable and raised with `IO.raiseError` or similar. They are not explicit in the function signature.

This approach keeps business APIs focused and free from low-level infrastructure concerns.

Example:

```scala
  for
    userId <- store.createAccount(creds) // can raise an infrastructure error
    _ <- accessControl.grantAccess(userId, userId, ContextualRole.Owner) // can raise another
  yield userId
```
When there is an error:
  * The current workflow is aborted, and any in-progress transaction is rolled back
  * The application layer decides how to handle the error from a business perspective, for example by:
    - Routing undelivered messages to a DLQ
    - Logging and tracking errors
    - Triggering alerts
  * Adapters translate the error in a format expected by clients.

### 5.3 A Note About Raised Errors

Raising errors **doesn't mean exceptions are thrown immediately**. Instead, `raiseError` returns **a description of a failed computation**, and the exception is only materialised at runtime.

This preserves [referential transparency](https://en.wikipedia.org/wiki/Referential_transparency) while allowing a mental model similar to throwing runtime exceptions that most developers are familiar with.


## 6. Example - Create User Account

Let's use account management as the canonical example for the application layer:

**API Definition:**

The algebra (interface) defines the contract:

```scala
trait UsersManager[F[_]]:
  def createAccount(username: Username, password: PlainPassword, globalRole: Option[GlobalRole] = none): F[UserId]

  . . . // other operations
```
> **See:** [UsersManager.scala](/auth/src/main/scala/org/fiume/sketch/auth/accounts/UsersManager.scala)

**Implementation:**

The concrete implementation composes the business workflow:

```scala
object UsersManager:
  def make[F[_]: Sync, Txn[_]: Monad](. . . /*dependencies*/): UsersManager[F] =
    . . .
    new UsersManager[F]:
      override def createAccount(username: Username, password: PlainPassword, globalRole: Option[GlobalRole]): F[UserId] =
        // Pre-transaction: Pure domain logic (hashing)
        val credentials = for
          salt <- Salt.generate()
          hashedPassword <- HashedPassword.hashPassword(password, salt)
        yield UserCredentials(username, hashedPassword, salt)

        val setUpAccount = for
          creds <- store.lift { credentials } // Lift pure computation into a transactional context
          userId <- store.createAccount(creds) // Port: Persistence
          _ <- accessControl.grantAccess(userId, userId, ContextualRole.Owner) // Port: Authorisation
          _ <- globalRole.fold(ifEmpty = ().pure[Txn])(accessControl.grantGlobalAccess(userId, _)) // Port: Conditional Authorisation
        yield userId

        setUpAccount.commit() // Perform atomic transaction

      . . . // remaining` operations
```
> **See:** [UsersManager.scala](/auth/src/main/scala/org/fiume/sketch/auth/accounts/UsersManager.scala)

* **Domain Logic**: Domain primitives are used to hash a password
* **Ports Usage**: `store` and `accessControl` are outbound ports (algebras)
* **Atomic Operation**: The entire `setUpAccount` block represents one transaction.
* **Error Handling**: Any failure triggers a rollback of all operations.

> **Transaction Boundaries:** See [TransactionManager](/shared-components/src/main/scala/org/fiume/sketch/shared/common/app/TransactionManager.scala) documentation to dive deeper on defining transactional boundaries, and how that is done without having low-level infrastructure details leaking into the application layer.


## 7. Pragmatic Compromises

While Clear Architecture textbooks recommend to completely shield the domain layer from inbound adapters, this adds complexity by introducing more indirection.

The aim is to reduce the amount of code, improve clarity and speed up new feature releases.

The sub-sections below describe places where we can skip textbook advice without sacrificing code quality and maintainability, with hints helping us to understand when following standard practices "by the book" may be a good idea.

### 7.1 Skip Application Layer If Use Case Is Trivial

For **simple use cases** (e.g. CRUD), **merging the adapter and the application layer** into a single component is an option. In these cases, the adapter handles cross-cutting concerns (e.g. authorisation, transaction boundaries, logging) and invokes domain API's directly.

| Context                                                  | Merge layers?          | Note                                                    |
|----------------------------------------------------------|------------------------|---------------------------------------------------------|
| Simple CRUD-like use cases                               | **✓**                  | Simple logic makes it easy to test and safe to merge    |
| Moderate orchestration logic complexity                  | **✓** (be cautious)    | Extract logic to an application layer if it grows in complexity and becomes difficult to test |
| Moderate cross-cutting logic complexity<br> (e.g. authorisation, transaction boundaries, auditing, monitoring) | **✓** (be cautious) | Similarly, consider extracting when cross-cutting logic becomes more difficult to maintain |
| Moderate orchestration & cross-cutting concerns combined | **✗** | At this stage, there will be hardly a reason to skip a dedicated application component |
| Multiple adapters depending on the same workflow         | **✗** | The application layer avoids duplicate code and prevents inconsistent implementations |

Example of a suitable case for merging adapter and application layer concerns:

```scala
  private val authedRoutes: AuthedRoutes[User, F] =
    AuthedRoutes.of {
      . . .

      case DELETE -> Root / "documents" / DocumentIdVar(uuid) as user =>
        // The following block normally belongs to the application layer
        for
          document <- accessControl
            .attempt(user.uuid, uuid) { _ => // Check permission
              accessControl.ensureRevoked_(user.uuid, uuid) { store.delete(_).as(uuid) } // Revoke access and delete document
            }
            .commit() // Execute transaction if there are no errors
          // Back to expected adapter responsibilities: converting result to a protocol-specific response
          res <- document match
            case Right(_) => NoContent()
            case Left(_) => Forbidden()
        yield res
  }

```
> **See:** [DocumentsRoutes.scala](/service/src/main/scala/org/fiume/sketch/http/DocumentsRoutes.scala)

### 7.2 Domain Objects Exposed To Adapters

The project's application layer exposes domain objects directly to inbound-adapters.

The **key boundary** remains **between domain and external systems**: entities must be converted to DTOs within adapters, shielding the domain from the outside world.

```scala
  private val authedRoutes: AuthedRoutes[User, F] =
    AuthedRoutes.of {
      . . .

      case GET -> Root / "documents" / DocumentIdVar(uuid) / "metadata" as user =>
        for
          document <- accessControl.attempt(user.uuid, uuid) { store.fetchDocument }.commit()
          res <- document match
            case Right(document) =>
              document
                .map(_.asResponsePayload) // This is where a `Document` aggregate is converted into a DTO
                .fold(ifEmpty = NotFound())(Ok(_))
            case Left(_)         => Forbidden()
        yield res
  }

```
> **See:** [DocumentsRoutes.scala](/service/src/main/scala/org/fiume/sketch/http/DocumentsRoutes.scala)

Do not expose domain objects to inbound adapters when:

  * Changing domain models cascades to various components, potentially in different project repos, especially if done frequently.

> **Important!**<br>
> High-discipline must be in place to ensure adapters **convert domain objects into DTOs**, **preventing them from leaking** to the external world.

See [Shielding The Domain](/docs/architecture/inbound-adapters/http/Design.md#3-shielding-the-domain) for more details.

### 7.3 Domain Errors Exposed to Adapters

In a Clean Architecture, the application layer converts domain errors into application-level errors.

Currently in this project, there is no mapping between domain and application errors, which propagates them upward. It is the inbound adapters responsibility to convert them to protocol-specific response.

For example, in the code below, `AccountAlreadyPendingDeletion` and `SoftDeleteAccountError.AccountNotFound` are both domain-level error types translated into an HTTP response with the proper status code and `ErrorInfo` payload.

```scala
  private val authedRoutes: AuthedRoutes[User, F] =
    AuthedRoutes.of {
      . . .

      case DELETE -> Root / "users" / UserIdVar(uuid) as authed =>
        usersManager
          .attemptToMarkAccountForDeletion(authed.uuid, uuid)
          .flatMap {
            case Right(job)                          => Ok(job.asResponsePayload)
            case Left(error: SoftDeleteAccountError) =>
              error match
                // Domain errors handled directly in the adapter
                case AccountAlreadyPendingDeletion          => Conflict(error.toErrorInfo)
                case SoftDeleteAccountError.AccountNotFound => NotFound(error.toErrorInfo)
            case Left(error: AccessDenied.type) => Forbidden(error.toErrorInfo)
          }
    }
```
> **See:** [UsersRoutes.scala](/auth/src/main/scala/org/fiume/sketch/auth/http/UsersRoutes.scala)

You may want to map domain to application errors if:
  - Domain errors are not stable, with changes propagating to multiple adapters
  - Their level of granularity is high

> **Important!**<br>
> Strict discipline is required to **prevent leaking domain errors to outer systems**. Make sure they are always mapped to a suitable representation to provide clients with actionable feedback, preserving Clean Architecture boundaries.

### 7.4 Input Validation Delegated to Inbound-Adapters (via Domain Factories)

The project's application layer expects valid inputs.

Besides checking that there are no structural errors, adapters are responsible for ensuring invariant are preserved by invoking [factory functions](/docs/architecture/domain/Design.md#4-domain-errors-as-first-class-citizens), and return a list of actionable errors otherwise.

```scala
  // Represents the JSON request payload
  case class LoginRequestPayload(username: String, password: String)

  extension (payload: LoginRequestPayload)
    def validated[F[_]: Async](): F[(Username, PlainPassword)] =
      (
        Username.validated(payload.username).leftMap(_.asDetails), // Domain factory
        PlainPassword.validated(payload.password).leftMap(_.asDetails) // Another domain factory
      ).parMapN((_, _)) // Errors are accumulated instead of failing fast
        .fold(
          errorDetails => Login.Error.makeSemanticInputError(errorDetails).raiseError, // Errors are converted to suitable representation
          _.pure[F] // Or else a tuple with `Username` and `PlainPassword` is returned (both domain value objects)
        )
```
> **See:** [Login.scala](/shared-auth/src/main/scala/org/fiume/sketch/shared/auth/http/model/Login.scala)

> **Important!**<br>
> Extra care needs to be taken to ensure **no business validation is implemented in adapters**.


## 8. Future Directions

* Improved Monitoring:
  - Currently, the application logs the bare minimum information to pinpoint potential problems
  - Tracking and tracing are missing entirely. The plan is to define tracking ports/algebras, and leave their implementation to when the app infrastructure is ready for deployment in a production environment.

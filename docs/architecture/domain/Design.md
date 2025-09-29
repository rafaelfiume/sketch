# Domain Layer - Design Guidelines

The domain layer represents the core business of an application.

It is technology-agnostic, independent from frameworks, and the communication with the external world must be made through ports (interfaces).


**Table of Contents**

1. [Goals](#1-goals)
2. [Principles](#2-principles)
3. [Keep Domain Clear From](#3-keep-domain-layer-free-from)
4. [Domain Errors as First-Class Citizens](#4-domain-errors-as-first-class-citizens)
5. [Key Components](#5-key-components)
    - 5.1 [Entities](#51-entities)
    - 5.2 [Value Objects](#52-value-objects)
    - 5.3 [Aggregates](#53-aggregates)
    - 5.4 [Ports / Algebras](#54-ports--algebras)
    - 5.5 [Domain & Integration Events](#55-domain--integration-events)
6. [Further Reading](#6-further-reading)


## 1. Goals

Define **business models and rules** that **capture business need** and **deliver value**.


## 2. Principles

* **Layer Isolation**:
    - No dependencies on the other layers
    - External capabilities are defined through ports.

* **No Side-Effects**:
    - Functions are pure, fully unit-testable
    - Shields the domain from external concerns, such as integration with external clients and infrastructure.

* **Clear and Enforced Invariants**:
    - Invalid states are unrepresentable
    - Validation rules encoded directly in [types](/docs/best-practices/Applied-Theory.md#1-types---compile-time-invariants) and [constructors](/shared-auth/src/main/scala/org/fiume/sketch/shared/auth/Passwords.scala#L50).


## 3. Keep Domain Layer Free From

| Concerns                              | Belongs To                                            | Rationale   |
|---------------------------------------|-------------------------------------------------------|-------------|
| **✗** Serialisation / Deserialisation | Inbound / Outbound Adapters                           | Data formats for communication (e.g. JSON, Protobuf) are infrastructure concerns handled by adapters |
| **✗** Side-effects                    | - Inbound / Outbound Adapters<br> - Application Layer | Keep the domain pure and deterministic |
| **✗** Use-cases / Orchestration       | Application Layer                                     | The domain contain  models and rules (primitives). The application layer composes them into higher-level workflows |
| **✗** Handling events                 | - Inbound / Outbound Adapters<br> - Application Layer | - Adapters: Encode/decode events<br> - Application: Define what happens when an event is received or published |
| **✗** Cross-cutting concerns          | Application Layer                                     | Concerns like access-control, logging, tracking, monitoring are supporting capabilities, not business rules |


## 4. Domain Errors as First-Class Citizens

| Practice                                   | Rationale               | Examples                 |
|--------------------------------------------|-------------------------|--------------------------|
| **✗** **Never throw exceptions**           | - Domain errors are expected, not exceptions<br><br> - Use `InvariantError` to represent domain-level errors | `PlainPassword.WeakPasswordError` |
| **✓** **Factory Functions**       | Controlled instantiation ensuring invariants are preserved:<br><br> - Return explicit errors when construction fails | `def validated(value: String): EitherNec[WeakPasswordError, PlainPassword]` |
| **✓** **Collect errors**, don't fail fast  | Factory functions should provide a comprehensive list of invariant errors with actionable feedback  | `EitherNec[WeakPasswordError, PlainPassword]`<br><br> |
| **✓** Define **unsafe factory functions** for trusted contexts (e.g. tests) | Never bypass validation in production | `def makeUnsafeFromString(value: String): PlainPassword` |

> **Code Reference**: [Passwords.scala](/shared-auth/src/main/scala/org/fiume/sketch/shared/auth/Passwords.scala)


## 5. Key Components

| Model                 | Description        | Use When | Avoid When | Examples    |
|-----------------------|--------------------|----------|----------------|-------------|
| **Entity**            | Defined by a **persistent identity** rather than attributes | **✓** Identity is essential to distinguish between instances | **✗** Equality can be fully determined by values<br><br> **✗** Ephemeral data| [User](/shared-auth/src/main/scala/org/fiume/sketch/shared/auth/User.scala), [Document](/shared-domain/src/main/scala/org/fiume/sketch/shared/domain/documents/Document.scala) |
| **Value Object**      | Immutable value with **equality purely based on its attributes** | **✓** Describing concepts such as money, measurement, passwords | **✗** Concept requires identity or lifecycle management | [PlainPassword](/shared-auth/src/main/scala/org/fiume/sketch/shared/auth/Passwords.scala) |
| **Aggregate**         | Related objects representing a **single consistency boundary** | **✓** Invariants must be enforced across multiple entities and value objects | **✗** A single entity or value object suffices to represent the concept | [DocumentWithId](/shared-domain/src/main/scala/org/fiume/sketch/shared/domain/documents/Document.scala#L24) |
| **Domain Service**    | **Business logic that doesn't belong to** a single entity, value object or aggregate| **✓** Behaviour spams multiple objects or is algorithmic | **✗** Logic fits cleanly within an object | [Authenticator](/auth/src/main/scala/org/fiume/sketch/auth/Authenticator.scala) |
| **Port / Algebra**    | - **Interface defining external capabilities** (storage, external APIs, messaging)<br><br> - Implementation lives in the Infrastructure layer | **✓** Domain needs to depend on behaviour, not on the underlying technology | **✗** Operation is computational with no external side-effects | [UsersStore](/shared-auth/src/main/scala/org/fiume/sketch/shared/auth/algebras/UsersStore.scala) |
| **Domain Event**      | Immutable object representing a **fact within a single bounded context** | **✓** Enabling asynchronous workflows | **✗** Synchronous method calls suffices | [AccountDeletionEvent](/shared-auth/src/main/scala/org/fiume/sketch/shared/auth/accounts/AccountDeletionEvent.scala) - Produced and consumed by the Identity module signalling that an account must be hard-deleted |
| **Integration Event** | Immutable object representing a **fact relevant to other bounded contexts or services** | **✓** Notifying external modules or services of state changes asynchronously | **✗** No other bounded contexts need to be informed of the change<br><br> **✗** Synchronous call suffices | [AccountDeletedNotification](/shared-auth/src/main/scala/org/fiume/sketch/shared/auth/accounts/AccountDeletedNotification.scala) - Notifies other modules to remove user related data |


### 5.1 Entities

Entities represent domain concepts with persistent identity.

IDs are modelled with [EntityId](/shared-components/src/main/scala/org/fiume/sketch/shared/common/EntityId.scala) in the codebase, such as `UserId` and `DocumentId`. `EntityId` types are entities themselves, since they uniquely identify domain objects.

Every aggregate is an entity, but **not every entity is an aggregate**.

| Property                                            | Entity          | Aggregate  |
|-----------------------------------------------------|-----------------|------------|
| Persistent ID                                       | **✓**           | **✓**      |
| Own other entities or value objects                 | Optional        | **✓**      |
| Root as the only entry point to change state        | **✗**           | **✓**      |
| Atomic changes to ensure all invariants hold        | **✗**           | **✓**      |


### 5.2 Value Objects

Value objects are first-class citizens. The table below explain their benefits:

| Property                                   | Rationale               | Example                  |
|--------------------------------------------|-------------------------|--------------------------|
| **Specific Types**          | - Communicates intention clearly<br><br> - Avoid type confusion at compile time<br> | `sealed abstract case class PlainPassword(value: String)` |
| **Immutable**               | Combined with factory functions, immutability guarantees that an instance cannot drift into an invalid state | Simplicity: either get a valid `PlainPassword`, or an error with actionable feedback |
| **Identity based on attributes** | Certain concepts are defined by their attributes | `PlainPassword` instances are equal _iff_ they hold the same string |

> **Code Reference**: [Passwords.scala](/shared-auth/src/main/scala/org/fiume/sketch/shared/auth/Passwords.scala)


### 5.3 Aggregates

Let's use `DocumentWithId` as the canonical example of an aggregate:

| Property                                   | Description             |
|--------------------------------------------|-------------------------|
| **Combined Domain Elements** | `DocumentWithId` is the aggregate root.<br><br> It is composed of:<br> - `DocumentId`<br> - `Metadata` = `Name` * `Description` * `UserId` (product type: all three must be present)<br><br>Entities: `DocumentId` and `UserId`<br>Value Objects:`Name` and `Description` |
| **Identity By ID**           | `DocumentId` uniquely identifies the aggregate |
| **Unrepresentable Invalid State** | - `DocumentWithId` can only be built passing valid `DocumentId` and `Document`<br> - `Document` can only be build with a valid `Metadata`<br> - `Metadata` can only be built with valid `Name`, `Description`, and `UserId` |
| **Atomic Operations** | The aggregate root and its components are persisted or deleted atomically through the `DocumentStore` |
| **Lifecycle** | Currently, the aggregate is created and deleted |

> **Note:** `DocumentWithId` is mostly a validated data holder for now.

> **Code Reference**:
> * [Document.scala](/shared-domain/src/main/scala/org/fiume/sketch/shared/domain/documents/Document.scala)
> * [DocumentsStore.scala](/shared-domain/src/main/scala/org/fiume/sketch/shared/domain/documents/algebras/DocumentsStore.scala)


### 5.4 Ports / Algebras

The following properties help designing predictable, composable interfaces for external capabilities:

| Property                                   | Rationale               | Example                  |
|--------------------------------------------|-------------------------|--------------------------|
| **✗** **No Invariants Enforcement**        | Domain ensures only valid objects are persisted | `def store(document: DocumentWithStream[F]): Txn[DocumentId]` |
| **✓** **`Option` for Missing Objects**     | - Use `Option` to represent the possibility of missing entities<br><br> - Keep the domain pure: never throw exceptions | `def fetchDocument(uuid: DocumentId): Txn[Option[DocumentWithId]]` |
| **✓** **Return Valid Aggregate**           | Helps to ensure invariants are always preserved | `def fetchDocument(uuid: DocumentId): Txn[Option[DocumentWithId]]` |
| **✓** **Streaming for Large Datasets**     | - Avoids memory issues<br><br> - Supports backpressure | `def fetchDocuments(uuids: fs2.Stream[Txn, DocumentId]): fs2.Stream[Txn, DocumentWithId]` |

> **Code Reference**: [DocumentsStore.scala](/shared-domain/src/main/scala/org/fiume/sketch/shared/domain/documents/algebras/DocumentsStore.scala)


### 5.5 Domain & Integration Events

Domain & Integration Events best practices:

| Property                                   | Rationale               | Benefits                 |
|--------------------------------------------|-------------------------|--------------------------|
| **✓** **Immutable**                        | Events must not change when published, ensuring their meaning remains consistent during the entire asynchronous processing | - Prevents inconsistent state<br><br> - Safe debugging and replay |
| **✓** **Flat Payload**                     | Design minimalistic, non-nested structured, with the minimum data necessary to describe facts | - Easier serialisation and schema evolution<br><br> - Decreases chances of coupling between modules or services |
| **✓** **Explicit Schema Evolution Policy** | Published events are contracts with consumers:<br> - Default to backward compatibility, allowing only additive changes<br> - Define new event types when semantic changes significantly | - Smoother client upgrades<br><br> - Reduced risks of breaking downstream systems |
| **✓** **Clear Naming**                     | - Past tense for historical facts, e.g. `AccountDeletedNotification`<br><br> - Present or neutral tense when signalling scheduled or pending actions, e.g. `AccountDeletionEvent` | Well-defined semantics |
| **✓** **Domain Defines, Application Handles** | - Application services are responsible for producing events via ports<br><br> - E.g. [UsersManagers](/auth/src/main/scala/org/fiume/sketch/auth/accounts/UsersManager.scala) produces `AccountDeletionEvent` via [EventProducer](/shared-components/src/main/scala/org/fiume/sketch/shared/common/events/EventProducer.scala) port | Simplifies testing and domain evolution |
| **✗** **No Business Logic**                | Events describe facts, not behaviour | - Keeps events reusable<br><br> - Reduces coupling by allowing consumers to decide behaviour |

The following practices take **event-driven systems to the next level**:

| Property                                   | Rationale               | Benefits                 |
|--------------------------------------------|-------------------------|--------------------------|
| **✓** **Versioned Integration Event**      | Define a versioning strategy for public events | Facilitates rollout of changes |
| **✓** **Idempotency**                      | Include an idempotency key, enabling consumer to identify duplicate events | Avoid harmful multiple side-effect executions in at-least-once delivery systems |
| **✓** **Traceability**                     | Include timestamps or trace IDs | - Increases observability facilitating troubleshooting<br><br> - Enables auditing  |

**Domain Event Flow - Hard Deletion:**

```
                   [ AccountDeletionEvent ] (Published by Identity Module)
                               |
                               ▼
                       [ Application ]      (Same bounded context )
                               |
                               ▼
                           [ Ports ]        (Delete User Account API )
                               |
                               ▼
                          [ Adapter ]       (Infrastructure implementation )
```
> **Code Reference**: [AccountDeletionEvent.scala](/shared-auth/src/main/scala/org/fiume/sketch/shared/auth/accounts/AccountDeletionEvent.scala)

**Cross-Module Communication with Integration Events - User Data Deletion:**

```
              [  AccountDeletedNotification  ] (Published by Identity Module)
                              │
            ┌-----------------┴----------------┐
            ▼                                  ▼
 [  Access-Control Module  ]           [  Project Module  ]
            |                                  |
            ▼                                  ▼
   [  Application  ]                   [  Application  ]
            |                                  |
            ▼                                  ▼
       [  Ports  ]                        [  Ports  ]
            |                                  |
            ▼                                  ▼
   [  Infrastructure  ]                [  Infrastructure  ]

```
> **Code Reference**: [AccountDeletedNotification.scala](/shared-auth/src/main/scala/org/fiume/sketch/shared/auth/accounts/AccountDeletedNotification.scala)


## 6. Further Reading

* [Hexagonal Architecture](https://en.wikipedia.org/wiki/Hexagonal_architecture_(software))
* [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
* [HTTP Inbound Adapters - Design Guidelines](/docs/architecture/inbound-adapters/http/Design.md) - Design effective and maintainable HTTP APIs.
* [Application Layer - Design Guidelines](/docs/architecture/application/Design.md) - Orchestrate stateless business workflows.

# Sketch

[![CircleCI](https://dl.circleci.com/status-badge/img/gh/rafaelfiume/sketch/tree/main.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/rafaelfiume/sketch/tree/main) [<img src="https://img.shields.io/badge/dockerhub-images-blue.svg?logo=LOGO">](<https://hub.docker.com/repository/docker/rafaelfiume/sketch/general>)


**Table of Contents**

1. [Project Philosophy](#1-project-philosophy)
2. [Start Here](#2-start-here)
3. [Architecture](#3-architecture)
    - 3.1 [Domain Modules](#31-domain-modules)
      - [Identity](#identity)
      - [Access Control](#access-control)
      - [Projects](#projects)
    - 3.2 [Application Layers](#32-application-layers)
4. [DevOps Practices](#4-devops-practices)
    - 4.1 [Continuous Integration](#41-continuous-integration)
    - 4.2 [12-Factor Principles](#42-the-12-factor-principles)
    - 4.3 [Scripting Guidelines](#43-scripting-guidelines)
    - 4.4 [Future Directions](#44-future-directions)
5. [From Ivory Tower to Production Code](#5-from-ivory-tower-to-production-code)
    - 5.1 [Postgres as a Lightweight Event Bus](#51-postgres-as-a-lightweight-event-bus)
6. [Further Reading](#6-further-reading)

## 1. Project Philosophy

Sketch is built on the principle of conceptual alchemy: **transforming abstract theory into practical engineering solutions**. 

This backend template uses non-obvious ideas, like using Information Theory to [write clear docs](docs/best-practices/Documentation.md) and [Category Theory](docs/best-practices/Applied-Theory.md#4-mathematical-foundations-category-theory---safe--composable-components) for building predictable and composable software components.

These diverse theories are applied not for their own sake, but to craft solutions to real-world problems. The result is a product that is secure, maintainable and scalable.

> See the section [From Ivory Tower to Production Code](#5-from-ivory-tower-to-production-code) for examples of this philosophy in action.


## 2. Start Here

To quickly run Sketch and its dependencies on your local machine, follow the [Onboarding](docs/start-here/Onboarding.md) guide.


## 3. Architecture

Sketch is a relatively small **modular monolith**. It's a single deployable unit, organised into modules, each composed by layers, for [low coupling](https://en.wikipedia.org/wiki/Coupling_(computer_programming)) and [high cohesion](https://en.wikipedia.org/wiki/Cohesion_(computer_science)).

This design provides two key benefits:
  1. **Fast iteration** during early and mid project stages
  2. A clear **migration path to a microservice architecture** as scaling challenges emerge.

### 3.1 Domain Modules

A domain module represents a distinct and cohesive set of business responsibilities.
Each can be evolved or extracted to a microservice independently later. They:
  * Encapsulate their data and internal implementation
  * Expose functionality through APIs (algebras) with well-defined behaviour.

The following modules compose the current system:

| **Module**            | Purpose                   | Scope              | Future Directions      |
------------------------|---------------------------|--------------------|------------------------|
| **[Identity](auth/README.md)** | Identifies users and manages their account lifecycle | Handles user registration and authentication | Profile management |
| **[Access Control](shared-access-control/README.md)** | Defines and enforces access-control policies and rules | Provides role- and owner-based authorisation mechanisms across modules | Fine-grained permissions to move beyond all-or-nothing access |
| **Projects** | Project lifecycle management | Securely handles user documents | - Expand to full lifecycle management, from early business opportunities to live operations<br><br> - Provide actionable business insights, such development opportunities and ROI |

### 3.2 Application Layers

Layers provide a clear separation of concerns, ensuring the system remains adaptable in a fast-evolving environment.


| Layer                       | Responsibility             | Components             | Dependencies             |  Do Not Allow |
|-----------------------------|----------------------------|------------------------|--------------------------|---------------|
| **Inbound Adapters**        | Entry points into the application.<br><br>Convert external inputs into a format the application layer understands, thus isolating the core domain | - [**Http APIs**](/docs/architecture/inbound-adapters/http/Design.md). E.g. [UserRoutes](/auth/src/main/scala/org/fiume/sketch/auth/http/UsersRoutes.scala)<br>- **Event Consumers**<br>- **CLI commands**<br>- **gRPC** | Application / Domain (entities) | **✗** Exposed domain entities to external world (use DTOs)<br><br> **✗**  Business-logic<br><br> **✗**  DAOs or external APIs used directly |
| **Application Layer**       | Implements use-cases by orchestrating domain components and invoking external modules through ports.<br><br>Defines transaction boundaries | E.g. [UsersManager](/auth/src/main/scala/org/fiume/sketch/auth/accounts/UsersManager.scala) | Domain (entities, ports) | **✗**  Bypass business rules<br><br> **✗**  Direct infrastructure access (e.g. low-level transaction code) |
| **Domain Layer**            | The core value of the system.<br><br> Expresses the business model, rules, and ports (contracts) for required external capabilities | - **Entities**. E.g. [User](/shared-auth/src/main/scala/org/fiume/sketch/shared/auth/User.scala)<br>- **Ports**. E.g. [UsersStore](/shared-auth/src/main/scala/org/fiume/sketch/shared/auth/algebras/UsersStore.scala) | None | **✗**  Dependencies on any other layer |
| **Outbound Adapters** (Infrastructure) | Implements ports, persisting the domain state, or calling external APIs | - **DAO**. E.g. [PostgresUsersStore](/storage/src/main/scala/org/fiume/sketch/storage/auth/postgres/PostgresUsersStore.scala)<br> - **Event Producers** | Domain (ports; entities as inputs/outputs) | **✗** Business rules<br><br> **✗**  Leaking infrastructure details |

> **Note**: We favour pragmatism over purism. The **Application Layer exposes Domain Entities as input and output to Inbound Adapters** to avoid unnecessary DTOs, reducing indirection without sacrificing maintainability. 

> This simplifies the code and preserves strong encapsulation. The **key boundary** remains **between domain and external systems**: entities must be converted to DTOs within Adapters, shielding the domain from the outside world.

**Compile-time dependencies:**

```
[ Inbound Adapters ] -> [ Application ] -> [ Domain ] <- [ Infrastructure ]

```


## 4. DevOps Practices

This project uses DevOps practices to automate releases and operational tasks, improve collaboration and build a cloud-ready, scalable application.

The goal is to **increase release cadence with confidence** through [shift-left testing](https://en.wikipedia.org/wiki/Shift-left_testing) and reliable operations.

### 4.1 Continuous Integration

The [CI pipeline](https://app.circleci.com/pipelines/github/rafaelfiume/sketch) automatically builds, tests, versions and [publishes](https://hub.docker.com/repository/docker/rafaelfiume/sketch) an image of the newest application version whenever changes are committed.
This ensures repeatable and frequent releases, preventing the pain of massive and infrequent release cycles. 

> **Note:** Continuous Delivery (CD) will expand this process to automatically deploy new versions to production.

See [Releases documentation](docs/devops/Releases.md).

### 4.2 The 12-Factor Principles

Applying the 12-Factors helps ensure the application is **portable**, **cloud-native** and **scalable**. 

Current focus includes [Admin Processes](/docs/devops/Admin.md),
with future guides planned for other principles, such as processes and logs.

### 4.3 Scripting Guidelines

Scripts **automate repetitive tasks** and work as **executable documentation**, with precise instructions to perform tasks, from starting the application stack to automating the release pipeline.

See the [Scripting Guidelines](docs/devops/Scripting.md) for tips on writing effective and maintainable scripts.

### 4.4 Future Directions

The plan is to expand DevOps practices in these areas:
  * **Infrastructure as Code (IaC)**: Declarative infrastructure provisioning with Terraform
  * **Continuous Delivery (CD)**: Automatic release to production after successful CI build
  * **Monitoring & Observability**: Metrics, logs and tracing with Prometheus, Grafana and Jaeger
  * **Security Practices**: Integrated security scanning and solid secrets management.

```
[ CI ] -> [IaC] -> [ CD ] -> [ Observability ] -> [ Security Practices ]
```


## 5. From Ivory Tower to Production Code

| Concept                         | Benefits             | Prevents         | Example      |
|---------------------------------|----------------------|------------------|--------------|
| **Natural Transformation** (`F ~> G`) | **Separates core business logic** (e.g. rules for setting up a user account) **from low-level infrastructure details** (e.g.transactions commit/rollback) | Mixing concerns, causing readability and maintenance nightmare, and making regression tests near impossible | **Define a clear transaction boundary.** <br><br>`val setupAccount = ... // create account, grant access to owner`<br>`setupAccount.commit()` <br><br>See: [UsersManager](auth/src/main/scala/org/fiume/sketch/auth/accounts/UsersManager.scala) (core domain) depends on [Store](shared-components/src/main/scala/org/fiume/sketch/shared/common/app/Store.scala) (infrastructure abstraction) |
| **Isomorphism**                 | **Lossless conversions** between data representations. | Corrupted keys leading to severe authentication failures | Ensuring cryptographic keys can be serialised and deserialised without corruption. <br><br>See: [KeyStringifierSpec](auth/src/test/scala/org/fiume/sketch/auth/KeyStringifierSpec.scala) |
| **Producer-Consumer Streams**   | Build **reliable event-driven systems with clear delivery semantics** | - Lost or duplicated events <br>- The low-level complexity generaPlly associated with this type of programming | Exactly-once semantics backed by [Postgres as a Lightweight Event Bus](#51-postgres-as-a-lightweight-event-bus). <br><br>`producer.produceEvent(. . .).ccommit`<br>`. . .`<br>`consumer.consumeEvent().flatMap { notification => . . . business logic }`<br><br>See: [PostgresAccountDeletedNotificationsStoreSpec](storage/src/it/scala/org/fiume/sketch/storage/auth/postgres/PostgresAccountDeletedNotificationsStoreSpec.scala) |
| **Phantom Types**                     | - Provides **compile-time guarantee of correctness** without runtime overhead<br><br> - Encodes domain rules directly in the type signature | Corrupting data by passing wrong IDs, such as `UserId` vs. `DocumentId`<br><br> - Fragile refactoring | [EntityId](/shared-components/src/main/scala/org/fiume/sketch/shared/common/EntityId.scala) |
| **Linear Logic**                | Processes events **concurrently with strict exactly-once guarantees** | Stale events, duplicate-processing. E.g., sending dozens of emails to each client, effectively turning your business into a spam machine (yes, I've seen this happen) | See: <br>- [Postgres as a Lightweight Event Bus](#51-postgres-as-a-lightweight-event-bus) doc <br>- [ScheduledAccountDeletionJob](auth/src/main/scala/org/fiume/sketch/auth/accounts/jobs/ScheduledAccountDeletionJob.scala) (implementation)  |

> ⚡ See [From Ivory Tower to Production Code](docs/best-practices/Applied-Theory.md) for a broader collection of theory-to-practice insights.


### 5.1 Postgres as a Lightweight Event Bus

We backend developers understand the immense value an event-driven architecture can bring to a business when implemented effectively.

But what if you don't have access to a Kafka broker or if a Redis Pub/Sub feels overkill, for example?
In such cases, Postgres' `FOR UPDATE SKIP LOCKED` can be an excellent alternative.

Here's how it works, using the example of an event-driven system to delete a user's personal data from multiple datasets after their account is deleted:

#### User Account Deleted Event

Define a specific event table (the equivalent of a Kafka topic).

```
CREATE TABLE account_deleted_notifications (
    uuid UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id UUID NOT NULL, -- The ID of the deleted user
    recipient VARCHAR(50) NOT NULL, -- The service responsible for processing
    created_at TIMESTAMP DEFAULT NOW() -- When the event was created
);
```

I recommend a single table for each event type to avoid overgeneralisation.

Including `created_at` to the schema adds minimal complexity while offering significant diagnostic value for monitoring stale events. 

#### Microservice Polling

Each service runs a polling job that selects and locks the event for processing:

```
-- Lock the next available event for this consumer
DELETE FROM account_deleted_notifications
WHERE id = (
    SELECT id
    FROM account_deleted_notifications
    WHERE recipient = 'recipient'
    FOR UPDATE SKIP LOCKED
    LIMIT 1
)
RETURNING *;
```

This solution will give us:
 - *Concurrent Processing*: Multiple instances of a service can poll the table for events without locking each other out
 - *Atomicity:* Consuming, Processing and removing the event from the table happens as part of the same transaction
 - *Error Handling*: Atomicity ensures that the event is either fully processed or safely retried
 - *No Double Processing*: `FOR UPDATE SKIP LOCKED` prevents race conditions and duplicate work


## 6. Further Reading

* [Documentation Guidelines](/docs/best-practices/Documentation.md)
* [Release Guidelines](/docs/devops/Releases.md)

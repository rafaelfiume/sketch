# Sketch

[![CircleCI](https://dl.circleci.com/status-badge/img/gh/rafaelfiume/sketch/tree/main.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/rafaelfiume/sketch/tree/main) [<img src="https://img.shields.io/badge/dockerhub-images-blue.svg?logo=LOGO">](<https://hub.docker.com/repository/docker/rafaelfiume/sketch/general>)


**Table of Contents**

1. [Project Philosophy](#1-project-philosophy)
2. [Start Here](#2-start-here)
3. [Modules](#3-modules)
4. ["Academic" Techniques for Real-World Software Engineering](#4-academic-techniques-for-real-world-software-engineering)
    - 4.1 [Postgres as a Lightweight Event Bus](#41-postgres-as-a-lightweight-event-bus)

## 1. Project Philosophy

Sketch is built on the principle of conceptual alchemy: **transforming abstract theory into practical engineering solutions**. 

This backend template uses non-obvious ideas, like using Information Theory to [write clearer docs](docs/best-practices/Documentation.md) and [Category Theory](docs/best-practices/Applied-Theory.md#4-mathematical-foundations-category-theory---safe--composable-components) for building predictable and composable software components.

These diverse theories are applied not for their own sake, but to craft solutions to real-world problems. The result is a product that is secure, maintainable and scalable.

> See the section ["Academic" Techniques for Real-World Software Engineering](#4-academic-techniques-for-real-world-software-engineering) for examples of this philosophy in action.


## 2. Start Here

To quickly run Sketch and its dependencies on your local machine, follow the [Onboarding](docs/start-here/Onboarding.md) guide.


## 3. Modules

Sketch is a small monolith carefully divided into **modules** with clear boundaries.

This design provides two key benefits:
 * Rapid development during early and mid stages
 * A smooth migration path to a microservice architecture when scalaling challenges appear.

 - [Authentication](auth/README.md)

 - [Authorisation](shared-access-control/README.md)

 - [Domain](docs/Domain.md) properties:
   - [Valid document names](shared-domain/src/test/scala/org/fiume/sketch/shared/domain/documents/DocumentSpec.scala)


## 4. "Academic" Techniques for Real-World Software Engineering

| Concept                         | Problem It Solves    | Prevents         | Example      |
|---------------------------------|----------------------|------------------|--------------|
| **Natural Transformation** (`F ~> G`) | **Separates core business logic** (e.g. rules for setting up a user account) **from low-level infrastructure details** (e.g.transactions commit/rollback) | Mixing concerns, causing readability and maintainance nightmare, and making regression tests near impossible | **Define a clear transaction boundary.** <br><br>`val setupAccount = ... // create account, grant access to owner`<br>`setupAccount.commit()` <br><br>See: [UsersManager](auth/src/main/scala/org/fiume/sketch/auth/accounts/UsersManager.scala) (core domain) depends on [Store](shared-components/src/main/scala/org/fiume/sketch/shared/common/app/Store.scala) (infrastructure abstraction) |
| **Isomorphism**                 | **Lossless conversions** between data representations. | Corrupted keys leading to severe authentication failures | Ensuring cryptographic keys can be serialised and deserialised without corruption. <br><br>See: [KeyStringifierSpec](auth/src/test/scala/org/fiume/sketch/auth/KeyStringifierSpec.scala) |
| **Producer-Consumer Streams**   | Build **reliable event-driven systems with clear delivery semantics** | * Lost or duplicated events <br>* The low-level complexity generelly associated with this type of programming | Exactly-once semantics backed by [Postgres as a Lightweight Event Bus](../../README.md). <br><br>`producer.produceEvent(. . .).ccommit`<br>`. . .`<br>`consumer.consumeEvent().flatMap { notification => . . . business logic }`<br><br>See: [PostgresAccountDeletedNotificationsStoreSpec](../../storage/src/it/scala/org/fiume/sketch/storage/auth/postgres/PostgresAccountDeletedNotificationsStoreSpec.scala) |
| **Phantom Types**               | **Compile-time guarantee** that the correct ID type is used, with zero runtime cost | Corrupting data by passing IDs of wrong type (e.g. `UserId` vs. `DocumentId`), fragile refactoring | [EntityId](shared-components/src/main/scala/org/fiume/sketch/shared/common/EntityId.scala) |
| **Linear Logic**                | Processes events **concurrently with strict exactly-once guarantees** | Stale events, duplicate-processing. E.g., sending dozens of emails to each client, effectively turning your business into a spam machine (yes, I've seen this happen) | See: <br>* [Postgres as a Lightweight Event Bus](#41-postgres-as-a-lightweight-event-bus) doc <br>* [ScheduledAccountDeletionJob](../sketch/auth/src/main/scala/org/fiume/sketch/auth/accounts/jobs/ScheduledAccountDeletionJob.scala) (implementation)  |

> ⚡ See [Applied Theory](docs/best-practices/Applied-Theory.md) for a broader collection of theory-to-practice insights.


### 4.1 Postgres as a Lightweight Event Bus

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
 - *Atomicity:* Consuming, processing and removing the event from the table happens as part of the same transaction
 - *Error Handling*: Atomicity ensures that the event is either fully processed or safely retried
 - *No Double Processing*: `FOR UPDATE SKIP LOCKED` prevents race conditions and duplicate work

# Sketch

[![CircleCI](https://dl.circleci.com/status-badge/img/gh/rafaelfiume/sketch/tree/main.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/rafaelfiume/sketch/tree/main) [<img src="https://img.shields.io/badge/dockerhub-images-blue.svg?logo=LOGO">](<https://hub.docker.com/repository/docker/rafaelfiume/sketch/general>)


**Table of Contents**

1. [Project Philosophy](#1-project-philosophy)
2. [Start Here](#2-start-here)
3. [Modules](#3-modules)
4. ["Academic" Techniques that Prove Useful in Real-Life](#4-academic-techniques-that-prove-useful-in-real-life)
    - 4.1 [Postgres as a Lightweight Event Bus](#41-postgres-as-a-lightweight-event-bus)

## 1. Project Philosophy

Sketch is built on the principle of conceptual alchemy: **transforming abstract theory into practical engineering solutions**. 

This backend template creatively uses non-obvious ideas, like using Information Theory to [write clearer docs](docs/best-practices/Documentation.md) and Category Theory for building predictable and composable software components.

These diverse theories are applied not for their own sake, but to craft elegant solutions to real-world problems. The result is a product that is secure, maintainable and scalable.

> See the section ["Academic" Techniques that Prove Useful in Real-Life](#4-academic-techniques-that-prove-useful-in-real-life) for concrete examples of this philosophy in action.

## 2. Start Here

To quickly run Sketch and its dependencies on your local machine, follow the [Onboarding](docs/start-here/Onboarding.md) guide.

> **Note:** Documentation is actively being rewritten to follow our [Documentation Guidelines](docs/best-practices/Documentation.md).

## 3. Modules

Sketch is a small monolith carefully divided into **modules** with clear boundaries.

This design provides two key benefits:
 * Rapid development during early and mid stages
 * A smooth migration path to a microservice a microservice architecture when scalaling challenges appear.

 - [Authentication](auth/README.md):

 - [Authorisation](shared-access-control/README.md)

 - [Domain](docs/Domain.md) properties:
   - [Valid document names](shared-domain/src/test/scala/org/fiume/sketch/shared/domain/documents/DocumentSpec.scala)


E.g. ensuring cryptographic keys can be serialised and deserialised without corruption.

## 4. "Academic" Techniques that Prove Useful in Real-Life

| Concept                         | Problem It Solves    | Prevents         | Example      |
|---------------------------------|----------------------|------------------|--------------|
| **Functor Transformation** (~>) | **Defines cross-cutting concerns like transactions as composable, type-safe boundaries.** | Transactional-mechanics (commit/rollback) from the database leaking into core domain logic | [Store](shared-components/src/main/scala/org/fiume/sketch/shared/common/app/Store.scala) |
| **Isomorphism**                 | **Lossless conversions** between data representations. E.g. Criptographic keys can be serialised and deserialised without corruption | Corrupted keys leading to severe authentication failures | [KeyStringifierSpec](auth/src/test/scala/org/fiume/sketch/auth/KeyStringifierSpec.scala) |
| Semigroups                  | **Accumulate** multiple errors automatically, avoiding multiple calls to `combine` in validation logic | Error-prone boilerplate code; subtle bugs caused by fail-fast validation instead of all errors | [ErrorDetailsLawSpec](/shared-components/src/test/scala/org/fiume/sketch/shared/common/troubleshooting/ErrorDetailsLawsSpec.scala) |
| Phantom Types               | **Compile-time guarantee** that the correct ID type is used, with zero runtime cost | Corrupting data by passing IDs of wrong type (e.g. `UserId` vs. `DocumentId`), fragile refactoring | [EntityId](shared-components/src/main/scala/org/fiume/sketch/shared/common/EntityId.scala) |
| Linear Logic                | Process events **concurrently with strict exactly-once guarantees** | Stale events, duplicate-processing. E.g., sending dozens of emails to each client, effectively turning your business into a spam machine (yes, I've seen this happen) | See: <br>* [Postgres as a Lightweight Event Bus](#41-postgres-as-a-lightweight-event-bus) doc <br>* [ScheduledAccountDeletionJob](../sketch/auth/src/main/scala/org/fiume/sketch/auth/accounts/jobs/ScheduledAccountDeletionJob.scala) (implementation)  |

> ⚡ Coming soon: [Theory in Action] for more non-obvious and yet extremelly useful techniques that can be applied to software engineering day-to-day features implementation.

> ⚡ Coming soon: how tests can be used as (lightweight) proof components work as expected.

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

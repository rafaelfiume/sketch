# Sketch

[![CircleCI](https://dl.circleci.com/status-badge/img/gh/rafaelfiume/sketch/tree/main.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/rafaelfiume/sketch/tree/main) [<img src="https://img.shields.io/badge/dockerhub-images-blue.svg?logo=LOGO">](<https://hub.docker.com/repository/docker/rafaelfiume/sketch/general>)


## Start Here

There's a [Postman collection](docs/Sketch.postman_collection.json) that can be used to send requests to the service,
and a [start-local.sh](/tools/stack/start-local.sh) script to initialise the services locally.

 - [Authentication](docs/Auth.md) properties:
   - [Authenticator](auth/src/test/scala/org/fiume/sketch/auth/AuthenticatorSpec.scala)
   - [Jwt generation & verification](auth/src/test/scala/org/fiume/sketch/auth/JwtIssuerSpec.scala)
   - [Password hashing](shared-auth/src/test/scala/org/fiume/sketch/shared/auth/HashedPasswordSpec.scala)
   - [Salt generation](shared-auth/src/test/scala/org/fiume/sketch/shared/auth/SaltSpec.scala)
   - [Valid usernames](shared-auth/src/test/scala/org/fiume/sketch/shared/auth/UsernameSpec.scala)
   - [Valid passwords](shared-auth/src/test/scala/org/fiume/sketch/shared/auth/PlainPasswordSpec.scala)

 - [Authorisation](docs/Authorisation.md) properties:
   - [Access control](auth/src/test/scala/org/fiume/sketch/auth/UsersManagerSpec.scala)

 - [Design](docs/Design.md)

 - [Domain](docs/Domain.md) properties:
   - [Valid document names](shared-domain/src/test/scala/org/fiume/sketch/shared/domain/documents/DocumentSpec.scala)
   - [User account management](auth/src/test/scala/org/fiume/sketch/auth/UsersManagerSpec.scala)

 - [Error Codes](docs/ErrorCodes.md)

 - [Pipeline & Stack (& Infra)](docs/Pipeline.md)

 - [Workspace](docs/Workspace.md)


### "Academic" techniques that prove useful in real life

 - [Bijective relationship](shared-components/src/test/scala/org/fiume/sketch/shared/common/ServiceStatusContractSpec.scala)
 - [Isomorphism](auth/src/test/scala/org/fiume/sketch/auth/KeyStringifierSpec.scala)
 - [Phantom types](shared-components/src/main/scala/org/fiume/sketch/shared/common/EntityId.scala) (see usage example [here](shared-components/src/test/scala/org/fiume/sketch/shared/common/EntityIdSpec.scala))
 - [Semigroups](shared-components/src/test/scala/org/fiume/sketch/shared/common/troubleshooting/ErrorDetailsLawsSpec.scala)

### Techniques with pure functional programming

 - [Applicatives and validation](shared-auth/src/main/scala/org/fiume/sketch/shared/auth/domain/Passwords.scala)
 - [Controlling (Db) transactions in business domain components with Functor Transformation](shared-components/src/main/scala/org/fiume/sketch/shared/common/algebras/Store.scala) (Via `FunctionK` or `~>`)
 - [fs2.Stream, the basics](shared-components/src/main/scala/org/fiume/sketch/shared/common/jobs/PeriodicJob.scala)
 - [fs2.Stream to test producing and consuming events](storage/src/it/scala/org/fiume/sketch/storage/auth0/postgres/PostgresAccountDeletedNotificationsStoreSpec.scala)
 - [Mutation is very much a part of pure functional programming](shared-auth/src/test/scala/org/fiume/sketch/shared/auth/testkit/UsersStoreContext.scala)
 - [Safe concurrent access and modification](https://github.com/rafaelfiume/sketch/blob/02b5e2b7bbeb6f1c0083ee9be8327b3bd61c13ae/storage/src/it/scala/org/fiume/sketch/storage/auth0/postgres/PostgresEventsStoreSpec.scala#L81)

### A few Scala features in action

 - [Extension methods](shared-account-management/src/main/scala/org/fiume/sketch/shared/account/management/http/model/AccountStateTransitionErrorSyntax.scala)
 - [Intersection types](shared-components/src/test/scala/org/fiume/sketch/shared/common/testkit/JobErrorHandlerContext.scala) (`&`)
 - [Metaprogramming](shared-components/src/main/scala/org/fiume/sketch/shared/common/Macros.scala)
 - [Trait constructors]() << comming soon
 - [Union types](https://github.com/rafaelfiume/sketch/blob/e7371cb27144e1ab20790e5a80648d7b504e2904/auth/src/main/scala/org/fiume/sketch/auth/JwtIssuer.scala#L43)


## Using Postgres as Lightweight Event Bus

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

## Guidelines

 - [Releases](docs/artigiani/Releases.md)
 - [Scripting](docs/artigiani/Scripting)

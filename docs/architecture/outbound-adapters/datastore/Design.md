# Datastore Outbound Adapters - Design Guidelines

DAO outbound adapters implement [ports](/docs/architecture/domain/Design.md#54-ports--algebras) that define persistence capabilities using concrete infrastructure such as PostgreSQL.


> This document is part of the project's [Architecture Guidelines](/README.md#62-architecture).

```
[ Inbound Adapters ] --> [ Application Layer ] --> [ Domain Layer ] <-- [ DAO Outbound Adapters (you're here) ]
```


**Table of Contents:**

1. [Goals](#1-goals)
2. [Principles](#2-principles)
3. [Keep The Datastore Layer Free From](#3-keep-the-datastore-layer-free-from)
4. [Shielding The Domain](#4-shielding-the-domain)
    - 4.1 [Lack of Persistence Model (Technical Debt)](#41-lack-of-persistence-model-technical-debt)
5. [Example - Store Documents](#5-example---store-documents)
6. [Component Design](#6-component-design)
    - 6.1 [Resource Lifecycle Management](#61-resource-lifecycle-management)
    - 6.2 [Enable Transaction Boundaries In The Application Layer](#62-enable-transaction-boundaries-in-the-application-layer)
    - 6.3 [Streaming APIs](#63-streaming-apis)
7. [Adapter Integration Tests](#7-adapter-integration-tests)
8. [Pragmatic Compromises](#8-pragmatic-compromises)
    - 8.1 [Single Database for Multiple Bounded Contexts](#81-single-database-for-multiple-bounded-contexts)
    - 8.2 [Blob Storage](#82-blob-storage)
9. [Further Reading](#9-further-reading)


## 1. Goals

* Implement storage capabilities defined by [ports/algebras](/docs/architecture/domain/Design.md#54-ports--algebras)
* Isolate the domain layer from database-specific concerns (e.g. schema design, SQL, connection pooling)
* Managing connection pooling and transactions (via [Doobie](https://typelevel.org/doobie/))
* Translate database errors to neutral errors appropriate for the application layer


## 2. Principles

* **Database concerns stay local**:
    - Database-specific details (schema, SQL, connection pools, constraint violation, connection errors) must not leak outside the DAO adapter
    - The domain and application layers interact with it only through ports.
* **Composable transactional operations**:
    - DAO adapters must implement ports with support for transaction contexts (e.g. [Store](/shared-components/src/main/scala/org/fiume/sketch/shared/common/app/Store.scala)), enabling the application layer to combine discrete operations into atomic workflows
    - Application layer defines the transaction boundaries. Adapters commit transactions without exposing low-level database mechanics.


## 3. Keep The Datastore Layer Free From

| Concern                               | Proper Layer      | Rationale   |
|---------------------------------------|-------------------|-------------|
| **✗** Business logic                  | Domain Layer      | Keeping domain logic isolated avoids polluting core business models and rules with infrastructure details, preventing a long-term maintenance nightmare |
| **✗** Business workflows (use-cases)  | Application Layer | Mixing in workflows in DAOs makes code brittle and coupled with the infrastructure, making use-cases difficult to unit test |
| **✗** Cross-cutting concerns          | Application Layer | Concerns such as authorisation, monitoring, or retries must be applied consistently at the workflow level. If buried in DAOs, they become scattered, harder to reason about and compose |


## 4. Shielding The Domain

The **domain model must not be forced to mirror the database schema**. To allow both to evolve independently, **use a persistence model (DTOs)** to represent how data is stored in the database.

### 4.1 Lack of Persistence Model (Technical Debt)

Currently, DAOs map domain objects directly to the database schema as a shortcut to reduce boilerplate in early development. Unlike [pragmatic compromises](#8-pragmatic-compromises), this is an architectural-debt.

DTOs must be introduced between domain and schema to restore the architectural boundary and prevent schema changes from rippling into the domain layer (and vice-versa).

Consider using a data transformation library such as [chimney](https://github.com/scalalandio/chimney) to reduce boilerplate when mapping between models.


## 5. Example - Store Documents

**API Definition:**

The port (algebra) defines the contract the adapter must implement:

```scala
trait DocumentsStore[F[_], Txn[_]] extends Store[F, Txn]:
  def fetchDocument(uuid: DocumentId): Txn[Option[DocumentWithId]]

  . . . // remaining operations
```
**See:** [DocumentsStore.scala](/shared-domain/src/main/scala/org/fiume/sketch/shared/domain/documents/algebras/DocumentsStore.scala)

**Implementation:**

```scala
  override def fetchDocument(uuid: DocumentId): ConnectionIO[Option[DocumentWithId]] =
    Statements.selectDocumentById(uuid).option

  // Private statements:
  def selectDocumentById(uuid: DocumentId): Query0[DocumentWithId] =
    sql"""
         |SELECT
         |  d.uuid,
         |  d.name,
         |  d.description,
         |  d.user_id
         |FROM domain.documents d
         |WHERE d.uuid = $uuid
    """.stripMargin.query[DocumentWithId]

  . . . // remaining implementations
```


## 6. Component Design

### 6.1 Resource Lifecycle Management

DAOs are instantiated using factory functions `make`. For production-ready DAOs (as opposed to testing doubles), `make` must return the instance wrapped in a `Resource`.

Example:

```scala
object PostgresDocumentsStore:
  def make[F[_]: Async](tx: Transactor[F]): Resource[F, DocumentsStore[F, ConnectionIO]] = . . .

```

Wrapping DAOs in `Resource[F, Algebra[F, ConnectionIO]]` provides:
  * **Automatic Lifecycle Management**: The connection pool backing the DAO is acquired during initialisation, and shutdown when the DAO instance is no longer in use
  * **Safety guarantees**: Connection pool is always shutdown in proper order even in failure or cancellation scenarios, preventing leaks of expensive resources
  * **Composability**:
    - Multiple DAOs can be initialised as a single `Resource`, sharing the same lifetime managed connection pool
    - E.g. `AppComponents.make` function returns `Resource[F, AppComponents[F]]` - see [AppComponents.scala](/service/src/main/scala/org/fiume/sketch/app/AppComponents.scala).

### 6.2 Enable Transaction Boundaries In The Application Layer

DAOs must fulfill the contract defined by persistence ports in the domain layer.

They must respect the [Store](/shared-components/src/main/scala/org/fiume/sketch/shared/common/app/Store.scala) algebra, which offers support for defining transaction boundaries as first-class operations:

```scala
trait Store[F[_], Txn[_]]:
  val lift: [A] => F[A] => Txn[A]
  val commit: [A] => Txn[A] => F[A]
  . . .
```
* `lift` allows pure or effectful (`F[_]`) computations to be embedded in a transaction context `Txn[_]`
* `commit` executes the composed workflow atomically against a database.

DAO adapters implemented with Doobie and PostgreSQL can extend [AbstractPostgresStore](/storage/src/main/scala/org/fiume/sketch/storage/postgres/AbstractPostgresStore.scala) to inherit the canonical `lift` and `commit` implementations.

This design allows the application layer to **compose discrete operations** into **atomic business workflows**, without dealing with low-level database concerns.

**Example - Atomic Account Creation Workflow:**

Ports define contracts for discrete transactional operations:

```scala
trait UsersStore[F[_], Txn[_]: Monad] extends Store[F, Txn]:
  def createAccount(credentials: UserCredentials): Txn[UserId]
  . . .

trait AccessControl[F[_], Txn[_]: Monad] extends Store[F, Txn]:
  def grantGlobalAccess(userId: UserId, role: GlobalRole): Txn[Unit]
  def grantAccess[T <: Entity](userId: UserId, entityId: EntityId[T], role: ContextualRole): Txn[Unit]
  . . .
```

Adapters implementing those ports are injected into application layer components during runtime:

```scala
object UsersManager:
  def make[F[_]: Sync, Txn[_]: Monad](. . . /*dependencies*/): UsersManager[F] =
    . . .
    new UsersManager[F]:
      override def createAccount(username: Username, password: PlainPassword, globalRole: Option[GlobalRole]): F[UserId] =
        // Pure computation
        val credentials = for
          salt <- Salt.generate()
          hashedPassword <- HashedPassword.hashPassword(password, salt)
        yield UserCredentials(username, hashedPassword, salt)

        // setUpAccount defines an atomic set of operations
        val setUpAccount = for
          creds <- store.lift { credentials } // Lifts a pure computation into a Transaction Context
          userId <- store.createAccount(creds) // Port: Persist the account
          _ <- accessControl.grantAccess(userId, userId, ContextualRole.Owner) // Port: Grant user access to the account
          _ <- globalRole.fold(ifEmpty = ().pure[Txn])(accessControl.grantGlobalAccess(userId, _)) // Port: Optionally, provide use global access to system resources
        yield userId

        setUpAccount.commit() // Execute the transaction if there are no failures/s
```

See also [Persistence Ports](/docs/architecture/domain/Design.md#541-persistence-ports) for the definition of transaction boundary capabilities in the domain layer.

### 6.3 Streaming APIs

Provide support for streaming APIs when:
  * Content is too large to fit in memory
  * Clients benefit from backpressure.

```scala
trait DocumentsStore[F[_], Txn[_]] extends Store[F, Txn]:
  // discrete operation
  def fetchDocument(uuid: DocumentId): Txn[Option[DocumentWithId]]

  // document size might be too large to be entirely loaded in memory
  def documentStream(uuid: DocumentId): fs2.Stream[Txn, Byte]

  // potentially large number of documents - can be processed incrementally
  def fetchDocuments(uuids: fs2.Stream[Txn, DocumentId]): fs2.Stream[Txn, DocumentWithId]

  . . . // remaining operations
```
**See:** [DocumentsStore.scala](/shared-domain/src/main/scala/org/fiume/sketch/shared/domain/documents/algebras/DocumentsStore.scala)


## 7. Adapter Integration Tests

DAO adapters must be verified with integration tests against a real database:
  * Always use the **same database engine as production** to avoid discrepancies ("it works on my machine" problems)
  * Use Docker and migration scripts to spin up databases with the same schema across different environments.

> See [DockerPostgresSuite](/storage/src/it/scala/org/fiume/sketch/storage/testkit/DockerPostgresSuite.scala) for utilities to start/stop a Postgres Docker container, initialise a `Transactor`, clean tables and other test utilities.

For example, to test account deletions:

```scala
class PostgresUsersStoreSpec
    extends CatsEffectSuite
    with ...

  test("deletes user account"):
    forAllF { (credentials: UserCredentials) =>
      // Set up: clean table, initialise a Doobie `Transactor`.
      will(cleanStorage) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            // *given* any account
            userId <- store.createAccount(credentials).ccommit // execute the transaction with the extension method `ccommit`

            // *when* deleting that account
            result <- store.deleteAccount(userId).ccommit

            // *then* the account cannot be retrieved any longer
            account <- store.fetchAccount(userId).ccommit
          yield
            assertEquals(result, userId.some)
            assert(account.isEmpty)
        }
      }
    }
```
> **See:** [PostgresUsersStoreSpec.scala](/storage/src/it/scala/org/fiume/sketch/storage/auth/postgres/PostgresUsersStoreSpec.scala).


## 8. Pragmatic Compromises

### 8.1 Single Database for Multiple Bounded Contexts

A single database is used for Identity and Access Control (`auth` schema), and Project (`domain` schema) modules.

The rationale is:
  * **Development speed**: All schemas defined in the same place, and no distributed system complexity in the MVP phase
  * **Data consistency**: Enables atomic workflows composed of different bounded context operations
  * **Operational simplicity**: Single backup and maintenance
  * **Foreign keys**: Allows referential integrity between different bounded contexts.

Consider extracting a schema to a dedicated database when:
  * **Different scaling requirements** between bounded contexts emerge
  * **Team ownership** leads to isolation of and enforced boundaries between schemas
  * **Compliance** requires physical separation of data.

Migration readiness is achieved with:
  * Separated schemas make extraction feasible
  * Referential integrity can be enforced at application level
  * Cross-schema joins will require event-driven flow or data aggregation through different API calls.

### 8.2 Blob Storage

Binary files (e.g. images, PDFs) are currently stored directly in Postgres `BYTEA` column rather than in a dedicated object storage (e.g. S3).

This approach works because:
  * **MVP simplicity**: Avoids the need of managing external pointers or pay for object storage services
  * **Facilitates access control**: The entire authorisation mechanism is implemented on top of a single database
  * **Small scale**: There are no performance concerns in the short term due to small files and low volume expectation.

Migration to an object storage should be considered if:
  * **Database performance degrades noticeably**: Queries or backups become slow due to large binaries
  * **Storage needs grow**: Individual files > 100MB, or total binary size > 100GB
  * **New requirements**: E.g. direct client uploads, CDN distribution, or tiered storage
  * **Compliance**: Business or legal requirements mandate separation of binaries and structured data.

Future direction:
  * Migrate binaries to a dedicated object storage
  * Keep the same port (algebra). The adapter will be responsible for persisting metadata in the database and binaries in the object storage.


## 9. Further Reading

* [Cats-Effect: Resource](https://typelevel.org/cats-effect/docs/std/resource): The canonical reference for managed resources with cats-effect.
* [Doobie: Managing Connections](https://typelevel.org/doobie/docs/14-Managing-Connections.html): For a deep dive on how Doobie applications manage database connections.

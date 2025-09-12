# Applied Theory


## 1. Type Theory -> Encoding Invariants at Compile Time

Because we prefer to catch bugs during compilation rather than execution.


| Concept                               | Problem It Solves     | Prevents             | Example      |
|---------------------------------------|-----------------------|----------------------|--------------|
| **Union Types** <br> (`A \| B`)  | * Specifies that an error **can be one of multiple types** <br>* Enforces each type is handled **at compile time** <br>* Brings clarity and **less boilerplate** | * Missing or incorrect error handling only spotted at runtime <br>* Cluttering the codebase with wrappers (e.g. `Either`) <br>* Lost type-safety and clarity using broader types like `Any` | [JwtIssuer](../../auth/src/main/scala/org/fiume/sketch/auth/JwtIssuer.scala) |
| **Intersection Types** <br> (`A & B`) | * **Composes behaviour** with mixin and [structural typing](https://en.wikipedia.org/wiki/Structural_type_system) <br>* **Demands capabilities** | Boilerplate and rigidity with fake ancestors. <br>E.g., `JobErrorHandler & Inspect` is more powerful than `InspectableJobErrorHandler` | [JobErrorHandlerContext](../../shared-components/src/test/scala/org/fiume/sketch/shared/common/testkit/JobErrorHandlerContext.scala) |
| **Phantom Types**                     | **Compile-time guarantee** that the correct ID type is used, with zero runtime cost | Corrupting data by passing IDs of wrong type (e.g. `UserId` vs. `DocumentId`), fragile refactoring | [EntityId](../../shared-components/src/main/scala/org/fiume/sketch/shared/common/EntityId.scala) |
| **Extension Methods**                 | * **Enriches** types without modifying their sourcecode <br>* Improves API **fluency and discoverability** | * Harder to discover, e.g. `JobUtils.toResponse(job)` . <br>* Clumsier to read: `ScheduledForPermanentDeletionResponse(job.uuid, job.userId, job.permanentDeletionAt)` vs. `job.asResponsePayload` | [Users](../../shared-auth/src/main/scala/org/fiume/sketch/shared/auth/http/model/Users.scala). See <br>`extension (job: AccountDeletionEvent.Scheduled)` |


## 2. Stream Processing -> Declarative & Resilient Flows

Streams excels modelling asynchronous, concurrent and infinite data flows.
They are a natural fit for encoding complex business in a single composable flow.

| Concept                         | Benefit     | Prevents             | Example      |
|---------------------------------|-------------|----------------------|--------------|
| **Composable** Stream           | Models infinite streams through a **declarative, resource-safe DSL** | * Business logic hidden inside low-level scheduling code <br>* Thread and IO resources leaks <br>* Risk of infinite loops | Run a [periodic job](../../shared-components/src/main/scala/org/fiume/sketch/shared/common/jobs/PeriodicJob.scala) |
| **First-class error handling**  | Errors as values flowing through the stream, enabling **guaranteed, declarative, composable and centralised error handling** | Brittle and scatterred try/catch's leading to inconsistent behaviour, silent failures, interrupted streams | Convert error to value, retry, skip, log, track. Keep running runs jobs periodically, even after encountering errors.<br><br>See: [PeriodicJobSpec ](../../shared-components/src/test/scala/org/fiume/sketch/shared/common/jobs/PeriodicJobSpec.scala) |
| **Cancellable** Stream          | Clean and predictable **asynchronous workflows interruption** | * Business logic entangled with interruption code <br> * Cancellation failures that lead to resource leaks and non-deterministic behaviour | Stop running jobs.<br><br>See: [PeriodicJobSpec ](../../shared-components/src/test/scala/org/fiume/sketch/shared/common/jobs/PeriodicJobSpec.scala) |
| **Concurrent** Streams          | Predictable and safe execution of **parallel, independent workflows** | Deadlocks, race conditions and other forms non-deterministic behaviour, like heinsenbugs and starvation | `httpServiceStream // core service`<br>`.concurrently(accountPermanentDeletionStream) // account lifecycle`<br>`.concurrently(sketchUserDataDeletionStream) // data cleanup`<br><br>See: [App](../../service/src/main/scala/org/fiume/sketch/app/App.scala) |
| **Producer-Consumer Streams**   | Build **reliable event-driven systems with clear delivery semantics** | * Lost or duplicated events <br>* The low-level complexity generelly associated with this type of programming | Exactly-once semantics backed by [Postgres as a Lightweight Event Bus](../../README.md). <br><br>`producer.produceEvent(. . .).ccommit`<br>`. . .`<br>`consumer.consumeEvent().flatMap { notification => . . . business logic }`<br><br>See: [PostgresAccountDeletedNotificationsStoreSpec](../../storage/src/it/scala/org/fiume/sketch/storage/auth/postgres/PostgresAccountDeletedNotificationsStoreSpec.scala) |



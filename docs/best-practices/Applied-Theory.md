# From Ivory Tower to Production Code

Bridging the gap between concepts that seem theoretical or abstract to real-world software engineering.

---

<small>(The benefits illustrated in each example are non-exhaustive.)</small>

**Table of Contents**

1. [Types -> Compile Time Invariants](#1-types---compile-time-invariants)
2. [Stream Processing -> Expressive & Resilient Flows](#2-stream-processing---expressive--resilient-flows)
3. [Managing Effects -> Composable & Predictable Real-World Programs](#3-managing-effects---composable--predictable-real-world-programs)
4. [Mathematical Foundations (Category Theory) -> Safe & Composable Components](#4-mathematical-foundations-category-theory---safe--composable-components)


## 1. Types -> Compile Time Invariants

Because we prefer to catch bugs during compilation rather than execution.

| Concept                               | Benefits              | Prevents             | Example      |
|---------------------------------------|-----------------------|----------------------|--------------|
| **Union Types** <br> (`A \| B`)  | - Specifies that values **can be one of multiple types**<br><br> - Requires all cases are handled **at compile time**<br><br> - **Reduces boilerplate** compared to wrappers like `Either` | - Missing or incorrect error handling discovered on at runtime<br><br> - Cluttering code with wrappers<br><br> - Losing type-safety by falling back to `Any` or broad types | See `jwtError: Throwable \| JwtVerificationError` in [JwtIssuer](/auth/src/main/scala/org/fiume/sketch/auth/JwtIssuer.scala) |
| **Intersection Types** <br> (`A & B`) | - **Composes behaviour** with mixin and [structural typing](https://en.wikipedia.org/wiki/Structural_type_system)<br><br> - Specifies that value must have **multiple capabilities** | Boilerplate and rigid hierarchies with fake ancestors | - `JobErrorHandler & Inspect` is more powerful than `InspectableJobErrorHandler`<br><br> See: [JobErrorHandlerContext](/shared-components/src/test/scala/org/fiume/sketch/shared/common/testkit/JobErrorHandlerContext.scala) |
| **Phantom Types**                     | - Provides **compile-time guarantee of correctness** without runtime overhead<br><br> - Encodes domain rules directly in the type signature | Corrupting data by passing wrong IDs, such as `UserId` vs. `DocumentId`<br><br> - Fragile refactoring | [EntityId](/shared-components/src/main/scala/org/fiume/sketch/shared/common/EntityId.scala) |
| **Extension Methods**                 | - **Enriches** types without modifying their sourcecode<br><br> - Improves API **fluency and discoverability** | - APIs that are harder to discover<br><br> - Verbose, less readable code | - Compare:<br>`ScheduledForPermanentDeletionResponse(job.uuid, job.userId, job.permanentDeletionAt)` vs. `job.asResponsePayload`<br><br> - See: [asResponsePayload](/shared-auth/src/main/scala/org/fiume/sketch/shared/auth/http/model/Users.scala#L17) |

---

## 2. Stream Processing -> Expressive & Resilient Flows

Streams excels modelling asynchronous, concurrent and infinite data flows.
They are a natural fit for encoding complex business in a single composable flow.

| Concept                         | Benefits    | Prevents             | Example      |
|---------------------------------|-------------|----------------------|--------------|
| **Composable** Stream           | Models infinite streams through a **declarative, resource-safe DSL** | - Business logic hidden inside low-level scheduling code<br><br> - Thread and IO resources leaks<br><br> - Risk of infinite loops | Run a [periodic job](/shared-components/src/main/scala/org/fiume/sketch/shared/common/jobs/PeriodicJob.scala) |
| **First-class error handling**  | Errors as values flowing through the stream, enabling **guaranteed, declarative, composable and centralised error handling** | Brittle and scattered try/catch's leading to inconsistent behaviour, silent failures, interrupted streams | Convert error to value, retry, skip, log, track. Keep running runs jobs periodically, even after encountering errors.<br><br>See: [PeriodicJobSpec ](/shared-components/src/test/scala/org/fiume/sketch/shared/common/jobs/PeriodicJobSpec.scala) |
| **Cancellable** Stream          | Clean and predictable **asynchronous workflows interruption** | - Business logic entangled with interruption code<br><br> - Cancellation failures that lead to resource leaks and non-deterministic behaviour | Stop running jobs.<br><br>See: [PeriodicJobSpec ](/shared-components/src/test/scala/org/fiume/sketch/shared/common/jobs/PeriodicJobSpec.scala) |
| **Concurrent** Streams          | Predictable and safe execution of **parallel, independent workflows** | Deadlocks, race conditions and other forms non-deterministic behaviour, like heinsenbugs and starvation | `httpServiceStream // core service`<br>`.concurrently(accountPermanentDeletionStream) // account lifecycle`<br>`.concurrently(sketchUserDataDeletionStream) // data cleanup`<br><br>See: [App](/service/src/main/scala/org/fiume/sketch/app/App.scala) |

---

## 3. Managing Effects -> Composable & Predictable Real-World Programs

Because developers need I/O, concurrency, mutation, failures. And we prefer to reason about them locally.

| Concept                         | Benefits             | Prevents         | Example      |
|---------------------------------|----------------------|------------------|--------------|
| Controlled Mutation             | **Safe and performant shared, mutable state** in concurrent contexts | The **hard-to-reproduce bugs** caused by race conditions, dirty reads and data corruption | Implementing **fully-functional storage components for integration tests**.<br><br>- Replaces databases and other external dependencies with fast, deterministic, in-memory versions.<br>- Enables unit testing components in concurrent contexts without flakiness.<br><br> See: Tests in [UsersManagerSpec](/auth/src/test/scala/org/fiume/sketch/auth/accounts/UsersManagerSpec.scala) that use an [**in-Memory Implementation**](/shared-auth/src/test/scala/org/fiume/sketch/shared/auth/testkit/UsersStoreContext.scala#L39) of [UsersStore](/shared-auth/src/main/scala/org/fiume/sketch/shared/auth/algebras/UsersStore.scala) interface. |

---

## 4. Mathematical Foundations (Category Theory) -> Safe & Composable Components

Category Theory can lead to predictable and composable components with safety guarantees.

| Concept                         | Benefits             | Prevents         | Example      |
|---------------------------------|----------------------|------------------|--------------|
| **Natural Transformation** (`F ~> G`) | **Separates core business logic** (e.g. rules for setting up a user account) **from low-level infrastructure details** (e.g.transactions commit/rollback) | Mixing concerns, causing readability and maintenance nightmare, and making unit tests near impossible | **Define a clear transaction boundary.** <br><br>`val setupAccount = ... // create account, grant access to owner`<br>`setupAccount.commit()` <br><br>See: [UsersManager](/auth/src/main/scala/org/fiume/sketch/auth/accounts/UsersManager.scala) (core domain) depends on [TransactionManager](../shared-components/src/main/scala/org/fiume/sketch/shared/common/app/TransactionManager.scala) (infrastructure abstraction) |
| **Isomorphism**                 | **Lossless conversions** between data representations. | Corrupted keys leading to severe authentication failures | Ensuring cryptographic keys can be serialised and deserialised without corruption. <br><br>See: [KeyStringifierSpec](/auth/src/test/scala/org/fiume/sketch/auth/KeyStringifierSpec.scala) |
| **Semigroups**                  | **Accumulates** multiple errors automatically, avoiding multiple calls to `combine` in validation logic | Error-prone boilerplate code; subtle bugs caused by fail-fast validation instead of all errors | [ErrorDetailsLawSpec](/shared-components/src/test/scala/org/fiume/sketch/shared/common/troubleshooting/ErrorDetailsLawsSpec.scala) |

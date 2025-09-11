# Applied Theory


## 1. Type Theory -> Encoding Invariants at Compile Time

Because we prefer to catch bugs during compilation rather than execution.


| Concept                               | Problem It Solves     | Prevents             | Example      |
|---------------------------------------|-----------------------|----------------------|--------------|
| **Union Types** <br> (`A \| B`)  | * Specifies that an error **can be one of multiple types** <br>* Enforces each type is handled **at compile time** <br>* Brings clarity and **less boilerplate** | * Missing or incorrect error handling only spotted at runtime <br>* Cluttering the codebase with wrappers (e.g. `Either`) <br>* Lost type-safety and clarity using broader types like `Any` | [JwtIssuer](../../auth/src/main/scala/org/fiume/sketch/auth/JwtIssuer.scala) |
| **Intersection Types** <br> (`A & B`) | * **Composes behaviour** with mixin and [structural typing](https://en.wikipedia.org/wiki/Structural_type_system) <br>* **Demands capabilities** | Boilerplate and rigidity with fake ancestors. <br>E.g., `JobErrorHandler & Inspect` is more powerful than `InspectableJobErrorHandler` | [JobErrorHandlerContext](../../shared-components/src/test/scala/org/fiume/sketch/shared/common/testkit/JobErrorHandlerContext.scala) |
| **Phantom Types**                     | **Compile-time guarantee** that the correct ID type is used, with zero runtime cost | Corrupting data by passing IDs of wrong type (e.g. `UserId` vs. `DocumentId`), fragile refactoring | [EntityId](../../shared-components/src/main/scala/org/fiume/sketch/shared/common/EntityId.scala) |
| **Extension Methods**                 | * **Enriches** types without modifying their sourcecode <br>* Improves API **fluency and discoverability** | * Harder to discover, e.g. `JobUtils.toResponse(job)` . <br>* Clumsier to read: `ScheduledForPermanentDeletionResponse(job.uuid, job.userId, job.permanentDeletionAt)` vs. `job.asResponsePayload` | [Users](../../shared-auth/src/main/scala/org/fiume/sketch/shared/auth/http/model/Users.scala). See <br>`extension (job: AccountDeletionEvent.Scheduled)` |

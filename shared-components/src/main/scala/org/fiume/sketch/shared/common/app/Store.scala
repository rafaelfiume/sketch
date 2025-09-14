package org.fiume.sketch.shared.common.app

/**
 * Acts as a boundary between business logic and infrastructure, abstracting how values are lifted into, and latter committed
 * within, a transactional context.
 *
 * @tparam F
 *   Represents the runtime, the effect that actually does the work (e.g. `IO`).
 * @tparam Txn
 *   The transactional context effect type (e.g. Doobie's `ConnectionIO`). A value of type `Txn[A]` describes a sequence of
 *   operations that form a single atomic transaction.
 *
 * #### Benefits
 *
 * ##### 1. Composability
 *
 * Operations defined within the transactional context `Txn` can be composed monadically (i.e. with `map` and `flatMap`). This
 * allows to build complex transactions from smaller, focused operations (e.g. `createAccount`, `grantAccess`).
 *
 * ##### 2. Testability
 *
 * Business logic is expressed as `Txn[A]`, a description of business operations. This keeps the same business logic isolated
 * from the transaction implementation details, allowing it to:
 *   - Run in production against a real database, using a `Store` that wraps the business logic in a true transactional boundary
 *     (BEGIN, COMMIT, ROLLBACK)
 *   - Run in unit tests, using an in-memory `Store` implemented with `Ref` and `IO` to simulate stateful, atomic updates.
 *
 * ##### 3. Type Safety
 *
 * The compiler enforces that a transactional program `Txn[A]` must be explicitilly commited to be transformed into an executable
 * program `F[A]`. This makes it impossible for a defined block of business logic to be accidently left out of a transaction.
 *
 * ##### 4. Resource Safety
 *
 * The well-defined transaction boundary represented by `Txn[A]` program ensures that database connections are acquired, released
 * and transactions are either committed or rolled back.
 */
trait Store[F[_], Txn[_]]:

  /**
   * Lifts a value from the runtime effect `F` into the transactional context `Txn`, i.e. a natural transformation `F ~> Txn`.
   *
   * Use `lift` to incorporate non-transactional operations into the transactional context. For example:
   *   - Pure functions
   *   - Logging
   *   - Calls to external APIs wrapped in `F`.
   *
   * > **Important**: in case the transaction is **rolled back**, these lifited operations will **not be reverted**. For
   * example, a logged message will still be output. It's the programer's responsibility to revert such actions when necessary.
   */
  val lift: [A] => F[A] => Txn[A]

  /**
   * Commits a transactional program `Txn[A]`, yielding a executable program `F[A]`, i.e. natural transformation `Txn ~> F`.
   *
   * This is where the transactional mechanics (BEGIN, COMMIT, ROLLBACK) are executed when the resulting `F[A]` program is run.
   */
  val commit: [A] => Txn[A] => F[A]

  val commitStream: [A] => fs2.Stream[Txn, A] => fs2.Stream[F, A]

object syntax:

  object StoreSyntax:
    extension [F[_], Txn[_], A](txn: Txn[A])(using store: Store[F, Txn]) def commit(): F[A] = store.commit(txn)

    extension [F[_], Txn[_], A](stream: fs2.Stream[Txn, A])(using store: Store[F, Txn])
      def commitStream(): fs2.Stream[F, A] = store.commitStream(stream)

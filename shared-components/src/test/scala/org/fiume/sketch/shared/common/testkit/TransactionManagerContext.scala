package org.fiume.sketch.shared.testkit

import cats.effect.IO
import cats.implicits.*
import org.fiume.sketch.shared.common.app.TransactionManager
import org.fiume.sketch.shared.testkit.TxRef

trait TransactionManagerContext:
  def makeTransactionManager(refs: List[TxRef[?]]): TransactionManager[IO, IO] =
    new TransactionManager[IO, IO]:
      override val lift: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

      override val commit: [A] => IO[A] => IO[A] = [A] =>
        (action: IO[A]) =>
          action.attempt.flatMap {
            case Right(a) =>
              refs.traverse_(_.commit) *> IO.pure(a)
            case Left(e) =>
              refs.traverse_(_.rollback) *> IO.raiseError(e)
        }

      override val commitStream: [A] => fs2.Stream[IO, A] => fs2.Stream[IO, A] = [A] => (action: fs2.Stream[IO, A]) => action

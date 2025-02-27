package org.fiume.sketch.shared.common.app

trait Store[F[_], Txn[_]]:
  val lift: [A] => F[A] => Txn[A]
  val commit: [A] => Txn[A] => F[A]
  val commitStream: [A] => fs2.Stream[Txn, A] => fs2.Stream[F, A]

object syntax:

  object StoreSyntax:
    extension [F[_], Txn[_], A](txn: Txn[A])(using store: Store[F, Txn]) def commit(): F[A] = store.commit(txn)

    extension [F[_], Txn[_], A](stream: fs2.Stream[Txn, A])(using store: Store[F, Txn])
      def commitStream(): fs2.Stream[F, A] = store.commitStream(stream)

package org.fiume.sketch.shared.app.algebras

trait Store[F[_], Txn[_]]:
  val commit: [A] => Txn[A] => F[A]
  val lift: [A] => F[A] => Txn[A]

object Store:
  object Syntax:
    extension [F[_], Txn[_], A](txn: Txn[A])(using store: Store[F, Txn]) def commit(): F[A] = store.commit(txn)

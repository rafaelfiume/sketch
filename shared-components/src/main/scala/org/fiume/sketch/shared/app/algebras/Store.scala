package org.fiume.sketch.shared.app.algebras

trait Store[F[_], Txn[_]]:
  val lift: [A] => F[A] => Txn[A]
  val commit: [A] => Txn[A] => F[A]
  val commitStream: [A] => fs2.Stream[Txn, A] => fs2.Stream[F, A]

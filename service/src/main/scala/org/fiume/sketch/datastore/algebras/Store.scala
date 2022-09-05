package org.fiume.sketch.datastore.algebras

trait Store[F[_], Txn[_]]:
  val commit: [A] => Txn[A] => F[A]
  val lift: [A] => F[A] => Txn[A]

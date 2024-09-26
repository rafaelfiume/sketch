package org.fiume.sketch.shared.app.syntax

import org.fiume.sketch.shared.app.algebras.Store

// TODO Move this to Store (see JobErrorHandler)
object StoreSyntax:

  extension [F[_], Txn[_], A](txn: Txn[A])(using store: Store[F, Txn]) def commit(): F[A] = store.commit(txn)

  extension [F[_], Txn[_], A](stream: fs2.Stream[Txn, A])(using store: Store[F, Txn])
    def commitStream(): fs2.Stream[F, A] = store.commitStream(stream)

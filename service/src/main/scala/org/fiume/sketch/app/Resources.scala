package org.fiume.sketch.app

import cats.effect.{Async, Resource}
import doobie.ConnectionIO
import doobie.util.transactor.Transactor
import org.fiume.sketch.shared.app.algebras.HealthCheck
import org.fiume.sketch.storage.algebras.DocumentsStore
import org.fiume.sketch.storage.postgres.{DbTransactor, PostgresStore}

trait Resources[F[_]]:
  val store: DocumentsStore[F, ConnectionIO] & HealthCheck[F]

object Resources:
  def make[F[_]](config: ServiceConfig)(using F: Async[F]): Resource[F, Resources[F]] =
    for
      transactor <- DbTransactor.make(config.db)
      store0 <- PostgresStore.make[F](transactor)
    yield new Resources[F]:
      override val store: DocumentsStore[F, ConnectionIO] & HealthCheck[F] = store0

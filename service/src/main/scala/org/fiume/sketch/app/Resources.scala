package org.fiume.sketch.app

import cats.effect.{Async, Resource}
import doobie.ConnectionIO
import doobie.util.transactor.Transactor
import org.fiume.sketch.shared.app.algebras.HealthCheck
import org.fiume.sketch.storage.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.postgres.PostgresDocumentsStore
import org.fiume.sketch.storage.postgres.{DbTransactor, PostgresHealthCheck}

trait Resources[F[_]]:
  val healthCheck: HealthCheck[F]
  val store: DocumentsStore[F, ConnectionIO]

object Resources:
  def make[F[_]](config: ServiceConfig)(using F: Async[F]): Resource[F, Resources[F]] =
    for
      transactor <- DbTransactor.make(config.db)
      healthCheck0 <- PostgresHealthCheck.make[F](transactor)
      store0 <- PostgresDocumentsStore.make[F](transactor)
    yield new Resources[F]:
      override val healthCheck: HealthCheck[F] = healthCheck0
      override val store: DocumentsStore[F, ConnectionIO] = store0

package org.fiume.sketch.app

import cats.effect.{Async, Resource}
import doobie.ConnectionIO
import doobie.util.transactor.Transactor
import org.fiume.sketch.app.SketchVersions.VersionFile
import org.fiume.sketch.shared.app.algebras.{HealthCheck, Versions}
import org.fiume.sketch.storage.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.postgres.PostgresDocumentsStore
import org.fiume.sketch.storage.postgres.{DbTransactor, PostgresHealthCheck}

trait Resources[F[_]]:
  val healthCheck: HealthCheck[F]
  val versions: Versions[F]
  val documentsStore: DocumentsStore[F, ConnectionIO]

object Resources:
  def make[F[_]](config: ServiceConfig)(using F: Async[F]): Resource[F, Resources[F]] =
    for
      transactor <- DbTransactor.make(config.db)
      healthCheck0 <- PostgresHealthCheck.make[F](transactor)
      versions0 <- SketchVersions.make[F](config.env, VersionFile("sketch.version"))
      documentsStore0 <- PostgresDocumentsStore.make[F](transactor)
    yield new Resources[F]:
      override val healthCheck: HealthCheck[F] = healthCheck0
      override val versions: Versions[F] = versions0
      override val documentsStore: DocumentsStore[F, ConnectionIO] = documentsStore0

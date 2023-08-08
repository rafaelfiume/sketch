package org.fiume.sketch.storage.postgres

import cats.effect.{Async, Resource}
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import org.fiume.sketch.shared.app.algebras.HealthCheck
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth.Infra

object PostgresHealthCheck:
  def make[F[_]: Async](tx: Transactor[F]): Resource[F, PostgresHealthCheck[F]] =
    Resource.pure { new PostgresHealthCheck[F](tx) }

private class PostgresHealthCheck[F[_]: Async] private (tx: Transactor[F]) extends HealthCheck[F]:

  override def check: F[ServiceHealth] =
    Statements.healthCheck
      .transact(tx)
      .as(ServiceHealth.healthy(Infra.Database))
      .recover(_ => ServiceHealth.faulty(Infra.Database))

private object Statements:
  val healthCheck: ConnectionIO[Int] = sql"select 42".query[Int].unique

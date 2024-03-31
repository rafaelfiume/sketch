package org.fiume.sketch.storage.postgres

import cats.effect.{Async, Resource}
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import org.fiume.sketch.shared.app.ServiceStatus.{DependencyStatus, Status}
import org.fiume.sketch.shared.app.ServiceStatus.Dependency.*
import org.fiume.sketch.shared.app.algebras.HealthChecker

object PostgresHealthCheck:
  def make[F[_]: Async](tx: Transactor[F]): Resource[F, PostgresHealthCheck[F]] =
    Resource.pure { new PostgresHealthCheck[F](tx) }

private class PostgresHealthCheck[F[_]: Async] private (tx: Transactor[F])
    extends HealthChecker.DependencyHealthChecker[F, Database]:

  override def check(): F[DependencyStatus[Database]] =
    Statements.healthCheck
      .transact(tx)
      .as(DependencyStatus(database, Status.Ok))
      .recover(_ => DependencyStatus(database, Status.Degraded))

private object Statements:
  val healthCheck: ConnectionIO[Int] = sql"select 42".query[Int].unique

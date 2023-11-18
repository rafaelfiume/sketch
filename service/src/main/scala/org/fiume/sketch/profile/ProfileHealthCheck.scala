package org.fiume.sketch.profile

import cats.effect.{Async, Resource}
import cats.implicits.*
import fs2.io.net.Network
import org.fiume.sketch.shared.app.ServiceStatus
import org.fiume.sketch.shared.app.ServiceStatus.Dependency.*
import org.fiume.sketch.shared.app.ServiceStatus.DependencyStatus
import org.fiume.sketch.shared.app.ServiceStatus.json.given
import org.fiume.sketch.shared.app.algebras.HealthCheck
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.client.*
import org.http4s.ember.client.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

object ProfileHealthCheck:
  def make[F[_]: Async: Network](): Resource[F, ProfileHealthCheck[F]] =
    given LoggerFactory[F] = Slf4jFactory.create[F]
    EmberClientBuilder.default[F].build.map(new ProfileHealthCheck(_))

private class ProfileHealthCheck[F[_]: Async] private (client: Client[F]) extends HealthCheck.DependencyHealth[F, Profile]:
  override def check(): F[DependencyStatus[Profile]] =
    client
      .expect[ServiceStatus]("http://localhost:3030/status")
      .flatMap { s => Async[F].delay { println(s) }.as(s)}
      .map(s => DependencyStatus[Profile](profile, s.status))

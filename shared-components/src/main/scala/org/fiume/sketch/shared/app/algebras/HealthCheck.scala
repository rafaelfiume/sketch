package org.fiume.sketch.shared.app.algebras

import cats.data.NonEmptyList
import cats.implicits.*
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth.Infra

trait HealthCheck[F[_]]:
  def check: F[ServiceHealth]

object HealthCheck:
  /*
   * We could have the algebra returning `IoR` instead of `Left`.
   * It would give us a more detailed information in case of faulty infrastructure at a cost,
   * noticibly the lack of circe codec implementation for `IoR`.
   */
  type ServiceHealth = Either[NonEmptyList[Infra], List[Infra]]

  object ServiceHealth:
    def healthy(infra: Infra): ServiceHealth = healthy(NonEmptyList.one(infra))
    def healthy(infras: NonEmptyList[Infra]): ServiceHealth = infras.toList.asRight[NonEmptyList[Infra]]

    def faulty(infra: Infra): ServiceHealth = faulty(NonEmptyList.one(infra))
    def faulty(infras: NonEmptyList[Infra]): ServiceHealth = infras.asLeft[List[Infra]]

    def noDependencies(): ServiceHealth = List().asRight[NonEmptyList[Infra]]

    enum Infra:
      case Database

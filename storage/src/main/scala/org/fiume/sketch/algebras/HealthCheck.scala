package org.fiume.sketch.algebras

// TODO duplicated. Should it be moved to a shared dependency?
trait HealthCheck[F[_]]:
  def healthCheck: F[Unit]

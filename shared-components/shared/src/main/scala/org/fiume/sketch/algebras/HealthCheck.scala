package org.fiume.sketch.algebras

trait HealthCheck[F[_]]:
  def healthCheck: F[Unit] // TODO Change signature to return Boolean

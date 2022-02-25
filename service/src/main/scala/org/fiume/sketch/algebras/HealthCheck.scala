package org.fiume.sketch.algebras

trait HealthCheck[F[_]]:
  def healthCheck: F[Unit]

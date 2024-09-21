package org.fiume.sketch.shared.jobs

import cats.effect.Temporal
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration

object PeriodicJob:
  private val logger = LoggerFactory.getLogger(PeriodicJob.getClass)

  def make[F[_]: Temporal, A](job: F[A], interval: FiniteDuration): fs2.Stream[F, A] =
    fs2.Stream
      .awakeEvery[F](interval)
      .evalMap { _ =>
        logger.info("Running job...")
        job
      }

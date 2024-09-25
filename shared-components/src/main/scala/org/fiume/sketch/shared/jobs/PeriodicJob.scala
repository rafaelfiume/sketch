package org.fiume.sketch.shared.jobs

import cats.effect.Temporal
import cats.effect.kernel.Sync
import cats.implicits.*
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration

object PeriodicJob:
  private val logger = LoggerFactory.getLogger(PeriodicJob.getClass)

  trait JobErrorHandler[F[_]]:
    val handleJobError: Throwable => F[Unit]

  def makeWithDefaultJobErrorHandler[F[_]: Sync: Temporal, A](
    interval: FiniteDuration,
    job: Job[F, A]
  ): fs2.Stream[F, A] = make(interval, job, makeJobErrorLogger())

  def make[F[_]: Temporal, A](
    period: FiniteDuration,
    job: Job[F, A],
    errorHandler: JobErrorHandler[F]
  ): fs2.Stream[F, A] =
    fs2.Stream
      .awakeEvery[F](period)
      .evalMap { _ =>
        logger.info(s"Running job: ${job.description}")
        job.run().attempt
      }
      .flatMap {
        case Right(a) => fs2.Stream.emit(a)
        case Left(e) =>
          fs2.Stream.exec {
            logger.warn("Job failed", e)
            errorHandler.handleJobError(e)
          }
      }

  private def makeJobErrorLogger[F[_]: Sync]() = new JobErrorHandler[F]:
    override val handleJobError: Throwable => F[Unit] = error =>
      Sync[F].delay { logger.warn(s"job failed with: ${error.getMessage()}") }

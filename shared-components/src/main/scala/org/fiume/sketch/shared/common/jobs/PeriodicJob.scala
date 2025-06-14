package org.fiume.sketch.shared.common.jobs

import cats.effect.{Sync, Temporal}
import cats.implicits.*
import org.fiume.sketch.shared.common.jobs.JobErrorHandler.Instances.JobErrorLogger
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration

object PeriodicJob:
  private val logger = LoggerFactory.getLogger(PeriodicJob.getClass)

  def makeWithDefaultJobErrorHandler[F[_]: {Sync, Temporal}, A](
    interval: FiniteDuration,
    job: Job[F, A]
  ): fs2.Stream[F, A] = make(interval, job, JobErrorLogger.make())

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
        case Left(e)  =>
          fs2.Stream.exec {
            logger.warn("Job failed", e)
            errorHandler.handleJobError(e)
          }
      }

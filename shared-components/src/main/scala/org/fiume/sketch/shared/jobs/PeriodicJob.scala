package org.fiume.sketch.shared.jobs

import cats.Applicative
import cats.effect.Temporal
import cats.implicits.*
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration

object PeriodicJob:
  private val logger = LoggerFactory.getLogger(PeriodicJob.getClass)

  trait JobErrorHandler[F[_], A]:
    /*
     * It must recover from error by returning an instance of `A` or simply `None`.
     */
    val handleJobError: Throwable => F[Option[A]]

  def makeWithDefaultJobErrorHandler[F[_]: Applicative: Temporal, A](
    interval: FiniteDuration,
    job: Job[F, A]
  ): fs2.Stream[F, Option[A]] = make(interval, job, makeJobErrorLogger())

  def make[F[_]: Temporal, A](
    period: FiniteDuration,
    job: Job[F, A],
    errorHandler: JobErrorHandler[F, A]
  ): fs2.Stream[F, Option[A]] =
    fs2.Stream
      .awakeEvery[F](period)
      .evalMap { _ =>
        logger.info(s"Running job: ${job.description}")
        job.run().attempt.flatMap {
          case Right(a) => a.some.pure[F]
          case Left(e) =>
            logger.warn("Job failed", e)
            errorHandler.handleJobError(e)
        }
      }

  private def makeJobErrorLogger[F[_]: Applicative, A]() = new JobErrorHandler[F, A]:
    override val handleJobError: Throwable => F[Option[A]] = error =>
      logger.warn(s"job failed with: ${error.getMessage()}")
      none[A].pure[F]

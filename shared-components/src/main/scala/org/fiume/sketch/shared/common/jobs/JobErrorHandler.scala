package org.fiume.sketch.shared.common.jobs

import cats.{Applicative, Apply}
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.shared.common.jobs.JobErrorHandler.Instances.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax.LoggerInterpolator

trait JobErrorHandler[F[_]]:
  val handleJobError: Throwable => F[Unit]

object JobErrorHandler:
  def make[F[_]](handler: Throwable => F[Unit]): JobErrorHandler[F] =
    new JobErrorHandler[F]:
      override val handleJobError = handler
  /*
   * Note: Do not rely on the order of execution of the handlers, as it is not guaranteed.
   */
  def combine[F[_]: Apply](fst: JobErrorHandler[F], snd: JobErrorHandler[F]): JobErrorHandler[F] =
    JobErrorHandler.make[F] { error =>
      fst.handleJobError(error) *> snd.handleJobError(error)
    }

  def combineAll[F[_]: Applicative](handlers: List[JobErrorHandler[F]]): JobErrorHandler[F] =
    handlers.foldLeft(NoOpJobErrorLogger.make[F]())(combine)

  object Instances:
    object NoOpJobErrorLogger:
      def make[F[_]: Applicative]() = JobErrorHandler.make[F](_ => Applicative[F].unit)

    object JobErrorLogger:
      def make[F[_]: Sync]() = JobErrorHandler.make[F] { error =>
        given Logger[F] = Slf4jLogger.getLogger[F]
        warn"job failed with: ${error.getMessage()}"
      }

object syntax:
  object JobErrorHandlerSyntax:
    extension [F[_]: Applicative](thisHandler: JobErrorHandler[F])
      def combine(other: JobErrorHandler[F]): JobErrorHandler[F] = JobErrorHandler.combine(thisHandler, other)
      def combineAll(others: List[JobErrorHandler[F]]): JobErrorHandler[F] = JobErrorHandler.combineAll(thisHandler :: others)

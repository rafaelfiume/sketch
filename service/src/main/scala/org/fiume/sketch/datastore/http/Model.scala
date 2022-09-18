package org.fiume.sketch.datastore.http

import cats.data.{EitherNec, NonEmptyChain}
import cats.implicits.*

object Model:

  case class Incorrect(details: NonEmptyChain[Incorrect.Missing])

  object Incorrect:
    case class Missing(field: String) extends AnyVal

  object IncorrectOps:
    extension [A](field: Option[A])
      def orMissing(name: String): EitherNec[Incorrect.Missing, A] =
        field.toRight(Incorrect.Missing(name)).toEitherNec

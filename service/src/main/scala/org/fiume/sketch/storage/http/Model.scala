package org.fiume.sketch.datastore.http

import cats.data.{EitherNec, NonEmptyChain}
import cats.implicits.*

object Model:

  case class Incorrect(details: NonEmptyChain[Incorrect.Detail])

  object Incorrect:
    sealed trait Detail
    case class Missing(field: String) extends Detail
    case class Malformed(description: String) extends Detail

  object IncorrectOps:
    extension [A](field: Option[A])
      def orMissing(name: String): EitherNec[Incorrect.Detail, A] =
        field.toRight(Incorrect.Missing(name)).toEitherNec

    extension (field: String)
      def missing: NonEmptyChain[Incorrect.Detail] =
        NonEmptyChain.one(Incorrect.Missing(field))

    extension (description: String)
      def malformed: NonEmptyChain[Incorrect.Detail] =
        NonEmptyChain.one(Incorrect.Malformed(description))

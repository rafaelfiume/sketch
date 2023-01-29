package org.fiume.sketch

import cats.Parallel
import cats.data.{EitherNec, EitherT, NonEmptyChain}
import cats.effect.{IO, IOApp}
import cats.implicits.*

object PlayIt extends IOApp.Simple:

  given Parallel[EitherT[IO, NonEmptyChain[String], *]] = EitherT.accumulatingParallel
  override def run: IO[Unit] =
    for
      _ <- (validateInt, validateString).parTupled match
        case Left(error) => IO { println(error) }
        case Right(_)    => ???

      validated <- (EitherT(IO.pure(validateInt)), EitherT(IO.pure(validateString))).parTupled.value
      _ <- validated match
        case Left(error) => IO { println(error) }
        case Right(_)    => ???
    yield ()

  def validateInt: EitherNec[String, Int] = Either.left(NonEmptyChain.one("invalid int"))
  def validateString: EitherNec[String, String] = Either.left(NonEmptyChain.one("invalid string"))

package org.fiume.sketch.support.gens

import cats.Applicative
import cats.data.NonEmptyList
import org.fiume.sketch.app.Version
import org.scalacheck.{Arbitrary, Gen}

import java.time.{ZoneId, ZoneOffset, ZonedDateTime}
import java.time.temporal.ChronoUnit.MILLIS

object Gens:

  given Applicative[Gen] = new Applicative[Gen]:
    def pure[A](a: A): Gen[A] = Gen.const(a)
    def ap[A, B](ff: Gen[A => B])(fa: Gen[A]): Gen[B] = ff.flatMap(f => fa.map(f))

  def appVersions: Gen[Version] =
    val prefix = "0.1"
    val snapshot: Gen[Version] = Gen.const(Version(s"$prefix-SNAPSHOT"))
    val buildVersion: Gen[Version] = for
      currentMillis <- dateAndTime.map(_.toInstant.toEpochMilli)
      buildA <- Gen.buildableOfN[String, Char](7, Gen.alphaNumChar)
      buildNumber <- Gen.buildableOfN[String, Char](2, Gen.numChar)
    yield Version(s"$prefix.$currentMillis.$buildA.$buildNumber")
    Gen.oneOf(snapshot, buildVersion)

  def dateAndTime: Gen[ZonedDateTime] =
    for
      year <- Gen.choose(2022, 2026)
      month <- Gen.choose(1, 12)
      day <- if month == 2 then Gen.choose(1, 28) else Gen.choose(1, 30) // too simplistic: improve it
      hour <- Gen.choose(0, 23)
      minute <- Gen.choose(0, 59)
      second <- Gen.choose(0, 59)
      nanoOfSecond = 0
    yield ZonedDateTime.of(year, month, day, hour, minute, second, nanoOfSecond, ZoneOffset.UTC)

  object Strings:
    def alphaNumString(min: Int, max: Int): Gen[String] =
      for
        size <- Gen.chooseNum(min, max)
        list <- Gen.listOfN(size, Gen.alphaNumChar)
      yield list.mkString

    def alphaNumStringFixedSize(size: Int): Gen[String] = alphaNumString(size, size)

    def alphaNumString(max: Int): Gen[String] = Gen.resize(max, alphaNumString)

    def alphaNumString: Gen[String] =
      Gen.nonEmptyListOf(Gen.alphaNumChar).retryUntil(_.nonEmpty, maxTries = 100000).map(_.mkString)

  object Booleans:
    def booleans: Gen[Boolean] = Arbitrary.arbitrary[Boolean]

  object Bytes:
    def bytes: Gen[Byte] = Arbitrary.arbitrary[Byte]

  object Lists:
    def nnonEmptyListOf[T](gen: Gen[T]): Gen[NonEmptyList[T]] =
      for
        head <- gen
        tail <- Gen.listOf(gen)
      yield NonEmptyList.ofInitLast(tail, head)

    def smallListOf[T](gen: Gen[T]): Gen[List[T]] =
      for
        n <- Gen.choose(2, 5)
        list <- Gen.containerOfN[Set, T](n, gen).map(_.toList)
      yield list

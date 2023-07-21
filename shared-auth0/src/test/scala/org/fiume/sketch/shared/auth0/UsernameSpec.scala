package org.fiume.sketch.shared.auth0

import munit.ScalaCheckSuite
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.shared.auth0.test.UserGens.*
import org.fiume.sketch.shared.auth0.test.UserGens.given
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.scalacheck.{Arbitrary, Gen, ShrinkLowPriority}
import org.scalacheck.Prop.forAll

import scala.util.Random

class UsernameSpec extends ScalaCheckSuite with ShrinkLowPriority:

  test("valid usernames"):
    forAll { (username: Username) =>
      Username.validated(username.value).rightValue == username
    }

  test("short usernames"):
    given Arbitrary[String] = Arbitrary(
      Gen.choose(0, Username.minLength - 1).flatMap { usernamesWithSize(_) }.map(_.value)
    )
    forAll { (shortUsername: String) =>
      Username.validated(shortUsername).leftValue.contains(Username.TooShort(shortUsername.length))
    }

  test("long usernames"):
    given Arbitrary[String] = Arbitrary(
      Gen
        .choose(Username.maxLength, 100)
        .flatMap { usernamesWithSize(_) }
        .suchThat(_.value.length > Username.maxLength)
        .map(_.value)
    )
    forAll { (longUsername: String) =>
      Username.validated(longUsername).leftValue.contains(Username.TooLong(longUsername.length))
    }

  test("usernames with invalid characters"):
    given Arbitrary[String] = Arbitrary(
      for
        usernames <- usernames
        invalidChars <- Gen
          .nonEmptyListOf(
            Gen.oneOf(" ", "\t", "\n", "\r", "\f", "*", "(", ")", "[", "]", "{", "}", "|", "\\", "'", "\"", "<", ">", "/")
          )
          .map(_.mkString)
        invalidUsername = Random.shuffle(usernames.value ++ invalidChars).mkString
      yield invalidUsername
    )
    forAll { (usernameWithInvalidChars: String) =>
      Username.validated(usernameWithInvalidChars).leftValue.contains(Username.InvalidChar)
    }

  test("usernames with reserved words"):
    given Arbitrary[String] = Arbitrary(
      for
        usernames <- usernames
        reservedWord <- Gen.oneOf(Username.reservedWords)
        reservedUsername = usernames.value ++ reservedWord.mkString
      yield reservedUsername
    )
    forAll { (usernameWithReservedWords: String) =>
      Username.validated(usernameWithReservedWords).leftValue.contains(Username.ReservedWords)
    }

  test("usernames with excessive repeated characters"):
    given Arbitrary[String] = Arbitrary(
      for
        atMostSize <- Gen.oneOf(Username.minLength, Username.maxLength)
        username <- usernamesWithSize(atMostSize).map(_.value)
        repeatedChar <- usernameChars
        repeatedCharLength = math.ceil(username.length * Username.maxRepeatedCharsPercentage).toInt
        repeatedCharString = repeatedChar.toString * repeatedCharLength
        repeatedUsername = username.dropRight(repeatedCharLength) ++ repeatedCharString
      yield repeatedUsername
    )
    forAll { (usernameWithExcessiveRepeatedChars: String) =>
      Username.validated(usernameWithExcessiveRepeatedChars).leftValue.contains(Username.ExcessiveRepeatedChars)
    }

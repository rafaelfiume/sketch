package org.fiume.sketch.shared.auth0

import munit.ScalaCheckSuite
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.shared.auth0.test.UserGens.Usernames.*
import org.fiume.sketch.shared.auth0.test.UserGens.Usernames.given
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.scalacheck.{Gen, ShrinkLowPriority}
import org.scalacheck.Prop.forAll

import scala.util.Random

class UsernameSpec extends ScalaCheckSuite with ShrinkLowPriority:

  test("valid usernames"):
    forAll { (username: Username) =>
      Username.validated(username.value).rightValue == username
    }

  test("short usernames"):
    forAll(shortUsernames) { shortUsername =>
      Username.validated(shortUsername).leftValue.contains(Username.TooShort)
    }

  test("long usernames"):
    forAll(longUsernames) { longUsername =>
      Username.validated(longUsername).leftValue.contains(Username.TooLong)
    }

  test("usernames with invalid characters"):
    forAll(usernamesWithInvalidChars) { usernameWithInvalidChars =>
      Username.validated(usernameWithInvalidChars).leftValue.contains(Username.InvalidCharater)
    }

  test("usernames with reserved words"):
    forAll(usernamesWithReservedWords) { usernameWithReservedWords =>
      Username.validated(usernameWithReservedWords).leftValue.contains(Username.ReservedWords)
    }

  test("usernames with excessive repeated characters"):
    forAll(usernamesWithRepeatedChars) { usernameWithExcessiveRepeatedChars =>
      Username.validated(usernameWithExcessiveRepeatedChars).leftValue.contains(Username.ExcessiveRepeatedChars)
    }

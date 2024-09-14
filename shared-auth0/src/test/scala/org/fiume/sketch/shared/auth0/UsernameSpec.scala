package org.fiume.sketch.shared.auth0

import munit.ScalaCheckSuite
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.shared.auth0.User.Username.WeakUsernameError
import org.fiume.sketch.shared.auth0.testkit.UserGens.*
import org.fiume.sketch.shared.auth0.testkit.UserGens.given
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.scalacheck.Prop.forAll
import org.scalacheck.ShrinkLowPriority

class UsernameSpec extends ScalaCheckSuite with ShrinkLowPriority:

  test("accepts valid username"):
    forAll { (username: Username) =>
      Username.validated(username.value).rightOrFail == username
    }

  test("rejects short username"):
    forAll(shortUsernames) { shortUsername =>
      Username.validated(shortUsername).leftOfFail.contains(WeakUsernameError.TooShort)
    }

  test("rejects long username"):
    forAll(longUsernames) { longUsername =>
      Username.validated(longUsername).leftOfFail.contains(WeakUsernameError.TooLong)
    }

  test("rejects username with invalid characters"):
    forAll(usernamesWithInvalidChars) { usernameWithInvalidChars =>
      Username.validated(usernameWithInvalidChars).leftOfFail.contains(WeakUsernameError.InvalidChar)
    }

  test("rejects username with reserved words"):
    forAll(usernamesWithReservedWords) { usernameWithReservedWords =>
      Username.validated(usernameWithReservedWords).leftOfFail.contains(WeakUsernameError.ReservedWords)
    }

  test("rejects username with excessive repeated characters"):
    forAll(usernamesWithRepeatedChars) { usernameWithExcessiveRepeatedChars =>
      Username.validated(usernameWithExcessiveRepeatedChars).leftOfFail.contains(WeakUsernameError.ExcessiveRepeatedChars)
    }

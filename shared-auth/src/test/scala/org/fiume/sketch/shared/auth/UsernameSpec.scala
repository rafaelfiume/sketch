package org.fiume.sketch.shared.auth

import munit.ScalaCheckSuite
import org.fiume.sketch.shared.auth.User.Username
import org.fiume.sketch.shared.auth.User.Username.WeakUsernameError
import org.fiume.sketch.shared.auth.testkit.UserGens.*
import org.fiume.sketch.shared.auth.testkit.UserGens.given
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
      Username.validated(shortUsername).leftOrFail.contains(WeakUsernameError.TooShort)
    }

  test("rejects long username"):
    forAll(longUsernames) { longUsername =>
      Username.validated(longUsername).leftOrFail.contains(WeakUsernameError.TooLong)
    }

  test("rejects username with invalid characters"):
    forAll(usernamesWithInvalidChars) { usernameWithInvalidChars =>
      Username.validated(usernameWithInvalidChars).leftOrFail.contains(WeakUsernameError.InvalidChar)
    }

  test("rejects username with reserved words"):
    forAll(usernamesWithReservedWords) { usernameWithReservedWords =>
      Username.validated(usernameWithReservedWords).leftOrFail.contains(WeakUsernameError.ReservedWords)
    }

  test("rejects username with excessive repeated characters"):
    forAll(usernamesWithRepeatedChars) { usernameWithExcessiveRepeatedChars =>
      Username.validated(usernameWithExcessiveRepeatedChars).leftOrFail.contains(WeakUsernameError.ExcessiveRepeatedChars)
    }

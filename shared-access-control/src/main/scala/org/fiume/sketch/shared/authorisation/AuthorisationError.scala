package org.fiume.sketch.shared.authorisation

import cats.implicits.*

enum AuthorisationError:
  case UnauthorisedError

object syntax:
  object AuthorisationErrorSyntax:
    extension [E, R](result: Either[E, R]) def widenErrorType = result.leftMap[AuthorisationError | E](identity)

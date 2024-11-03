package org.fiume.sketch.shared.authorisation

import cats.implicits.*

case object AccessDenied

object syntax:
  object AccessDeniedSyntax:
    extension [E, R](result: Either[E, R]) def widenErrorType = result.leftMap[AccessDenied.type | E](identity)

package org.fiume.sketch.shared.auth0.domain

import scala.util.control.NoStackTrace

enum AuthenticationError(val details: String) extends Throwable with NoStackTrace:
  case UserNotFoundError extends AuthenticationError("User not found")
  case InvalidPasswordError extends AuthenticationError("Invalid password")
  case AccountNotActiveError extends AuthenticationError("Account is not active")

package org.fiume.sketch.shared.auth.domain

enum AuthenticationError(val details: String):
  case UserNotFoundError extends AuthenticationError("User not found")
  case InvalidPasswordError extends AuthenticationError("Invalid password")
  case AccountNotActiveError extends AuthenticationError("Account is not active")

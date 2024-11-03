package org.fiume.sketch.shared.auth.domain

/*
 * These errors occur specifically during the login path of authentication.
 * For the jwt verification phase of authentication, see JwtError
 * TODO Should AuthenticationError be renamed?
 */
enum AuthenticationError(val details: String):
  case UserNotFoundError extends AuthenticationError("User not found")
  case InvalidPasswordError extends AuthenticationError("Invalid password")
  case AccountNotActiveError extends AuthenticationError("Account is not active")

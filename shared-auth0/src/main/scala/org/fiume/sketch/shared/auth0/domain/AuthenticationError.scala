package org.fiume.sketch.shared.auth0.domain

import scala.util.control.NoStackTrace

sealed trait AuthenticationError extends Throwable with NoStackTrace:
  def details: String

object AuthenticationError:
  case object UserNotFoundError extends AuthenticationError:
    override def details: String = "User not found"

  case object InvalidPasswordError extends AuthenticationError:
    override def details: String = "Invalid password"

  case object AccountNotActiveError extends AuthenticationError:
    override def details: String = "Account is not active"

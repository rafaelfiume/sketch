package org.fiume.sketch.shared.auth0.domain

import scala.util.control.NoStackTrace

sealed trait AuthenticationError extends Throwable with NoStackTrace:
  def details: String

object AuthenticationError:
  case object UserNotFoundError extends AuthenticationError:
    override def details: String = "User not found"

  case object InvalidPasswordError extends AuthenticationError:
    override def details: String = "Invalid password"

  case class AccountNotActiveError private (state: AccountState) extends AuthenticationError:
    override def details: String = s"Account is not active: $state"

  object AccountNotActiveError:
    def make(state: AccountState): AccountNotActiveError =
      // checking invariant first
      if state.isInstanceOf[AccountState.Active] then throw new IllegalArgumentException("Account is active")
      else AccountNotActiveError(state)

// TODO Rethink namespace since this is an auth/access-control combo
package org.fiume.sketch.shared.auth.http.model

import org.fiume.sketch.shared.auth.domain.{ActivateAccountError, SoftDeleteAccountError}
import org.fiume.sketch.shared.auth.domain.ActivateAccountError.*
import org.fiume.sketch.shared.auth.domain.SoftDeleteAccountError.*
import org.fiume.sketch.shared.authorisation.AuthorisationError
import org.fiume.sketch.shared.authorisation.AuthorisationError.*
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.common.troubleshooting.syntax.ErrorInfoSyntax.*

object AccountStateTransitionErrorSyntax:

  extension (error: SoftDeleteAccountError | AuthorisationError)
    def toErrorInfo =
      val (errorCode, errorMessage) = error match
        case AccountAlreadyPendingDeletion          => "1200".code -> "Account already marked for deletion".message
        case SoftDeleteAccountError.AccountNotFound => "1201".code -> "Account not found".message
        case UnauthorisedError                      => "3000".code -> "Unauthorised operation".message
      ErrorInfo.make(errorCode, errorMessage)

  extension (error: ActivateAccountError | AuthorisationError)
    def toActivateErrorInfo =
      val (errorCode, errorMessage) = error match
        case AccountAlreadyActive                 => "1210".code -> "Account is already active".message
        case ActivateAccountError.AccountNotFound => "1211".code -> "Account not found".message
        case UnauthorisedError                    => "3000".code -> "Unauthorised operation".message
      ErrorInfo.make(errorCode, errorMessage)

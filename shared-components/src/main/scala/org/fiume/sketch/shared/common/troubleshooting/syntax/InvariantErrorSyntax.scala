package org.fiume.sketch.shared.common.troubleshooting.syntax

import cats.data.NonEmptyChain
import cats.implicits.*
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.ErrorDetails
import org.fiume.sketch.shared.common.troubleshooting.InvariantError

object InvariantErrorSyntax:
  extension [T <: InvariantError](
    inputError: T
  ) def asDetails: ErrorDetails = ErrorDetails(inputError.key -> inputError.detail) // Yolo

  extension (inputErrors: NonEmptyChain[InvariantError])
    def asDetails: ErrorDetails = ErrorDetails(inputErrors.map(e => e.key -> e.detail).toList*)

package org.fiume.sketch.shared.common.troubleshooting.syntax

import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.{ErrorCode, ErrorDetails, ErrorMessage}

object ErrorInfoSyntax:
  extension (s: String)
    def code = ErrorCode(s)
    def message = ErrorMessage(s)

  extension (details: (String, String)) def details = ErrorDetails(details)

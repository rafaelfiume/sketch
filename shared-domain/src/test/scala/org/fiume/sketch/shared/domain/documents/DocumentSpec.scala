package org.fiume.sketch.shared.domain.documents

import munit.ScalaCheckSuite
import org.fiume.sketch.shared.domain.documents.Document.Metadata.*
import org.fiume.sketch.shared.domain.documents.Document.Metadata.Name.InvalidDocumentNameError
import org.fiume.sketch.shared.domain.documents.Document.Metadata.Name.InvalidDocumentNameError.*
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.*
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.given
import org.fiume.sketch.shared.testkit.Syntax.EitherSyntax.*
import org.scalacheck.Prop.forAll
import org.scalacheck.ShrinkLowPriority

class DocumentSpec extends ScalaCheckSuite with ShrinkLowPriority:

  test("accepts valid document name"):
    forAll { (name: Name) =>
      Name.validated(name.value).rightValue == name
    }

  test("rejects short document names"):
    forAll(shortNames) { shortName =>
      Name.validated(shortName).leftValue.contains(InvalidDocumentNameError.TooShort)
    }

  test("rejects long document name"):
    forAll(longNames) { longName =>
      Name.validated(longName).leftValue.contains(InvalidDocumentNameError.TooLong)
    }

  test("rejects document name with invalid characters"):
    forAll(namesWithInvalidChars) { nameWithInvalidChars =>
      Name.validated(nameWithInvalidChars).leftValue.contains(InvalidDocumentNameError.InvalidChar)
    }

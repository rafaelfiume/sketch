package org.fiume.sketch.storage.documents

import munit.ScalaCheckSuite
import org.fiume.sketch.shared.testkit.EitherSyntax.*
import org.fiume.sketch.storage.documents.Document.Metadata.*
import org.fiume.sketch.storage.documents.Document.Metadata.Name.InvalidDocumentNameError
import org.fiume.sketch.storage.documents.Document.Metadata.Name.InvalidDocumentNameError.*
import org.fiume.sketch.storage.testkit.DocumentsGens
import org.fiume.sketch.storage.testkit.DocumentsGens.*
import org.fiume.sketch.storage.testkit.DocumentsGens.given
import org.scalacheck.Prop.forAll
import org.scalacheck.ShrinkLowPriority

class DocumentSpec extends ScalaCheckSuite with ShrinkLowPriority:

  test("valid document names"):
    forAll { (name: Name) =>
      Name.validated(name.value).rightValue == name
    }

  test("short document names"):
    forAll(shortNames) { shortName =>
      Name.validated(shortName).leftValue.contains(InvalidDocumentNameError.TooShort)
    }

  test("long document name"):
    forAll(longNames) { longName =>
      Name.validated(longName).leftValue.contains(InvalidDocumentNameError.TooLong)
    }

  test("document name with invalid characters"):
    forAll(namesWithInvalidChars) { nameWithInvalidChars =>
      Name.validated(nameWithInvalidChars).leftValue.contains(InvalidDocumentNameError.InvalidChar)
    }

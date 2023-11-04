package org.fiume.sketch.storage.documents

import cats.Eq
import cats.data.{EitherNec, Validated}
import cats.implicits.*
import fs2.Stream
import org.fiume.sketch.shared.app.WithUuid
import org.fiume.sketch.shared.app.troubleshooting.{InvariantError, InvariantHolder}
import org.fiume.sketch.storage.documents.Document.Metadata
import org.fiume.sketch.storage.documents.Document.Metadata.*
import org.fiume.sketch.storage.documents.Document.Metadata.Name.InvalidDocumentNameError
import org.fiume.sketch.storage.documents.Document.Metadata.Name.InvalidDocumentNameError.*

import java.util.UUID

type DocumentWithUuid[F[_]] = Document[F] & WithUuid[DocumentUuid]

object DocumentUuid:
  // TODO Possible to generalise it?
  given Eq[DocumentUuid] = Eq.fromUniversalEquals[DocumentUuid]

case class DocumentUuid(uuid: UUID) extends AnyVal

case class Document[F[_]](
  metadata: Metadata,
  content: Stream[F, Byte]
)

object Document:
  def withUuid[F[_]](
    withUuid: DocumentUuid,
    metadata: Metadata,
    content: Stream[F, Byte]
  ): Document[F] & WithUuid[DocumentUuid] =
    new Document[F](metadata, content) with WithUuid[DocumentUuid]:
      override val uuid: DocumentUuid = withUuid

  extension [F[_]](document: Document[F])
    def withUuid(withUuid: DocumentUuid): Document[F] & WithUuid[DocumentUuid] =
      Document.withUuid[F](withUuid, document.metadata, document.content)

  case class Metadata(name: Name, description: Description)

  object Metadata:
    sealed abstract case class Name(value: String)

    object Name extends InvariantHolder[InvalidDocumentNameError]:
      sealed trait InvalidDocumentNameError extends InvariantError

      object InvalidDocumentNameError:
        case object TooShort extends InvalidDocumentNameError:
          override def uniqueCode: String = "document.name.too.short"
          override val message: String = s"must be at least $minLength characters long"

        case object TooLong extends InvalidDocumentNameError:
          override def uniqueCode: String = "document.name.too.long"
          override val message: String = s"must be at most $maxLength characters long"

        case object InvalidChar extends InvalidDocumentNameError:
          override def uniqueCode: String = "document.name.invalid"
          override val message: String =
            "must only contain letters (a-z,A-Z), numbers (0-9), whitespace ( ), underscores (_) hyphens (-) and periods (.)"

      val minLength = 4
      val maxLength = 64
      override val invariantErrors = Set(TooShort, TooLong, InvalidChar)

      def validated(value: String): EitherNec[InvalidDocumentNameError, Name] =
        val hasMinLength = Validated.condNec[InvalidDocumentNameError, Unit](value.length >= minLength, (), TooShort)
        val hasMaxLength = Validated.condNec(value.length <= maxLength, (), TooLong)
        val hasNoInvalidChar = Validated.condNec(value.matches("^[a-zA-Z0-9_\\-. ]*$"), (), InvalidChar)
        (hasMinLength, hasMaxLength, hasNoInvalidChar)
          .mapN((_, _, _) => new Name(value) {})
          .toEither

      def notValidatedFromString(value: String): Name = new Name(value) {}

      given Eq[Name] = Eq.fromUniversalEquals
      given Eq[InvalidDocumentNameError] = Eq.fromUniversalEquals[InvalidDocumentNameError]

    case class Description(value: String) extends AnyVal

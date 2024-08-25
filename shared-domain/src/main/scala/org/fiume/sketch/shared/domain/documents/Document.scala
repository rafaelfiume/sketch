package org.fiume.sketch.shared.domain.documents

import cats.Eq
import cats.data.{EitherNec, Validated}
import cats.implicits.*
import org.fiume.sketch.shared.app.{Entity, EntityId, WithUuid}
import org.fiume.sketch.shared.app.troubleshooting.InvariantError
import org.fiume.sketch.shared.auth0.UserId
import org.fiume.sketch.shared.domain.documents.Document.Metadata
import org.fiume.sketch.shared.domain.documents.Document.Metadata.*
import org.fiume.sketch.shared.domain.documents.Document.Metadata.Name.InvalidDocumentNameError
import org.fiume.sketch.shared.domain.documents.Document.Metadata.Name.InvalidDocumentNameError.*

import java.util.UUID

type DocumentId = EntityId[DocumentEntity]
object DocumentId:
  def apply(uuid: UUID): DocumentId = EntityId[DocumentEntity](uuid)
sealed trait DocumentEntity extends Entity

case class Document(metadata: Metadata)

type DocumentWithId = Document & WithUuid[DocumentId]

trait WithStream[F[_]]:
  val stream: fs2.Stream[F, Byte]

type DocumentWithStream[F[_]] = Document & WithStream[F]

type DocumentWithIdAndStream[F[_]] = Document & WithUuid[DocumentId] & WithStream[F]

object Document:
  def withUuid(uuid0: DocumentId, metadata: Metadata): Document & WithUuid[DocumentId] =
    new Document(metadata) with WithUuid[DocumentId]:
      override val uuid: DocumentId = uuid0

  def withStream[F[_]](stream0: fs2.Stream[F, Byte], metadata: Metadata): Document & WithStream[F] =
    new Document(metadata) with WithStream[F]:
      override val stream: fs2.Stream[F, Byte] = stream0

  case class Metadata(name: Name, description: Description, owner: UserId)

  object Metadata:
    sealed abstract case class Name(value: String)

    object Name:
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

  extension (document: Document)
    def withUuid(uuid: DocumentId): Document & WithUuid[DocumentId] = Document.withUuid(uuid, document.metadata)

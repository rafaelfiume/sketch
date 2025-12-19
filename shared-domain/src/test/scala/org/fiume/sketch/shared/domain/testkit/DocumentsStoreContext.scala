package org.fiume.sketch.shared.domain.testkit

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits.*
import fs2.Stream
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.common.WithUuid
import org.fiume.sketch.shared.domain.documents.{Document, DocumentId, DocumentWithId, DocumentWithIdAndStream, WithStream}
import org.fiume.sketch.shared.domain.documents.algebras.DocumentsStore
import org.fiume.sketch.shared.testkit.TxRef

trait DocumentsStoreContext:
  type DocsState = Map[DocumentId, DocumentWithIdAndStream[IO]]

  def makeDocumentsStore(): IO[(DocumentsStore[IO], TxRef[DocsState])] =
    makeDocumentsStore(state = Map.empty)

  def makeDocumentsStore(state: DocumentWithIdAndStream[IO]*): IO[(DocumentsStore[IO], TxRef[DocsState])] =
    makeDocumentsStore(state.map(doc => doc.uuid -> doc).toMap)

  private def makeDocumentsStore(state: DocsState): IO[(DocumentsStore[IO], TxRef[DocsState])] =
    for txRef <- TxRef.of(state)
    yield (
      new DocumentsStore[IO]:
        override def store(bytes0: Array[Byte], document: Document): IO[DocumentId] =
          import scala.language.adhocExtensions
          IO.randomUUID.map(DocumentId(_)).flatMap { uuid =>
            txRef
              .update {
                val doc = new Document(document.metadata) with WithUuid[DocumentId] with WithStream[IO]:
                  override val uuid = uuid
                  override val bytes = bytes0
                _.updated(uuid, doc)
              }
              .as(uuid)
          }

        override def fetchDocument(uuid: DocumentId): IO[Option[DocumentWithId]] =
          txRef.get.map(_.collectFirst {
            case (storedUuid, document) if storedUuid === uuid => document
          })

        override def documentStream(uuid: DocumentId): fs2.Stream[IO, Byte] = ////
          fetchAll().find { _.uuid === uuid }.flatMap(doc => fs2.Stream.emits(doc.bytes))

        override def fetchDocuments(uuids: fs2.Stream[IO, DocumentId]): fs2.Stream[IO, DocumentWithId] =
          uuids.flatMap { uuid => fetchAll().find(_.uuid === uuid) }

        override def fetchDocumentsByOwnerId(ownerId: UserId): fs2.Stream[IO, DocumentWithId] =
          fetchAll().find(_.metadata.ownerId === ownerId)

        override def delete(uuid: DocumentId): IO[Option[DocumentId]] = txRef.modify { state =>
          if state.contains(uuid) then state.removed(uuid) -> uuid.some
          else state -> none
        }

        given IORuntime = IORuntime.global
        private def fetchAll(): Stream[IO, DocumentWithIdAndStream[IO]] = fs2.Stream.emits(
          txRef.get.unsafeRunSync().values.toSeq
        )
    ) -> txRef

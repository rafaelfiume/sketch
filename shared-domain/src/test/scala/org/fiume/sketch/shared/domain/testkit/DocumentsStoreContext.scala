package org.fiume.sketch.shared.domain.testkit

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits.*
import fs2.Stream
import org.fiume.sketch.shared.app.WithUuid
import org.fiume.sketch.shared.domain.documents.{
  Document,
  DocumentId,
  DocumentWithId,
  DocumentWithIdAndStream,
  DocumentWithStream,
  WithStream
}
import org.fiume.sketch.shared.domain.documents.algebras.DocumentsStore

trait DocumentsStoreContext:
  def makeDocumentsStore(): IO[DocumentsStore[IO, IO]] = makeDocumentsStore(state = Map.empty)

  def makeDocumentsStore(state: DocumentWithIdAndStream[IO]*): IO[DocumentsStore[IO, IO]] =
    makeDocumentsStore(state.map(doc => doc.uuid -> doc).toMap)

  private def makeDocumentsStore(state: Map[DocumentId, DocumentWithIdAndStream[IO]]): IO[DocumentsStore[IO, IO]] =
    IO.ref(state).map { storage =>
      new DocumentsStore[IO, IO]:
        override def store(document: DocumentWithStream[IO]): IO[DocumentId] =
          import scala.language.adhocExtensions
          IO.randomUUID.map(DocumentId(_)).flatMap { uuid =>
            storage
              .update {
                val doc = new Document(document.metadata) with WithUuid[DocumentId] with WithStream[IO]:
                  val uuid = uuid
                  val stream = document.stream
                _.updated(uuid, doc)
              }
              .as(uuid)
          }

        override def fetchDocument(uuid: DocumentId): IO[Option[DocumentWithId]] =
          storage.get.map(_.collectFirst {
            case (storedUuid, document) if storedUuid === uuid => document
          })

        override def documentStream(uuid: DocumentId): fs2.Stream[IO, Byte] =
          fetchAll().find { _.uuid === uuid }.flatMap(_.stream)

        override def fetchDocuments(uuids: fs2.Stream[IO, DocumentId]): fs2.Stream[IO, DocumentWithId] =
          uuids.flatMap { uuid => fetchAll().find(_.uuid === uuid) }

        override def delete(uuid: DocumentId): IO[Unit] = storage.update { _.removed(uuid) }

        override val lift: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        override val commit: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        override val commitStream: [A] => fs2.Stream[IO, A] => fs2.Stream[IO, A] = [A] => (action: fs2.Stream[IO, A]) => action

        given IORuntime = IORuntime.global
        private def fetchAll(): Stream[IO, DocumentWithIdAndStream[IO]] = fs2.Stream.emits(
          storage.get.unsafeRunSync().values.toSeq
        )
    }

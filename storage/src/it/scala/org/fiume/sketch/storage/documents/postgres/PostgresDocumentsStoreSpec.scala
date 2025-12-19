package org.fiume.sketch.storage.documents.postgres

import cats.effect.*
import cats.implicits.*
import doobie.{ConnectionIO, *}
import doobie.implicits.*
import doobie.postgres.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.domain.documents.{DocumentId, DocumentWithIdAndStream, DocumentWithStream}
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.given
import org.fiume.sketch.shared.domain.testkit.syntax.DocumentSyntax.*
import org.fiume.sketch.shared.testkit.FileContentContext
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.fiume.sketch.storage.documents.postgres.DatabaseCodecs.given
import org.fiume.sketch.storage.postgres.PostgresTransactionManager
import org.fiume.sketch.storage.testkit.DockerPostgresSuite
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant

class PostgresDocumentsStoreSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with DockerPostgresSuite
    with FileContentContext
    with PostgresStoreSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(5)

  test("fetches metadata of stored document"):
    forAllF { (doc: DocumentWithIdAndStream[IO]) =>
      will(cleanStorage) {
        (
          PostgresDocumentsStore.make[IO](),
          PostgresTransactionManager.make[IO](transactor())
        ).tupled.use { case (store, tx) =>
          for
            uuid <- tx.commit { store.store(doc.bytes, doc) }

            result <- tx.commit { store.fetchDocument(uuid) }
//
          yield assertEquals(result, doc.some)
        }
      }
    }

  test("fetches content bytes of stored document"):
    forAllF { (doc: DocumentWithStream[IO]) =>
      will(cleanStorage) {
        (
          PostgresDocumentsStore.make[IO](),
          PostgresTransactionManager.make[IO](transactor())
        ).tupled.use { case (store, tx) =>
          for
            uuid <- tx.commit { store.store(doc.bytes, doc) }

            result <- tx.commitStream { store.documentStream(uuid) }.compile.toList

            bytes = doc.bytes.toList
          yield assertEquals(result, bytes)
        }
      }
    }

  test("fetches multiple documents"):
    forAllF { (fstDoc: DocumentWithStream[IO], sndDoc: DocumentWithStream[IO]) =>
      will(cleanStorage) {
        (
          PostgresDocumentsStore.make[IO](),
          PostgresTransactionManager.make[IO](transactor())
        ).tupled.use { case (store, tx) =>
          for
            fstUuid <- tx.commit { store.store(fstDoc.bytes, fstDoc) }
            sndUuid <- tx.commit { store.store(sndDoc.bytes, sndDoc) }

            result <- tx
              .commitStream {
                fs2.Stream(fstUuid, sndUuid).through { store.fetchDocuments }
              }
              .compile
              .toList
//
          yield assertEquals(result, List(fstDoc.withUuid(fstUuid), sndDoc.withUuid(sndUuid)))
        }
      }
    }

  test("fetches documents by ownerId"):
    forAllF { (fstDoc: DocumentWithStream[IO], sndDoc: DocumentWithStream[IO]) =>
      will(cleanStorage) {
        (
          PostgresDocumentsStore.make[IO](),
          PostgresTransactionManager.make[IO](transactor())
        ).tupled.use { case (store, tx) =>
          for
            _ <- tx.commit {
              store.store(fstDoc.bytes, fstDoc) *>
                store.store(sndDoc.bytes, sndDoc)
            }

            result <- tx
              .commitStream {
                store.fetchDocumentsByOwnerId(sndDoc.metadata.ownerId)
              }
              .compile
              .toList
//
          yield assertEquals(result.map(_.metadata.ownerId), List(sndDoc.metadata.ownerId))
        }
      }
    }

  test("deletes stored document"):
    forAllF { (fstDoc: DocumentWithStream[IO], sndDoc: DocumentWithStream[IO]) =>
      will(cleanStorage) {
        (
          PostgresDocumentsStore.make[IO](),
          PostgresTransactionManager.make[IO](transactor())
        ).tupled.use { case (store, tx) =>
          for
            fstUuid <- tx.commit { store.store(fstDoc.bytes, fstDoc) }
            sndUuid <- tx.commit { store.store(sndDoc.bytes, sndDoc) }

            result <- tx.commit { store.delete(fstUuid) }

            fstDocResult <- tx.commit { store.fetchDocument(fstUuid) }
            sndDocResult <- tx.commit { store.fetchDocument(sndUuid) }
          yield
            assertEquals(result, fstUuid.some)
            assertEquals(fstDocResult, none)
            assertEquals(sndDocResult.someOrFail.uuid, sndUuid)
        }
      }
    }

  test("timestamps createdAt and updatedAt upon storage"):
    forAllF { (doc: DocumentWithStream[IO]) =>
      will(cleanStorage) {
        (
          PostgresDocumentsStore.make[IO](),
          PostgresTransactionManager.make[IO](transactor())
        ).tupled.use { case (store, tx) =>
          for
            uuid <- tx.commit { store.store(doc.bytes, doc) }

            createdAt <- tx.commit { fetchCreatedAt(uuid) }
            updatedAt <- tx.commit { fetchUpdatedAt(uuid) }
//
          yield assertEquals(createdAt, updatedAt)
        }
      }
    }

  // no support for updates yet.

  test("play it".ignore): // good to see it in action
    import org.fiume.sketch.shared.auth.UserId
    import java.util.UUID
    import org.fiume.sketch.shared.domain.documents.Document
    import org.fiume.sketch.shared.domain.documents.Document.Metadata.*
    val filename = "mountain-bike-liguria-ponent.jpg"
    IO { bytesFrom[IO](filename) }.flatMap { content =>
      will(cleanStorage) {
        (
          PostgresDocumentsStore.make[IO](),
          PostgresTransactionManager.make[IO](transactor())
        ).tupled.use { case (store, tx) =>
          val doc = Document(
            Document.Metadata(Name.makeUnsafeFromString(filename), Description(""), UserId(UUID.randomUUID()))
          )
          for
            bytes <- content.compile.to(Array)
            uuid <- tx.commit { store.store(bytes, doc) }
            _ <- tx
              .commitStream { store.documentStream(uuid) }
              .through(fs2.io.file.Files[IO].writeAll(fs2.io.file.Path(filename)))
              .compile
              .drain
          yield ()
        }
      }
    }

trait PostgresStoreSpecContext:
  def cleanStorage: ConnectionIO[Unit] = sql"TRUNCATE TABLE domain.documents".update.run.void

  def fetchCreatedAt(uuid: DocumentId): ConnectionIO[Instant] =
    sql"SELECT created_at FROM domain.documents WHERE uuid = ${uuid}".query[Instant].unique
  def fetchUpdatedAt(uuid: DocumentId): ConnectionIO[Instant] =
    sql"SELECT updated_at FROM domain.documents WHERE uuid = ${uuid}".query[Instant].unique

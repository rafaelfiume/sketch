package org.fiume.sketch.storage.documents.postgres

import cats.effect.*
import cats.implicits.*
import doobie.{ConnectionIO, *}
import doobie.implicits.*
import doobie.postgres.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.domain.documents.{DocumentId, DocumentWithIdAndStream, DocumentWithStream}
import org.fiume.sketch.shared.domain.documents.algebras.DocumentsStore
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.*
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.given
import org.fiume.sketch.shared.domain.testkit.syntax.DocumentSyntax.*
import org.fiume.sketch.shared.testkit.FileContentContext
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.fiume.sketch.storage.documents.postgres.DatabaseCodecs.given
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
    forAllF { (document: DocumentWithIdAndStream[IO]) =>
      will(cleanStorage) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(document).ccommit

            result <- store.fetchDocument(uuid).ccommit
//
          yield assertEquals(result, document.some)
        }
      }
    }

  test("fetches content bytes of stored document"):
    forAllF { (document: DocumentWithStream[IO]) =>
      will(cleanStorage) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(document).ccommit

            result <- store.documentStream(uuid).ccommitStream.compile.toList

            bytes <- document.stream.compile.toList
          yield assertEquals(result, bytes)
        }
      }
    }

  test("fetches multiple documents"):
    forAllF { (fstDoc: DocumentWithStream[IO], sndDoc: DocumentWithStream[IO]) =>
      will(cleanStorage) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          for
            fstUuid <- store.store(fstDoc).ccommit
            sndUuid <- store.store(sndDoc).ccommit

            result <- fs2.Stream(fstUuid, sndUuid).through { store.fetchDocuments }.ccommitStream.compile.toList
//
          yield assertEquals(result, List(fstDoc.withUuid(fstUuid), sndDoc.withUuid(sndUuid)))
        }
      }
    }

  test("fetches documents by ownerId"):
    forAllF { (fstDoc: DocumentWithStream[IO], sndDoc: DocumentWithStream[IO]) =>
      will(cleanStorage) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          for
            fstUuid <- store.store(fstDoc).ccommit
            sndUuid <- store.store(sndDoc).ccommit

            result <- store.fetchDocumentsByOwnerId(sndDoc.metadata.ownerId).ccommitStream.compile.toList
//
          yield assertEquals(result.map(_.metadata.ownerId), List(sndDoc.metadata.ownerId))
        }
      }
    }

  test("deletes stored document"):
    forAllF { (fstDoc: DocumentWithStream[IO], sndDoc: DocumentWithStream[IO]) =>
      will(cleanStorage) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          for
            fstUuid <- store.store(fstDoc).ccommit
            sndUuid <- store.store(sndDoc).ccommit

            result <- store.delete(fstUuid).ccommit

            fstDocResult <- store.fetchDocument(fstUuid).ccommit
            sndDocResult <- store.fetchDocument(sndUuid).ccommit
          yield
            assertEquals(result, fstUuid.some)
            assertEquals(fstDocResult, none)
            assertEquals(sndDocResult.someOrFail.uuid, sndUuid)
        }
      }
    }

  test("timestamps createdAt and updatedAt upon storage"):
    forAllF { (document: DocumentWithStream[IO]) =>
      will(cleanStorage) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(document).ccommit

            createdAt <- store.fetchCreatedAt(uuid).ccommit
            updatedAt <- store.fetchUpdatedAt(uuid).ccommit
//
          yield assertEquals(createdAt, updatedAt)
        }
      }
    }

  // no support for updates yet.

  test("play it".ignore): // good to see it in action
    val filename = "mountain-bike-liguria-ponent.jpg"
    IO { bytesFrom[IO](filename) }.flatMap { content =>
      will(cleanStorage) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(documentsWithStream.sample.someOrFail).ccommit
            _ <- store
              .documentStream(uuid)
              .ccommitStream
              .through(fs2.io.file.Files[IO].writeAll(fs2.io.file.Path(filename)))
              .compile
              .drain
          yield ()
        }
      }
    }

trait PostgresStoreSpecContext:
  def cleanStorage: ConnectionIO[Unit] = sql"TRUNCATE TABLE domain.documents".update.run.void

  extension (store: DocumentsStore[IO, ConnectionIO])
    def fetchCreatedAt(uuid: DocumentId): ConnectionIO[Instant] =
      sql"SELECT created_at FROM domain.documents WHERE uuid = ${uuid}".query[Instant].unique
    def fetchUpdatedAt(uuid: DocumentId): ConnectionIO[Instant] =
      sql"SELECT updated_at FROM domain.documents WHERE uuid = ${uuid}".query[Instant].unique

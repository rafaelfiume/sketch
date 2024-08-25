package org.fiume.sketch.storage.documents.postgres

import cats.data.OptionT
import cats.effect.*
import cats.implicits.*
import doobie.{ConnectionIO, *}
import doobie.implicits.*
import doobie.postgres.implicits.*
import fs2.Stream
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.shared.domain.documents.{Document, DocumentId, DocumentWithIdAndStream, DocumentWithStream}
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.*
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.given
import org.fiume.sketch.shared.testkit.FileContentContext
import org.fiume.sketch.storage.documents.postgres.DoobieMappings.given
import org.fiume.sketch.storage.testkit.DockerPostgresSuite
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant

class PostgresDocumentsStoreSpec
    extends ScalaCheckEffectSuite
    with DockerPostgresSuite
    with FileContentContext
    with PostgresStoreSpecContext
    with ShrinkLowPriority:

  test("fetches metadata of stored document"):
    forAllF { (document: DocumentWithIdAndStream[IO]) =>
      will(cleanDocuments) {
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
      will(cleanDocuments) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(document).ccommit

            result <- OptionT(store.documentStream(uuid).ccommit).semiflatMap(_.compile.toList).value

            bytes <- document.stream.compile.toList
          yield assertEquals(result, bytes.some)
        }
      }
    }

  test("fetches documents by owner"):
    forAllF { (fstDoc: DocumentWithStream[IO], sndDoc: DocumentWithStream[IO]) =>
      will(cleanDocuments) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          for
            fstUuid <- store.store(fstDoc).ccommit
            sndUuid <- store.store(sndDoc).ccommit

            result <- store.fetchByOwner(sndDoc.metadata.owner).compile.toList
//
          yield assertEquals(result, List(sndDoc.withUuid(sndUuid)))
        }
      }
    }

  test("deletes stored document"):
    forAllF { (fstDoc: DocumentWithStream[IO], sndDoc: DocumentWithStream[IO]) =>
      will(cleanDocuments) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          for
            fstUuid <- store.store(fstDoc).ccommit
            sndUuid <- store.store(sndDoc).ccommit

            _ <- store.delete(fstUuid).ccommit

            fstResult <- IO.both(
              store.fetchDocument(fstUuid).ccommit,
              store.documentStream(fstUuid).ccommit
            )
            sndResult <- IO.both(
              store.fetchDocument(sndUuid).ccommit,
              store.documentStream(sndUuid).ccommit
            )
            _ <- IO {
              assertEquals(fstResult._1, none)
              assertEquals(fstResult._2, none)
              assert(sndResult._1.isDefined)
              assert(sndResult._2.isDefined)
            }
          yield ()
        }
      }
    }

  test("timestamps createdAt and updatedAt upon storage"):
    forAllF { (document: DocumentWithStream[IO]) =>
      will(cleanDocuments) {
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
      will(cleanDocuments) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(documentsWithStream.sample.get).ccommit
            _ <- OptionT(
              store.documentStream(uuid).ccommit
            ).semiflatMap {
              _.through(fs2.io.file.Files[IO].writeAll(fs2.io.file.Path(filename))).compile.drain
            }.value
          yield ()
        }
      }
    }

trait PostgresStoreSpecContext:
  def cleanDocuments: ConnectionIO[Unit] = sql"TRUNCATE TABLE domain.documents".update.run.void

  extension (store: PostgresDocumentsStore[IO])
    def fetchCreatedAt(uuid: DocumentId): ConnectionIO[Instant] =
      sql"SELECT created_at FROM domain.documents WHERE uuid = ${uuid}".query[Instant].unique
    def fetchUpdatedAt(uuid: DocumentId): ConnectionIO[Instant] =
      sql"SELECT updated_at FROM domain.documents WHERE uuid = ${uuid}".query[Instant].unique

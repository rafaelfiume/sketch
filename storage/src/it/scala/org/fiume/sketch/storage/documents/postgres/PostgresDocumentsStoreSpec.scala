package org.fiume.sketch.storage.documents.postgres

import cats.data.OptionT
import cats.effect.*
import cats.effect.std.Random
import cats.syntax.all.*
import doobie.{ConnectionIO, *}
import doobie.implicits.*
import doobie.postgres.implicits.*
import fs2.Stream
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.shared.test.FileContentContext
import org.fiume.sketch.storage.documents.Model.*
import org.fiume.sketch.storage.documents.Model.Metadata.*
import org.fiume.sketch.storage.documents.postgres.{DoobieMappings, PostgresDocumentsStore}
import org.fiume.sketch.storage.documents.postgres.DoobieMappings.given
import org.fiume.sketch.storage.test.support.DockerPostgresSuite
import org.fiume.sketch.test.support.DocumentsGens.*
import org.fiume.sketch.test.support.DocumentsGens.given
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.*

class PostgresDocumentsStoreSpec
    extends ScalaCheckEffectSuite
    with DockerPostgresSuite
    with FileContentContext
    with PostgresStoreSpecContext
    with ShrinkLowPriority:

  test("store document and fetch metadata") {
    forAllF { (metadata: Metadata, content: Stream[IO, Byte]) =>
      will(cleanDocuments) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(metadata, content).ccommit

            result <- store.fetchMetadata(uuid).ccommit

            _ <- IO {
              assertEquals(result, metadata.some)
              // TODO assertEquals(storedCredentials.createdAt, storedCredentials.updatedAt)
            }
          yield ()
        }
      }
    }
  }

  test("store document and fetch content") {
    forAllF { (metadata: Metadata, content: Stream[IO, Byte]) =>
      will(cleanDocuments) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(metadata, content).ccommit

            result <- OptionT(
              store.fetchContent(uuid).ccommit
            ).semiflatMap(_.compile.toList).value

            bytes <- content.compile.toList
            _ <- IO {
              assertEquals(result, bytes.some)
            }
          yield ()
        }
      }
    }
  }

  test("update document content") {
    forAllF { (metadata: Metadata, content: Stream[IO, Byte], newMetadata: Metadata, newBytes: Stream[IO, Byte]) =>
      PostgresDocumentsStore.make[IO](transactor()).use { store =>
        for
          uuid <- store.store(metadata, content).ccommit

          _ <- store.update(uuid, newMetadata, newBytes).ccommit

          updatedMetadata <- store.fetchMetadata(uuid).ccommit
          updatedBytes <- OptionT(store.fetchContent(uuid).ccommit).semiflatMap(_.compile.toList).value
          originalBytes <- newBytes.compile.toList
          _ <- IO {
            assertEquals(updatedMetadata, newMetadata.some)
            assertEquals(updatedBytes, originalBytes.some)
          }
        yield ()
      }
    }
  }

  test("delete document") {
    forAllF { (fstDoc: (Metadata, Stream[IO, Byte]), sndDoc: (Metadata, Stream[IO, Byte])) =>
      will(cleanDocuments) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          for _ <- store.commit {
              for
                fstStoredUuid <- store.store(fstDoc._1, fstDoc._2)
                sndStoredUuid <- store.store(sndDoc._1, sndDoc._2)
                _ <- store.delete(fstStoredUuid)

                fstMetadataResult <- store.fetchMetadata(fstStoredUuid)
                fstBytesResult <- store.fetchContent(fstStoredUuid)
                sndMetadataResult <- store.fetchMetadata(sndStoredUuid)
                sndBytesResult <- store.fetchContent(sndStoredUuid)

                _ <- store.lift(IO {
                  assertEquals(fstMetadataResult, none)
                  assertEquals(fstBytesResult, none)
                  assert(sndMetadataResult.isDefined)
                  assert(sndBytesResult.isDefined)
                })
              yield ()
            }
          yield ()
        }
      }
    }
  }

  test("set document's `createdAt` and `updatedAt` field to the current timestamp during storage") {
    forAllF { (metadata: Metadata, content: Stream[IO, Byte]) =>
      will(cleanDocuments) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(metadata, content).ccommit

            createdAt <- store.fetchCreatedAt(uuid).ccommit
            updatedAt <- store.fetchUpdatedAt(uuid).ccommit

            _ <- IO {
              assertEquals(createdAt, updatedAt)
            }
          yield ()
        }
      }
    }
  }

  test("set document's `updatedAt` field to the current timestamp during update") {
    forAllF { (metadata: Metadata, content: Stream[IO, Byte], newMetadata: Metadata) =>
      PostgresDocumentsStore.make[IO](transactor()).use { store =>
        for
          uuid <- store.store(metadata, content).ccommit
          updatedAt1 <- store.fetchUpdatedAt(uuid).ccommit

          _ <- store.update(uuid, newMetadata, content).ccommit

          updatedAt2 <- store.fetchUpdatedAt(uuid).ccommit
          _ <- IO {
            assert(updatedAt2.isAfter(updatedAt1), s"updatedAt $updatedAt2 should be after $updatedAt1")
          }
        yield ()
      }
    }
  }

  test("play it".ignore) { // good to see it in action
    val filename = "mountain-bike-liguria-ponent.jpg"
    IO { bytesFrom[IO](filename) }.flatMap { content =>
      will(cleanDocuments) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          val metadata = metadataG.sample.get
          for
            uuid <- store.store(metadata, content).ccommit
            _ <- OptionT(
              store.fetchContent(uuid).ccommit
            ).semiflatMap {
              _.through(fs2.io.file.Files[IO].writeAll(fs2.io.file.Path(filename))).compile.drain
            }.value
          yield ()
        }
      }
    }
  }

trait PostgresStoreSpecContext:
  def cleanDocuments: ConnectionIO[Unit] = sql"TRUNCATE TABLE documents".update.run.void

  extension (store: PostgresDocumentsStore[IO])
    def fetchCreatedAt(uuid: UUID): ConnectionIO[Instant] =
      sql"SELECT created_at FROM documents WHERE uuid = ${uuid}".query[Instant].unique
    def fetchUpdatedAt(uuid: UUID): ConnectionIO[Instant] =
      sql"SELECT updated_at FROM documents WHERE uuid = ${uuid}".query[Instant].unique

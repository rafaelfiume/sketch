package org.fiume.sketch.storage.documents.postgres

import cats.data.OptionT
import cats.effect.*
import cats.effect.std.Random
import cats.syntax.all.*
import doobie.{ConnectionIO, *}
import doobie.implicits.*
import doobie.postgres.implicits.*
import fs2.Stream
import monocle.syntax.all.*
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

  // override def scalaCheckInitialSeed = "DCHaHgKmD4XmEOKVUE1Grw8K2uWlohHvD-5gMuoh2pE="

  test("store and fetch documents metadata") {
    forAllF { (metadata: Metadata, bytes: Stream[IO, Byte]) =>
      will(cleanDocuments) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(metadata, bytes).ccommit

            result <- store.fetchMetadata(uuid).ccommit

            _ <- IO {
              assertEquals(result, metadata.some)
            }
          yield ()
        }
      }
    }
  }

  test("store documents and fetch bytes") {
    forAllF { (metadata: Metadata, bytes: Stream[IO, Byte]) =>
      will(cleanDocuments) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(metadata, bytes).ccommit

            result <- OptionT(
              store.fetchBytes(uuid).ccommit
            ).semiflatMap(_.compile.toList).value

            bytes <- bytes.compile.toList
            _ <- IO {
              assertEquals(result, bytes.some)
            }
          yield ()
        }
      }
    }
  }

  // TODO check createdAt/updatedAt
  test("update document") {
    forAllF { (metadata: Metadata, bytes: Stream[IO, Byte], newMetadata: Metadata, newBytes: Stream[IO, Byte]) =>
      PostgresDocumentsStore.make[IO](transactor()).use { store =>
        for
          uuid <- store.store(metadata, bytes).ccommit

          _ <- store.update(uuid, newMetadata, newBytes).ccommit

          updatedMetadata <- store.fetchMetadata(uuid).ccommit
          updatedBytes <- OptionT(store.fetchBytes(uuid).ccommit).semiflatMap(_.compile.toList).value
          originalBytes <- newBytes.compile.toList
          _ <- IO {
            assertEquals(updatedMetadata, newMetadata.some)
            assertEquals(updatedBytes, originalBytes.some)
          }
        yield ()
      }
    }
  }

  test("updated document -> more recent updated_at_utc") {
    forAllF { (metadata: Metadata, bytes: Stream[IO, Byte], newMetadata: Metadata) =>
      PostgresDocumentsStore.make[IO](transactor()).use { store =>
        for
          uuid <- store.store(metadata, bytes).ccommit
          updatedAt1 <- store.fetchUpdatedAt(uuid).ccommit

          _ <- store.update(uuid, newMetadata, bytes).ccommit

          updatedAt2 <- store.fetchUpdatedAt(uuid).ccommit
          _ <- IO {
            assert(updatedAt2.isAfter(updatedAt1), s"updatedAt should have been updated from $updatedAt2 to $updatedAt1")
          }
        yield ()
      }
    }
  }

  import cats.implicits.*
  override def scalaCheckInitialSeed = "4IwMdKq5GTn_pZQ2P8dE6sFcoOLRcpM23Dh_e4Zgy5H="
  test("delete document") {
    forAllF { (fstDoc: (Metadata, Stream[IO, Byte]), sndDoc: (Metadata, Stream[IO, Byte])) =>
      // TODO Wait till PropF.forAllF supports '==>' (scalacheck implication)
      will(cleanDocuments) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          for _ <- store.commit {
              for
                fstStoredUuid <- store.store(fstDoc._1, fstDoc._2)
                sndStoredUuid <- store.store(sndDoc._1, sndDoc._2)
                _ <- store.delete(fstStoredUuid)

                fstMetadataResult <- store.fetchMetadata(fstStoredUuid)
                fstBytesResult <- store.fetchBytes(fstStoredUuid)
                sndMetadataResult <- store.fetchMetadata(sndStoredUuid)
                sndBytesResult <- store.fetchBytes(sndStoredUuid)

                _ <- store.lift(IO {
                  println(s"fstResult: $fstMetadataResult")
                  println(s"sndResult: $sndMetadataResult")
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

  test("play it".ignore) { // good to see it in action
    val filename = "mountain-bike-liguria-ponent.jpg"
    IO { bytesFrom[IO](filename) }.flatMap { bytes =>
      will(cleanDocuments) {
        PostgresDocumentsStore.make[IO](transactor()).use { store =>
          val metadata = metadataG.sample.get
          for
            uuid <- store.store(metadata, bytes).ccommit
            _ <- OptionT(
              store.fetchBytes(uuid).ccommit
            ).semiflatMap {
              _.through(fs2.io.file.Files[IO].writeAll(fs2.io.file.Path(filename))).compile.drain
            }.value
          yield ()
        }
      }
    }
  }

trait PostgresStoreSpecContext:

  /*
   * Queries
   */

  def cleanDocuments: ConnectionIO[Unit] = sql"TRUNCATE TABLE documents".update.run.void

  extension (store: PostgresDocumentsStore[IO])
    def fetchUpdatedAt(uuid: UUID): ConnectionIO[Instant] =
      sql"SELECT updated_at FROM documents WHERE uuid = ${uuid}"
        .query[Instant]
        .unique

  /*
   * Lenses
   */

  extension [F[_]](metadata: Metadata)
    def withDescription(description: Description): Metadata =
      metadata.focus(_.description).replace(description)

  // TODO: DO I need this?
  extension [F[_]](doc: Document[F])
    def withDescription(description: Description): Document[F] =
      doc.focus(_.metadata.description).replace(description)

    def withBytes(bytes: Stream[F, Byte]): Document[F] =
      doc.focus(_.bytes).replace(bytes)

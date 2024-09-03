package org.fiume.sketch.storage.authorisation.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.authorisation.Role
import org.fiume.sketch.shared.auth0.UserId
import org.fiume.sketch.shared.auth0.testkit.UserGens.given
import org.fiume.sketch.shared.auth0.testkit.UsersStoreContext
import org.fiume.sketch.shared.domain.documents.{DocumentEntity, DocumentId, DocumentWithIdAndStream}
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.given
import org.fiume.sketch.shared.testkit.Syntax.EitherSyntax.*
import org.fiume.sketch.storage.documents.postgres.PostgresDocumentsStore
import org.fiume.sketch.storage.testkit.DockerPostgresSuite
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

class PostgresAccessControlSpec
    extends ScalaCheckEffectSuite
    with DockerPostgresSuite
    with UsersStoreContext
    with PostgresAccessControlSpecContext
    with ShrinkLowPriority:

  test("grants a user permission to access an entity"):
    forAllF { (userId: UserId, entityId: DocumentId, role: Role) =>
      will(cleanGrants) {
        PostgresAccessControl.make[IO](transactor()).use { accessControl =>
          for
            _ <- accessControl.allowAccess(userId, entityId, role).ccommit

            result <- accessControl.canAccess(userId, entityId).ccommit
//
          yield assert(result)
        }
      }
    }

  test("stores an entity and ensures the user has access to it"):
    forAllF { (userId: UserId, document: DocumentWithIdAndStream[IO], role: Role) =>
      will(cleanGrants) {
        (
          PostgresAccessControl.make[IO](transactor()),
          PostgresDocumentsStore.make[IO](transactor())
        ).tupled.use { case (accessControl, documentStore) =>
          for
            documentId <- accessControl
              .ensureAccess(userId, role) { documentStore.store(document) }
              .ccommit

            result <- accessControl
              .attemptWithAuthorisation(userId, documentId) {
                documentStore.fetchDocument
              }
              .ccommit
//
          yield assertEquals(result.rightValue, document.some)
        }
      }
    }

  test("does not fetch an entity if the user does not have access to it"):
    forAllF { (userId: UserId, document: DocumentWithIdAndStream[IO], role: Role) =>
      will(cleanGrants) {
        (
          PostgresAccessControl.make[IO](transactor()),
          PostgresDocumentsStore.make[IO](transactor())
        ).tupled.use { case (accessControl, documentStore) =>
          for
            // not granting access to the user
            documentId <- documentStore.store(document).ccommit

            result <- accessControl
              .attemptWithAuthorisation(userId, documentId) {
                documentStore.fetchDocument
              }
              .ccommit
//
          yield assertEquals(result.leftValue, "Unauthorised")
        }
      }
    }

  test("fetches all authorised entity ids"):
    forAllF {
      (fstUserId: UserId,
       fstDocument: DocumentWithIdAndStream[IO],
       sndDocument: DocumentWithIdAndStream[IO],
       sndUserId: UserId,
       trdDocument: DocumentWithIdAndStream[IO],
       role: Role
      ) =>
        will(cleanGrants) {
          (
            PostgresAccessControl.make[IO](transactor()),
            PostgresDocumentsStore.make[IO](transactor())
          ).tupled.use { case (accessControl, documentStore) =>
            for
              fstDocumentId <- accessControl
                .ensureAccess(fstUserId, role) { documentStore.store(fstDocument) }
                .ccommit
              sndDocumentId <- accessControl
                .ensureAccess(fstUserId, role) { documentStore.store(sndDocument) }
                .ccommit
              trdDocumentId <- accessControl
                .ensureAccess(sndUserId, role) { documentStore.store(trdDocument) }
                .ccommit

              result <- accessControl
                .fetchAllAuthorisedEntityIds[DocumentEntity](fstUserId)
                .ccommitStream
                .compile
                .toList
//
            yield assertEquals(result, List(fstDocumentId, sndDocumentId))
          }
        }
    }

  test("revokes a user's permission to access an entity"):
    forAllF { (userId: UserId, entityId: DocumentId, role: Role) =>
      will(cleanGrants) {
        PostgresAccessControl.make[IO](transactor()).use { accessControl =>
          for
            _ <- accessControl.allowAccess(userId, entityId, role).ccommit

            _ <- accessControl.revokeAccess(userId, entityId).ccommit

            result <- accessControl.canAccess(userId, entityId).ccommit
          yield assert(!result)
        }
      }
    }

trait PostgresAccessControlSpecContext:
  import org.scalacheck.Gen
  import org.scalacheck.Arbitrary

  def cleanGrants: ConnectionIO[Unit] = sql"TRUNCATE TABLE auth.access_control".update.run.void

  given Arbitrary[Role] = Arbitrary(roles)
  def roles: Gen[Role] = Gen.const(Role.Owner)

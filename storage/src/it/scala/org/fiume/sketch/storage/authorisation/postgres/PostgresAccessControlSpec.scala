package org.fiume.sketch.storage.authorisation.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.authorisation.{ContextualRole, GlobalRole, Role}
import org.fiume.sketch.authorisation.GlobalRole.Superuser
import org.fiume.sketch.authorisation.testkit.AccessControlGens.given
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

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(10)

  /*
   ** Global Role Specs
   */

  test("grants a Superuser permission to access all entities"):
    forAllF { (userId: UserId, entityId: DocumentId, role: GlobalRole) =>
      will(cleanGrants) {
        PostgresAccessControl.make[IO](transactor()).use { accessControl =>
          for
            _ <- accessControl.grantGlobalAccess(userId, role).ccommit

            // always true for a Superuser even if entityId doesn't exist
            grantedAccess <- accessControl.canAccess(userId, entityId).ccommit
//
          yield assert(grantedAccess)
        }
      }
    }

  test("fetches all authorised entity ids for a global role"):
    forAllF {
      (fstUserId: UserId,
       fstDocument: DocumentWithIdAndStream[IO],
       sndDocument: DocumentWithIdAndStream[IO],
       sndUserId: UserId,
       trdDocument: DocumentWithIdAndStream[IO]
      ) =>
        will(cleanGrants) {
          (
            PostgresAccessControl.make[IO](transactor()),
            PostgresDocumentsStore.make[IO](transactor())
          ).tupled.use { case (accessControl, documentStore) =>
            for
              _ <- accessControl.grantGlobalAccess(fstUserId, Superuser).ccommit
              fstDocumentId <- accessControl
                .ensureAccess(fstUserId, ContextualRole.Owner) { documentStore.store(fstDocument) }
                .ccommit
              sndDocumentId <- accessControl
                .ensureAccess(sndUserId, ContextualRole.Owner) { documentStore.store(sndDocument) }
                .ccommit
              trdDocumentId <- accessControl
                .ensureAccess(sndUserId, ContextualRole.Owner) { documentStore.store(trdDocument) }
                .ccommit

              result <- accessControl
                .fetchAllAuthorisedEntityIds(fstUserId, "DocumentEntity")
                .ccommitStream
                .compile
                .toList
//
            yield assertEquals(result, List(fstDocumentId, sndDocumentId, trdDocumentId))
          }
        }
    }

  /*
   ** Contextual Role Specs
   */
  test("grants a user permission to access an entity"):
    forAllF { (userId: UserId, entityId: DocumentId, globalRole: GlobalRole, contextualRole: ContextualRole) =>
      will(cleanGrants) {
        PostgresAccessControl.make[IO](transactor()).use { accessControl =>
          for
            // TODO check property where contextual role takes precedence over global role
            _ <- accessControl.grantGlobalAccess(userId, globalRole).ccommit
            _ <- accessControl.grantAccess(userId, entityId, contextualRole).ccommit

            result <- accessControl.canAccess(userId, entityId).ccommit
//
          yield assert(result)
        }
      }
    }

  test("stores an entity and ensures the user has access to it"):
    forAllF { (userId: UserId, document: DocumentWithIdAndStream[IO], role: ContextualRole) =>
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
       role: ContextualRole
      ) =>
        will(cleanGrants) {
          (
            PostgresAccessControl.make[IO](transactor()),
            PostgresDocumentsStore.make[IO](transactor())
          ).tupled.use { case (accessControl, documentStore) =>
            for
              fstDocumentId <- accessControl.ensureAccess(fstUserId, role) { documentStore.store(fstDocument) }.ccommit
              sndDocumentId <- accessControl.ensureAccess(fstUserId, role) { documentStore.store(sndDocument) }.ccommit
              trdDocumentId <- accessControl.ensureAccess(sndUserId, role) { documentStore.store(trdDocument) }.ccommit

              result <- accessControl
                .fetchAllAuthorisedEntityIds(fstUserId, "DocumentEntity")
                .ccommitStream
                .compile
                .toList
//
            yield assertEquals(result, List(fstDocumentId, sndDocumentId))
          }
        }
    }

  test("revokes a user's permission to access an entity"):
    forAllF { (userId: UserId, entityId: DocumentId, role: ContextualRole) =>
      will(cleanGrants) {
        PostgresAccessControl.make[IO](transactor()).use { accessControl =>
          for
            _ <- accessControl.grantAccess(userId, entityId, role).ccommit

            _ <- accessControl.revokeAccess(userId, entityId).ccommit

            grantRemoved <- accessControl.canAccess(userId, entityId).map(!_).ccommit
          yield assert(grantRemoved)
        }
      }
    }

trait PostgresAccessControlSpecContext:

  def cleanGrants: ConnectionIO[Unit] =
    sql"TRUNCATE TABLE auth.access_control".update.run.void *>
      sql"TRUNCATE TABLE auth.global_access_control".update.run.void

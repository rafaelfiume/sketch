package org.fiume.sketch.storage.authorisation.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.auth.User.UserCredentials
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.testkit.UserGens.*
import org.fiume.sketch.shared.auth.testkit.UserGens.given
import org.fiume.sketch.shared.auth.testkit.UsersStoreContext
import org.fiume.sketch.shared.authorisation.{AccessDenied, ContextualRole, GlobalRole, Role}
import org.fiume.sketch.shared.authorisation.ContextualRole.Owner
import org.fiume.sketch.shared.authorisation.GlobalRole.{Admin, Superuser}
import org.fiume.sketch.shared.authorisation.testkit.AccessControlGens.*
import org.fiume.sketch.shared.authorisation.testkit.AccessControlGens.given
import org.fiume.sketch.shared.domain.documents.{DocumentId, DocumentWithIdAndStream}
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.*
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.given
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.someOrFail
import org.fiume.sketch.storage.auth.postgres.PostgresUsersStore
import org.fiume.sketch.storage.documents.postgres.PostgresDocumentsStore
import org.fiume.sketch.storage.testkit.DockerPostgresSuite
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

class PostgresAccessControlSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with DockerPostgresSuite
    with UsersStoreContext
    with PostgresAccessControlSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(5)

  /*
   ** Global Role Specs
   */

  test("grants Admin users permission to access all entities"):
    forAllF(userIds, entitiesIds) { (userId, entityId) =>
      will(cleanStorage) {
        PostgresAccessControl.make[IO](transactor()).use { accessControl =>
          for
            _ <- accessControl.grantGlobalAccess(userId, Admin).ccommit

            // note that it is up to the developer to make sure that a pair of existing userId and entityId are passed
            grantedAccess <- accessControl.canAccess(userId, entityId).ccommit
//
          yield assert(grantedAccess)
        }
      }
    }

  test("grants Superuser's permission to access all entities except UserEntity"):
    forAllF(userIds, entitiesIds) { (userId, entityId) =>
      will(cleanStorage) {
        PostgresAccessControl.make[IO](transactor()).use { accessControl =>
          for
            _ <- accessControl.grantGlobalAccess(userId, Superuser).ccommit

            grantedAccess <- accessControl.canAccess(userId, entityId).ccommit
//
          yield if entityId.entityType =!= "UserEntity" then assert(grantedAccess) else assert(!grantedAccess)
        }
      }
    }

  test("fetches all existent entity ids if user is Admin"):
    forAllF {
      (fstUser: UserCredentials,
       globalRole: GlobalRole,
       fstDocument: DocumentWithIdAndStream[IO],
       sndDocument: DocumentWithIdAndStream[IO],
       sndUser: UserCredentials,
       trdDocument: DocumentWithIdAndStream[IO]
      ) =>
        will(cleanStorage) {
          (
            PostgresAccessControl.make[IO](transactor()),
            PostgresDocumentsStore.make[IO](transactor()),
            PostgresUsersStore.make[IO](transactor())
          ).tupled.use { case (accessControl, docStore, usersStore) =>
            for
              fstUserId <- usersStore
                .createAccount(fstUser)
                .flatTap { userId => accessControl.grantAccess(userId, userId, Owner) }
                .ccommit
              sndUserId <- usersStore
                .createAccount(sndUser)
                .flatTap { userId => accessControl.grantAccess(userId, userId, Owner) }
                .ccommit
              fstDocId <- accessControl.ensureAccess_(fstUserId, Owner) { docStore.store(fstDocument) }.ccommit
              sndDocId <- accessControl.ensureAccess_(sndUserId, Owner) { docStore.store(sndDocument) }.ccommit
              trdDocId <- accessControl.ensureAccess_(sndUserId, Owner) { docStore.store(trdDocument) }.ccommit
              _ <- accessControl.grantGlobalAccess(fstUserId, globalRole).ccommit

              result <- accessControl
                .fetchAllAuthorisedEntityIds(fstUserId, "DocumentEntity")
                .ccommitStream
                .compile
                .toList
//
            yield assertEquals(result, List(fstDocId, sndDocId, trdDocId))
          }
        }
    }

  // UserEntity is special in that it is extremely sensitive
  test("fetches all existent entity ids, except non-owned UserEntity, if user is Superuser"):
    forAllF { (fstUser: UserCredentials, globalRole: GlobalRole, sndUser: UserCredentials) =>
      will(cleanStorage) {
        (
          PostgresAccessControl.make[IO](transactor()),
          PostgresUsersStore.make[IO](transactor())
        ).tupled.use { case (accessControl, usersStore) =>
          for
            fstUserId <- usersStore
              .createAccount(fstUser)
              .flatTap { userId => accessControl.grantAccess(userId, userId, Owner) }
              .ccommit
            sndUserId <- usersStore
              .createAccount(sndUser)
              .flatTap { userId => accessControl.grantAccess(userId, userId, Owner) }
              .ccommit
            _ <- accessControl.grantGlobalAccess(fstUserId, globalRole).ccommit

            result: List[UserId] <- accessControl
              .fetchAllAuthorisedEntityIds(fstUserId, "UserEntity")
              .ccommitStream
              .compile
              .toList
//
          yield
            if globalRole == Superuser then assertEquals(result, List(fstUserId))
            if globalRole == Admin then assertEquals(result, List(fstUserId, sndUserId))
        }
      }
    }

  /*
   ** Contextual Role Specs
   */
  test("grants a user ownership, and thus access, to an entity"):
    forAllF(userIds, entitiesIds) { (userId, entityId) =>
      will(cleanStorage) {
        PostgresAccessControl.make[IO](transactor()).use { accessControl =>
          for
            _ <- accessControl.grantAccess(userId, entityId, Owner).ccommit

            result <- accessControl.canAccess(userId, entityId).ccommit
//
          yield assert(result)
        }
      }
    }

  test("stores an entity and ensures that the user is its owner"):
    forAllF { (userId: UserId, document: DocumentWithIdAndStream[IO], role: ContextualRole) =>
      will(cleanStorage) {
        (
          PostgresAccessControl.make[IO](transactor()),
          PostgresDocumentsStore.make[IO](transactor())
        ).tupled.use { case (accessControl, docStore) =>
          for
            docId <- accessControl.ensureAccess_(userId, role) { docStore.store(document) }.ccommit

            result <- accessControl.attempt(userId, docId) { docStore.fetchDocument }.ccommit
//
          yield assertEquals(result.rightOrFail, document.some)
        }
      }
    }

  test("does not perform operation on entity if the user is not authorized to access it"):
    forAllF { (userId: UserId, document: DocumentWithIdAndStream[IO], role: Role) =>
      will(cleanStorage) {
        (
          PostgresAccessControl.make[IO](transactor()),
          PostgresDocumentsStore.make[IO](transactor())
        ).tupled.use { case (accessControl, docStore) =>
          for
            // no granting access to the user
            docId <- docStore.store(document).ccommit

            result <- accessControl.attempt(userId, docId) { docStore.fetchDocument }.ccommit
//
          yield assertEquals(result.leftOrFail, AccessDenied)
        }
      }
    }

  test("fetches all entity ids of entities owned by the user"):
    forAllF {
      (fstUserId: UserId,
       fstDocument: DocumentWithIdAndStream[IO],
       sndDocument: DocumentWithIdAndStream[IO],
       sndUserId: UserId,
       trdDocument: DocumentWithIdAndStream[IO],
       role: ContextualRole
      ) =>
        will(cleanStorage) {
          (
            PostgresAccessControl.make[IO](transactor()),
            PostgresDocumentsStore.make[IO](transactor())
          ).tupled.use { case (accessControl, documentStore) =>
            for
              fstDocumentId <- accessControl.ensureAccess_(fstUserId, role) { documentStore.store(fstDocument) }.ccommit
              sndDocumentId <- accessControl.ensureAccess_(fstUserId, role) { documentStore.store(sndDocument) }.ccommit
              trdDocumentId <- accessControl.ensureAccess_(sndUserId, role) { documentStore.store(trdDocument) }.ccommit

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

  // TODO What if global access?
  test("revokes a user's permission to access an entity"):
    forAllF(userIds, entitiesIds, contextualRoles) { (userId, entityId, contextualRole) =>
      will(cleanStorage) {
        PostgresAccessControl.make[IO](transactor()).use { accessControl =>
          for
            _ <- accessControl.grantAccess(userId, entityId, contextualRole).ccommit

            _ <- accessControl.revokeContextualAccess(userId, entityId).ccommit

            grantRemoved <- accessControl.canAccess(userId, entityId).map(!_).ccommit
          yield assert(grantRemoved)
        }
      }
    }

  /*
   ** Roles precedence
   */

  // fetchRole returns the least permissive role first
  test("contextual roles takes precedence over global roles"):
    forAllF(userIds, entitiesIds, globalRoles) { (userId, entityId, globalRole) =>
      will(cleanStorage) {
        PostgresAccessControl.make[IO](transactor()).use { accessControl =>
          for
            _ <- accessControl.grantGlobalAccess(userId, globalRole).ccommit
            _ <- accessControl.grantAccess(userId, entityId, Owner).ccommit

            result <- accessControl.fetchRole(userId, entityId).ccommit
//
          yield assertEquals(result.someOrFail, Role.Contextual(Owner))
        }
      }
    }

trait PostgresAccessControlSpecContext:
  import org.scalacheck.Gen

  def cleanStorage: ConnectionIO[Unit] =
    sql"""|TRUNCATE TABLE
          |  auth.access_control,
          |  auth.global_access_control,
          |  auth.account_deletion_scheduled_events,
          |  auth.users,
          |  domain.documents
      """.stripMargin.update.run.void

  def entitiesIds = Gen.oneOf(documentsIds, userIds)

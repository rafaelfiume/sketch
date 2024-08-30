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
import org.fiume.sketch.shared.domain.documents.DocumentWithIdAndStream
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

  test("grants a user permission to access a resource"):
    forAllF { (userId: UserId, resourceId: TestResourceId, role: Role) =>
      will(cleanGrants) {
        PostgresAccessControl.make[IO](transactor()).use { accessControl =>
          for
            _ <- accessControl.allowAccess(userId, resourceId, role).ccommit

            result <- accessControl.canAccess(userId, resourceId).ccommit
//
          yield assert(result)
        }
      }
    }

  test("grants a user permission to access a resource and then perform the action"):
    forAllF { (userId: UserId, document: DocumentWithIdAndStream[IO], role: Role) =>
      will(cleanGrants) {
        (
          PostgresAccessControl.make[IO](transactor()),
          PostgresDocumentsStore.make[IO](transactor())
        ).tupled.use { case (accessControl, documentStore) =>
          for
            documentId <- accessControl
              .createResourceThenAllowAccess(userId, role)(documentStore.store(document))
              .ccommit

            result <- accessControl
              .fetchResourceIfAuthorised(userId, documentId)(documentStore.fetchDocument)
              .ccommit
//
          yield assertEquals(result.rightValue, document.some)
        }
      }
    }

  test("does not grant a user permission to access a resource"):
    forAllF { (userId: UserId, document: DocumentWithIdAndStream[IO], role: Role) =>
      will(cleanGrants) {
        (
          PostgresAccessControl.make[IO](transactor()),
          PostgresDocumentsStore.make[IO](transactor())
        ).tupled.use { case (accessControl, documentStore) =>
          for
            documentId <- documentStore.store(document).ccommit

            result <- accessControl
              .fetchResourceIfAuthorised(userId, documentId)(documentStore.fetchDocument)
              .ccommit
//
          yield assertEquals(result.leftValue, "Unauthorised")
        }
      }
    }

  test("revokes a user's permission to access a resource"):
    forAllF { (userId: UserId, resourceId: TestResourceId, role: Role) =>
      will(cleanGrants) {
        PostgresAccessControl.make[IO](transactor()).use { accessControl =>
          for
            _ <- accessControl.allowAccess(userId, resourceId, role).ccommit

            _ <- accessControl.revokeAccess(userId, resourceId).ccommit

            result <- accessControl.canAccess(userId, resourceId).ccommit
          yield assert(!result)
        }
      }
    }

trait PostgresAccessControlSpecContext:
  import org.scalacheck.Gen
  import org.fiume.sketch.shared.app.{Resource, ResourceId}
  import org.scalacheck.Arbitrary

  import java.util.UUID

  def cleanGrants: ConnectionIO[Unit] = sql"TRUNCATE TABLE auth.access_control".update.run.void

  type TestResourceId = ResourceId[TestResourceEntity]
  object TestResourceId:
    def apply(uuid: UUID): TestResourceId = ResourceId[TestResourceEntity](uuid)
  sealed trait TestResourceEntity extends Resource

  given Arbitrary[TestResourceId] = Arbitrary(testResourceIds)
  def testResourceIds: Gen[TestResourceId] = Gen.uuid.map(TestResourceId(_)) :| "TestResourceId"

  given Arbitrary[Role] = Arbitrary(roles)
  def roles: Gen[Role] = Gen.const(Role.Owner)

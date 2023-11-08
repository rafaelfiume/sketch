package org.fiume.sketch.acceptance

import cats.effect.IO
import io.circe.Json
import munit.Assertions.*
import munit.CatsEffectSuite
import org.fiume.sketch.acceptance.testkit.{AuthenticationContext, Http4sClientContext}
import org.fiume.sketch.shared.auth0.testkit.UserGens
import org.fiume.sketch.shared.testkit.FileContentContext
import org.http4s.Status.*
import org.http4s.circe.*
import org.http4s.headers.Authorization

class DocumentsSpec extends CatsEffectSuite with FileContentContext with AuthenticationContext with DocumentsSpecContext:

  val docName = "a-unique-name-for-altamural.jpg"
  val docDesc = "La bella Altamura in Puglia <3"
  val ownedBy = UserGens.userIds.sample.get.toString()
  val pathToFile = "altamura.jpg"

  test("store documents"):
    for
      authenticated <- loginAndGetAuthenticatedUser()
      authorizationHeader = authenticated.authorization
      _ <- withHttp { client =>
        for
          uuid <- client.expect[Json](fileUploadRequest(payload(docName, docDesc, ownedBy), pathToFile, authorizationHeader)).map(_.uuid)

          _ <- client.expect[Json](s"http://localhost:8080/documents/$uuid/metadata".get.withHeaders(authorizationHeader)).map { res =>
            assertEquals(res.uuid, uuid)
            assertEquals(res.docName, docName)
            assertEquals(res.description, docDesc)
            assertEquals(res.createdBy, authenticated.user.uuid.toString)
            assertEquals(res.ownedBy, ownedBy)
          }

          content <- client
            .stream(s"http://localhost:8080/documents/$uuid".get.withHeaders(authorizationHeader))
            .flatMap(_.body)
            .compile
            .toList
          originalContent <- bytesFrom[IO](pathToFile).compile.toList
          _ <- IO { assertEquals(content, originalContent) }
        yield ()
      }
    yield ()

  test("delete documents"):
    for
      authenticated <- loginAndGetAuthenticatedUser()
      authorizationHeader = authenticated.authorization
      _ <- withHttp { client =>
        for
          uuid <- client.expect[Json](fileUploadRequest(payload(docName, docDesc, ownedBy), pathToFile, authorizationHeader)).map(_.uuid)

          _ <- client.status(s"http://localhost:8080/documents/$uuid".delete.withHeaders(authorizationHeader)).map { status =>
            assertEquals(status, NoContent)
          }

          _ <- client.status(s"http://localhost:8080/documents/$uuid/metadata".get.withHeaders(authorizationHeader)).map { status =>
            assertEquals(status, NotFound)
          }

          _ <- client.status(s"http://localhost:8080/documents/$uuid".get.withHeaders(authorizationHeader)).map { status =>
            assertEquals(status, NotFound)
          }
        yield ()
      }
    yield ()

trait DocumentsSpecContext extends Http4sClientContext:
  import org.http4s.{MediaType, Request}
  import org.http4s.multipart.{Boundary, Multipart, Part}
  import org.http4s.client.dsl.io.*
  import org.http4s.headers.`Content-Type`

  def fileUploadRequest(payload: String, pathToFile: String, authHeader: Authorization): Request[IO] =
    val imageFile = getClass.getClassLoader.getResource(pathToFile)
    val multipart = Multipart[IO](
      parts = Vector(
        Part.formData("metadata", payload),
        Part.fileData("bytes", imageFile, `Content-Type`(MediaType.image.jpeg))
      ),
      boundary = Boundary("boundary")
    )
    "http://localhost:8080/documents".post.withEntity(multipart).withHeaders(multipart.headers.put(authHeader))

  // TODO Load from storage/src/test/resources/storage/contract/http/document.json
  def payload(name: String, description: String, ownedBy: String): String =
    s"""
       |{
       |  "name": "$name",
       |  "description": "$description",
       |  "ownedBy": "$ownedBy"
       |}
      """.stripMargin

  extension (json: Json)
    def uuid: String = json.hcursor.get[String]("uuid").getOrElse(fail("'uuid' field not found"))
    def docName: String = json.hcursor.downField("metadata").get[String]("name").getOrElse(fail("'name' field not found"))
    def description: String = json.hcursor.downField("metadata").get[String]("description").getOrElse(fail("'description' field not found"))
    def createdBy: String = json.hcursor.downField("metadata").get[String]("createdBy").getOrElse(fail("'createdBy' field not found"))
    def ownedBy: String = json.hcursor.downField("metadata").get[String]("ownedBy").getOrElse(fail("'ownedBy' field not found"))

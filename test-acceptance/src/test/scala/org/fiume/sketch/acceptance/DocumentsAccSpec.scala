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

class DocumentsAccSpec extends CatsEffectSuite with FileContentContext with AuthenticationContext with DocumentsSpecContext:

  val docName = "a-unique-name-for-altamural.jpg"
  val docDesc = "La bella Altamura in Puglia <3"
  val owner = UserGens.userIds.sample.get.asString()
  val pathToFile = "altamura.jpg"

  test("store documents"):
    for
      authenticated <- loginAndGetAuthenticatedUser()
      authorizationHeader = authenticated.authorization
      _ <- withHttp { client =>
        for
          uuid <- client
            .expect[Json](fileUploadRequest(payload(docName, docDesc, owner), pathToFile, authorizationHeader))
            .map(_.uuid)

          _ <- client.expect[Json](s"http://localhost:8080/documents/$uuid/metadata".get.withHeaders(authorizationHeader)).map {
            res =>
              assertEquals(res.uuid, uuid)
              assertEquals(res.docName, docName)
              assertEquals(res.description, docDesc)
              assertEquals(res.author, authenticated.user.uuid.asString())
              assertEquals(res.owner, owner)
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
          uuid <- client
            .expect[Json](fileUploadRequest(payload(docName, docDesc, owner), pathToFile, authorizationHeader))
            .map(_.uuid)

          _ <- client.status(s"http://localhost:8080/documents/$uuid".delete.withHeaders(authorizationHeader)).map { status =>
            assertEquals(status, NoContent)
          }

          _ <- client.status(s"http://localhost:8080/documents/$uuid/metadata".get.withHeaders(authorizationHeader)).map {
            status =>
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

  // TODO Duplicated from `contract/document/metadata.request.json`
  def payload(name: String, description: String, owner: String): String =
    s"""
       |{
       |  "name": "$name",
       |  "description": "$description",
       |  "owner": "$owner"
       |}
      """.stripMargin

  extension (json: Json)
    def uuid: String = json.hcursor.get[String]("uuid").getOrElse(fail("'uuid' field not found"))
    def docName: String = json.hcursor.downField("metadata").get[String]("name").getOrElse(fail("'name' field not found"))
    def description: String =
      json.hcursor.downField("metadata").get[String]("description").getOrElse(fail("'description' field not found"))
    def author: String =
      json.hcursor.downField("metadata").get[String]("author").getOrElse(fail("'author' field not found"))
    def owner: String = json.hcursor.downField("metadata").get[String]("owner").getOrElse(fail("'owner' field not found"))

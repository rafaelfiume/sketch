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

class DocumentsAccSpec extends CatsEffectSuite with AuthenticationContext with DocumentsSpecContext:

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
          metadataPayload <- payload(docName, docDesc, owner)
          uuid <- client
            .expect[Json](fileUploadRequest(metadataPayload, pathToFile, authorizationHeader))
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
          metadataPayload <- payload(docName, docDesc, owner)
          uuid <- client
            .expect[Json](fileUploadRequest(metadataPayload, pathToFile, authorizationHeader))
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

trait DocumentsSpecContext extends Http4sClientContext with FileContentContext:
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

  // TODO Duplicated see load test
  def payload(name: String, description: String, owner: String): IO[String] =
    stringFrom[IO]("document/metadata.request.json")
      .map { json =>
        json
          .replace("SuCJzND", name)
          .replace("19ZHxAyyBQ4olkFv7YUGuAGq7A5YWzPIfZAd703rMzCO8uvua2XliMf6dzw", description)
          .replace("7a21d5bc-9e3a-4c1b-bf6c-33cc9926c3ac", owner)
      }
      .use(IO.pure)

  extension (json: Json)
    def uuid: String = json.hcursor.get[String]("uuid").getOrElse(fail("'uuid' field not found"))
    def docName: String = json.hcursor.downField("metadata").get[String]("name").getOrElse(fail("'name' field not found"))
    def description: String =
      json.hcursor.downField("metadata").get[String]("description").getOrElse(fail("'description' field not found"))
    def author: String =
      json.hcursor.downField("metadata").get[String]("author").getOrElse(fail("'author' field not found"))
    def owner: String = json.hcursor.downField("metadata").get[String]("owner").getOrElse(fail("'owner' field not found"))

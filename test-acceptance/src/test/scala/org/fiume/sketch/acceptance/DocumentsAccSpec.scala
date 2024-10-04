package org.fiume.sketch.acceptance

import cats.effect.IO
import io.circe.Json
import munit.Assertions.*
import munit.CatsEffectSuite
import org.fiume.sketch.acceptance.testkit.{AccountSetUpAndLoginContext}
import org.fiume.sketch.shared.testkit.Http4sClientContext
import org.fiume.sketch.shared.testkit.FileContentContext
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.http4s.Status.*
import org.http4s.circe.*
import org.http4s.headers.Authorization

class DocumentsAccSpec extends CatsEffectSuite with AccountSetUpAndLoginContext with DocumentsSpecContext:

  val docName = "a-unique-name-for-altamural.jpg"
  val docDesc = "La bella Altamura in Puglia <3"
  val pathToFile = "altamura.jpg"

  test("store documents"):
    for
      jwt <- loginAndGetAuthenticatedUser()
      authHeader = Authorization.parse(s"Bearer ${jwt.value}").rightOrFail
      _ <- withHttp { client =>
        for
          metadataPayload <- payload(docName, docDesc)
          uuid <- client
            .expect[Json](fileUploadRequest(metadataPayload, pathToFile, authHeader))
            .map(_.uuid)

          _ <- client.expect[Json](s"http://localhost:8080/documents/$uuid/metadata".get.withHeaders(authHeader)).map { res =>
            assertEquals(res.uuid, uuid)
            assertEquals(res.docName, docName)
            assertEquals(res.description, docDesc)
          }

          content <- client
            .stream(s"http://localhost:8080/documents/$uuid".get.withHeaders(authHeader))
            .flatMap(_.body)
            .compile
            .toList
          originalContent <- bytesFrom[IO](pathToFile).compile.toList
        yield assertEquals(content, originalContent)
      }
    yield ()

  test("delete documents"):
    for
      jwt <- loginAndGetAuthenticatedUser()
      authHeader = Authorization.parse(s"Bearer ${jwt.value}").rightOrFail
      _ <- withHttp { client =>
        for
          metadataPayload <- payload(docName, docDesc)
          uuid <- client
            .expect[Json](fileUploadRequest(metadataPayload, pathToFile, authHeader))
            .map(_.uuid)

          _ <- client.status(s"http://localhost:8080/documents/$uuid".delete.withHeaders(authHeader)).map {
            assertEquals(_, NoContent)
          }

          _ <- client.status(s"http://localhost:8080/documents/$uuid/metadata".get.withHeaders(authHeader)).map {
            assertEquals(_, Forbidden)
          }

          _ <- client.status(s"http://localhost:8080/documents/$uuid".get.withHeaders(authHeader)).map {
            assertEquals(_, Forbidden)
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
  def payload(name: String, description: String): IO[String] =
    stringFrom[IO]("domain/documents/get.metadata.request.json")
      .map { json =>
        json
          .replace("SuCJzND", name)
          .replace("19ZHxAyyBQ4olkFv7YUGuAGq7A5YWzPIfZAd703rMzCO8uvua2XliMf6dzw", description)
      }
      .use(IO.pure)

  extension (json: Json)
    def uuid: String = json.hcursor.get[String]("uuid").getOrElse(fail("'uuid' field not found"))
    def docName: String = json.hcursor.downField("metadata").get[String]("name").getOrElse(fail("'name' field not found"))
    def description: String =
      json.hcursor.downField("metadata").get[String]("description").getOrElse(fail("'description' field not found"))

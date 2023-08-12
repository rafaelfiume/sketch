package org.fiume.sketch.acceptance

import cats.effect.IO
import io.circe.Json
import munit.Assertions.*
import munit.CatsEffectSuite
import org.fiume.sketch.acceptance.testkit.Http4sClientContext
import org.fiume.sketch.shared.testkit.FileContentContext
import org.http4s.Status.*
import org.http4s.circe.*
import org.http4s.headers.Authorization

class DocumentsSpec extends CatsEffectSuite with FileContentContext with AuthenticationContext with DocumentsSpecContext:

  val docName = "a-unique-name-for-altamural.jpg"
  val docDesc = "La bella Altamura in Puglia <3"
  val pathToFile = "altamura.jpg"

  test("store documents"):
    for
      authHeader <- loginAndGetAuthenticationHeader()
      _ <- http { client =>
        for
          uuid <- client.expect[Json](fileUploadRequest(payload(docName, docDesc), pathToFile, authHeader)).map(_.uuid)
  
          _ <- client.expect[Json](s"http://localhost:8080/documents/$uuid/metadata".get.withHeaders(authHeader)).map { res =>
            assertEquals(res.docName, docName)
            assertEquals(res.description, docDesc)
          }
  
          content <- client
            .stream(s"http://localhost:8080/documents/$uuid".get.withHeaders(authHeader))
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
      authHeader <- loginAndGetAuthenticationHeader()
      _ <- http { client =>
        for
          uuid <- client.expect[Json](fileUploadRequest(payload(docName, docDesc), pathToFile, authHeader)).map(_.uuid)
  
          _ <- client.status(s"http://localhost:8080/documents/$uuid".delete.withHeaders(authHeader)).map { status =>
            assertEquals(status, NoContent)
          }
  
          _ <- client.status(s"http://localhost:8080/documents/$uuid/metadata".get.withHeaders(authHeader)).map { status =>
            assertEquals(status, NotFound)
          }
  
          _ <- client.status(s"http://localhost:8080/documents/$uuid".get.withHeaders(authHeader)).map { status =>
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

  // TODO Load from storage/src/test/resources/storage/contract/http/document.metadata.json
  def payload(name: String, description: String): String =
    s"""
       |{
       |  "name": "$name",
       |  "description": "$description"
       |}
      """.stripMargin

  extension (json: Json)
    def uuid: String = json.hcursor.get[String]("uuid").getOrElse(fail("'uuid' field not found"))
    def docName: String = json.hcursor.get[String]("name").getOrElse(fail("'name' field not found"))
    def description: String = json.hcursor.get[String]("description").getOrElse(fail("'description' field not found"))

trait AuthenticationContext extends Http4sClientContext:
  import org.fiume.sketch.auth0.scripts.UsersScript.*
  import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.*
  import org.fiume.sketch.shared.auth0.testkit.UserGens.*
  import org.fiume.sketch.auth0.http.AuthRoutes.Model.LoginResponsePayload
  import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
  import org.fiume.sketch.shared.auth0.User.Username
  import org.http4s.circe.CirceEntityDecoder.*
  import org.fiume.sketch.auth0.http.AuthRoutes.Model.Json.given
  import org.fiume.sketch.shared.testkit.EitherSyntax.*
  import org.scalacheck.Gen

  private def aUsername() = Gen.delay(validUsernames).sample.get
  private def aPassword() = Gen.delay(validPlainPasswords).sample.get
  private def loginRequest(username: Username, password: PlainPassword) =
    "http://localhost:8080/login".post.withEntity(payload(username, password))

  def loginAndGetAuthenticationHeader(): IO[Authorization] =
    val username = aUsername()
    val password = aPassword()
    doRegistreUser(username, password) *>
      http {
        _.expect[LoginResponsePayload](loginRequest(username, password))
          .map(_.token)
          .map(jwtToken => Authorization.parse(s"Bearer $jwtToken"))
          .map(_.rightValue)
      }

  // TODO Improve this somehow....
  private def payload(username: Username, password: PlainPassword): String =
    s"""
       |{
       |  "username": "${username.value}",
       |  "password": "${password.value}"
       |}
      """.stripMargin

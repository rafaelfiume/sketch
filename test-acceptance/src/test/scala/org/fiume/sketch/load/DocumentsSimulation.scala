package org.fiume.sketch.load

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import io.gatling.core.Predef.*
import io.gatling.http.Predef.{http, *}
import org.fiume.sketch.acceptance.testkit.AccountSetUpAndLoginContext
import org.fiume.sketch.shared.testkit.FileContentContext
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.http4s.headers.Authorization

import scala.annotation.nowarn
import scala.concurrent.duration.*

@nowarn
class DocumentsSimulation extends Simulation with AccountSetUpAndLoginContext with DocumentsSimulationContext:

  private given IORuntime = IORuntime.global

  private val upload =
    val docName = "Nicolas_e_Joana"
    val docDesc = "Meus amores <3"
    val pathToFile = "meus-fofinhos.jpg"
    val bytes = bytesFrom[IO](pathToFile).compile.toVector.map(_.toArray).unsafeRunSync()
    val metadataPayload = payload(docName, docDesc).unsafeRunSync()
    exec(
      http("upload document")
        .post("/documents")
        .header("Content-Type", "multipart/form-data")
        .bodyParts(
          StringBodyPart("metadata", metadataPayload),
          ByteArrayBodyPart("bytes", bytes)
            .fileName(docName)
            .contentType("application/octet-stream")
        )
        .asMultipartForm
        .check(status.is(201))
        .check(jsonPath("$.uuid").saveAs("documentId"))
    )

  private val download = exec(
    http("download document")
      .get("/documents/#{documentId}")
      .check(status.is(200))
  )

  private val delete = exec(
    http("delete document")
      .delete("/documents/#{documentId}")
      .check(status.is(204))
  )

  private val documentsCrudScenario = scenario("DocumentsRoutes")
    .exec(upload)
    .exec(download)
    .exitHereIfFailed
    .exec(delete)

  private val httpProtocol =
    val jwt = loginAndGetAuthenticatedUser().unsafeRunSync()
    val authHeader = Authorization.parse(s"Bearer ${jwt.value}").rightOrFail
    http
      .baseUrl("http://localhost:8080")
      .acceptHeader("application/json")
      .contentTypeHeader("multipart/form-data")
      .header("Authorization", authHeader.credentials.toString)

  setUp(
    documentsCrudScenario.inject(
      constantUsersPerSec(5).during(10.seconds), // Stay at 5 users for 10 secs
      rampUsersPerSec(1).to(10).during(30.seconds) // Ramp up to 10 users in 30 secs
    )
  ).protocols(httpProtocol)

trait DocumentsSimulationContext extends FileContentContext:
  // TODO Duplicated see acc test
  def payload(name: String, description: String): IO[String] =
    stringFrom[IO]("domain/documents/get.metadata.request.json")
      .map { json =>
        json
          .replace("SuCJzND", name)
          .replace("19ZHxAyyBQ4olkFv7YUGuAGq7A5YWzPIfZAd703rMzCO8uvua2XliMf6dzw", description)
      }
      .use(IO.pure)

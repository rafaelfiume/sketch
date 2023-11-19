package org.fiume.sketch.load

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import io.gatling.core.Predef.*
import io.gatling.http.Predef.{http, *}
import org.fiume.sketch.acceptance.testkit.AuthenticationContext
import org.fiume.sketch.shared.auth0.testkit.UserGens
import org.fiume.sketch.shared.testkit.FileContentContext

import scala.concurrent.duration.*

class DocumentsSimulation extends Simulation with AuthenticationContext with DocumentsSimulationContext:

  val docName = "Nicolas_e_Joana"
  val docDesc = "Meus amores <3"
  val pathToFile = "meus-fofinhos.jpg"
  val owner = UserGens.userIds.sample.get.asString()
  given IORuntime = IORuntime.global
  val bytes = bytesFrom[IO](pathToFile).compile.toVector.map(_.toArray).unsafeRunSync()

  val authenticated = loginAndGetAuthenticatedUser().unsafeRunSync()
  val authorizationHeader = authenticated.authorization

  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
    .contentTypeHeader("multipart/form-data")
    .header("Authorization", authorizationHeader.credentials.toString)

  val metadataPayload = payload(docName, docDesc, owner).unsafeRunSync()
  val scn = scenario("DocumentsRoutes")
    .exec(
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
    .exec(
      http("download document")
        .get("/documents/${documentId}")
        .check(status.is(200))
    )
    .exitHereIfFailed
    .exec(
      http("delete document")
        .delete("/documents/${documentId}")
        .check(status.is(204))
    )

  setUp(
    scn.inject(
      constantUsersPerSec(5).during(10.seconds), // Stay at 5 users for 10 secs
      rampUsersPerSec(1).to(10).during(30.seconds) // Ramp up to 10 users in 30 secs
    )
  ).protocols(httpProtocol)

trait DocumentsSimulationContext extends FileContentContext:
  // TODO Duplicated see acc test
  def payload(name: String, description: String, owner: String): IO[String] =
    stringFrom[IO]("document/metadata.request.json")
      .map { json =>
        json
          .replace("SuCJzND", name)
          .replace("19ZHxAyyBQ4olkFv7YUGuAGq7A5YWzPIfZAd703rMzCO8uvua2XliMf6dzw", description)
          .replace("7a21d5bc-9e3a-4c1b-bf6c-33cc9926c3ac", owner)
      }
      .use(IO.pure)

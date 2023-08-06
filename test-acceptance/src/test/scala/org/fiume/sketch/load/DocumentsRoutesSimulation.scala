package org.fiume.sketch.load

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import io.gatling.core.Predef.*
import io.gatling.http.Predef.{http, *}
import org.fiume.sketch.shared.testkit.FileContentContext

import scala.concurrent.duration.*

class DocumentsRoutesSimulation extends Simulation with FileContentContext with DocumentsRoutesSimulationContext:

  val docName = "Nicolas_e_Joana"
  val docDesc = "Meus amores <3"
  val pathToFile = "meus-fofinhos.jpg"
  given IORuntime = IORuntime.global
  val bytes = bytesFrom[IO](pathToFile).compile.toVector.map(_.toArray).unsafeRunSync()

  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
    .contentTypeHeader("multipart/form-data")

  val scn = scenario("DocumentsRoutes")
    .exec(
      http("create document")
        .post("/documents")
        .header("Content-Type", "multipart/form-data")
        .bodyParts(
          StringBodyPart("metadata", payload(docName, docDesc)),
          ByteArrayBodyPart("bytes", bytes)
            .fileName(docName)
            .contentType("application/octet-stream")
        )
        .asMultipartForm
        .check(status.is(201))
        .check(jsonPath("$.uuid").saveAs("documentId"))
    )
    .exec(
      http("get document")
        .get("/documents/${documentId}")
        .check(status.is(200))
    )
    .exec(
      http("delete document")
        .delete("/documents/${documentId}")
        .check(status.is(204))
    )

  setUp(
    scn.inject(
      constantUsersPerSec(5).during(10.seconds), // Stay at 5 users for 10 secs
      rampUsersPerSec(1).to(20).during(60.seconds) // Ramp up to 20 users in 60 secs
    )
  ).protocols(httpProtocol)

// TODO: duplicated
trait DocumentsRoutesSimulationContext:
  // TODO Load from storage/src/test/resources/storage/contract/http/document.metadata.json
  def payload(name: String, description: String): String =
    s"""
       |{
       |  "name": "$name",
       |  "description": "$description"
       |}
      """.stripMargin

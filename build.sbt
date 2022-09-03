import com.typesafe.sbt.packager.docker._
import sbt.{enablePlugins, IO}
import scala.util.Properties

val ScalaVersion = "3.1.3"

enablePlugins(GitVersioning)

ThisBuild / envFileName := "service/environments/dev.env"

// scala fix organise imports config
ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"
// scalafix semantic db config
inThisBuild(
  List(
    scalaVersion := "3.1.3",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

lazy val buildNumber: String =
  Properties
    .envOrNone("BUILD_NUMBER")
    .map(buildNumber => s".${System.currentTimeMillis()}.$buildNumber")
    .getOrElse("dev")

lazy val commonSettings = Seq(
  scalaVersion := ScalaVersion,
  organization := "org.fiume",
  version := buildNumber,
  run / fork := true
)

val IntegrationTests = config("it").extend(Test)

lazy val service =
  (project in file("service"))
    .enablePlugins(JavaAppPackaging)
    .settings(commonSettings: _*)
    .configs(IntegrationTests)
    .settings(
      inConfig(IntegrationTests)(Defaults.testSettings ++ scalafixConfigSettings(IntegrationTests) ++ Seq(fork := true)): _*
    )
    .settings(
      Test / envFileName := "service/environments/dev.env",
      Test / envVars := (Test / envFromFile).value
    )
    .settings(
      name := "sketch",
      libraryDependencies ++= Seq(
        Dependency.cats,
        Dependency.catsEffect,
        Dependency.circeCore,
        Dependency.circeGeneric,
        Dependency.circeParser,
        Dependency.ciris,
        Dependency.doobieCirce,
        Dependency.doobieCore,
        Dependency.doobiePostgres,
        Dependency.doobieHikari,
        Dependency.flyway,
        Dependency.http4sBlazeClient,
        Dependency.http4sBlazeServer,
        Dependency.http4sCirce,
        Dependency.http4sDsl,
        Dependency.log4catsCore,
        Dependency.log4catsSlf4j,
        Dependency.slf4jSimple,
        Dependency.munit % "test,it",
        Dependency.munitCatsEffect % "test,it",
        Dependency.munitScalaCheckEffect % "test,it",
        Dependency.munitTestcontainersScala % "it",
        Dependency.munitTestcontainersScalaPG % "it"
      ),
      Compile / resourceGenerators += Def.task {
        val file = (Compile / resourceManaged).value / "sketch.version"
        val lines = Seq(
          version.value,
          git.gitHeadCommit.value.getOrElse("no commit hash")
        )
        IO.writeLines(file, lines)
        Seq(file)
      },
      Compile / mainClass := Some("org.fiume.sketch.app.Main"),
      dockerBaseImage := "openjdk:17-jdk-slim",
      dockerCommands += Cmd("USER", "root"),
      dockerCommands ++= Seq(
        Cmd("RUN", "apt-get update -y && apt-get install -y curl")
      ),
      dockerCommands += Cmd("USER", "1001:0")
    )

lazy val sketch =
  (project in file("."))
    .settings(commonSettings: _*)
    .aggregate(service)

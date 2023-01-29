import com.typesafe.sbt.packager.docker._
import sbt.{enablePlugins, IO}
import scala.util.Properties

val ScalaVersion = "3.2.1"

enablePlugins(GitVersioning)

// scala fix organise imports config
ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"
// scalafix semantic db config
inThisBuild(
  List(
    scalaVersion := "3.2.1",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

lazy val branchVersion: Option[String] =
  Properties
    .envOrNone("CIRCLE_BRANCH")
    .map(name => if (name == "main") "" else s"$name.")

// e.g. version 105 (main) or branch.105 (branch) or snapshot (workstation)
lazy val buildNumber: String =
  Properties
    .envOrNone("CIRCLE_BUILD_NUM")
    .flatMap(number => branchVersion.map(branch => s"$branch$number"))
    .getOrElse("snapshot")

lazy val commonSettings = Seq(
  scalaVersion := ScalaVersion,
  organization := "org.fiume",
  version := buildNumber,
  fork := true
)

val IntegrationTests = config("it").extend(Test)

lazy val service =
  (project in file("service"))
    .enablePlugins(JavaAppPackaging)
    .disablePlugins(plugins.JUnitXmlReportPlugin) // see https://www.scala-sbt.org/1.x/docs/Testing.html
    .settings(commonSettings: _*)
    .configs(IntegrationTests)
    .settings(
      inConfig(IntegrationTests)(Defaults.testSettings ++ scalafixConfigSettings(IntegrationTests))
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
        Dependency.http4sCirce,
        Dependency.http4sDsl,
        Dependency.http4sEmberClient,
        Dependency.http4sEmberServer,
        Dependency.log4catsCore,
        Dependency.log4catsSlf4j,
        Dependency.slf4jSimple,
        Dependency.monocleCore,
        Dependency.monocleMacro,
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
      dockerCommands += Cmd("USER", "1001:0"),
      dockerUpdateLatest := true,
      dockerUsername := Some("rafaelfiume"),
      dockerRepository := Some("docker.io")
    )

lazy val acceptance =
  (project in file("acceptance"))
    .disablePlugins(plugins.JUnitXmlReportPlugin)
    .settings(commonSettings: _*)
    .settings(
      name := "acceptance",
      libraryDependencies ++= Seq(
        Dependency.cats,
        Dependency.catsEffect,
        Dependency.circeCore,
        Dependency.http4sCirce,
        Dependency.http4sEmberClient,
        Dependency.munit % Test,
        Dependency.munitCatsEffect % Test,
        Dependency.munitScalaCheckEffect % Test
      )
    )

lazy val sketch =
  (project in file("."))
    .settings(commonSettings: _*)
    .aggregate(service)

import org.scalajs.linker.interface.ModuleSplitStyle
lazy val sketchUI =
  (project in file("sketchUI"))
    .enablePlugins(ScalaJSPlugin)
    .settings(commonSettings: _*)
    .settings(
      name := "sketchUI",
      scalaJSUseMainModuleInitializer := true,
      scalaJSLinkerConfig ~= {
        _.withModuleKind(ModuleKind.ESModule)
          .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("sketchUI")))
      },
      libraryDependencies ++= Seq(
        "org.scala-js" %%% "scalajs-dom" % "2.2.0",
      )
    )
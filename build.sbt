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

import org.scalajs.linker.interface.ModuleSplitStyle
lazy val frontend =
  (project in file("frontend"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(sharedDomain.js)
    .settings(scalaVersion := ScalaVersion)
    // `test / test` tasks in a Scala.js project require `test / fork := false`.
    .settings(fork := false)
    .settings(
      name := "frontend",
      scalaJSUseMainModuleInitializer := true,
      scalaJSLinkerConfig ~= {
        _.withModuleKind(ModuleKind.ESModule)
          .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("frontend")))
      },
      libraryDependencies ++= Seq(
        "com.raquo"                       %%% "laminar"                         % "0.14.2",
        "io.circe"                        %%% "circe-core"                      % "0.14.3",
        "org.scala-js"                    %%% "scalajs-dom"                     % "2.2.0",
        "com.softwaremill.sttp.client3"   %%% "core"                            % "3.8.11",
        "com.softwaremill.sttp.client3"   %%% "circe"                           % "3.8.11",

        //// Test Dependencies
        "org.scalameta"                   %%% "munit"                           % "0.7.29"        % Test
      )
    )

lazy val service =
  (project in file("service")) // TODO replace 'project in file'
    .dependsOn(sharedDomain.jvm)
    .dependsOn(storage)
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
        Dependency.doobieCore, // TODO Should we have any dependencies on Doobie here
        Dependency.doobieHikari,
        Dependency.http4sCirce,
        Dependency.http4sDsl,
        Dependency.http4sEmberClient,
        Dependency.http4sEmberServer,
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
      dockerCommands += Cmd("USER", "1001:0"),
      dockerUpdateLatest := true,
      dockerUsername := Some("rafaelfiume"),
      dockerRepository := Some("docker.io")
    )

/*
 * sharedDomain == contract between modules/services, for instance
 * `frontend -> storage`, `frontend -> sketch`, `sketch -> storage`, etc.
 *
 * I.e. be cautious with breaking changes
 */
lazy val sharedDomain =
  crossProject(JSPlatform, JVMPlatform).in(file("shared-domain"))
    .disablePlugins(plugins.JUnitXmlReportPlugin)
    .settings(commonSettings: _*)
    .settings(
      name := "shared-domain",
      libraryDependencies ++= Seq(
        Dependency.cats,
        Dependency.circeCore,
        Dependency.fs2Core
      )
    )
    .jvmSettings(
      libraryDependencies ++= Seq(
        Dependency.fs2Core
      )
    )
    .jsSettings(
      fork := false
    )

lazy val sketch =
  (project in file("."))
    .settings(commonSettings: _*)
    .aggregate(frontend)
    .aggregate(service)
    .aggregate(sharedDomain.js, sharedDomain.jvm)
    .aggregate(storage)

lazy val storage =
  (project in file("storage"))
    .dependsOn(sharedDomain.jvm)
    .disablePlugins(plugins.JUnitXmlReportPlugin)
    .settings(commonSettings: _*)
    .configs(IntegrationTests)
    .settings(
      inConfig(IntegrationTests)(Defaults.testSettings ++ scalafixConfigSettings(IntegrationTests))
    )
    .settings(
      name := "storage",
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
        Dependency.fs2Core,
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
      )
    )

lazy val testAcceptance =
  (project in file("test-acceptance"))
    .disablePlugins(plugins.JUnitXmlReportPlugin)
    .settings(commonSettings: _*)
    .settings(
      name := "test-acceptance",
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

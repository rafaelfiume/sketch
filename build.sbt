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

// e.g. version `105` (main), `branch.105` (feature branch) or `snapshot` (local)
lazy val buildNumber: String = {
  lazy val branchVersion = Properties.envOrNone("CIRCLE_BRANCH").map { name => if (name == "main") "" else s"$name." }
  Properties
    .envOrNone("CIRCLE_BUILD_NUM")
    .flatMap(number => branchVersion.map(branch => s"$branch$number"))
    .getOrElse("snapshot")
}

lazy val commonSettings = Seq(
  scalaVersion := ScalaVersion,
  organization := "org.fiume",
  version := buildNumber,
  fork := true
)

val IntegrationTests = config("it").extend(Test)

import org.scalajs.linker.interface.ModuleSplitStyle
lazy val frontend =
   project.in(file("frontend"))
     .enablePlugins(ScalaJSPlugin)
     .dependsOn(sharedComponents.js)
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
         "org.scala-js"                    %%% "scalajs-dom"                     % "2.6.0",
         "com.softwaremill.sttp.client3"   %%% "core"                            % "3.8.11",
         "com.softwaremill.sttp.client3"   %%% "circe"                           % "3.8.11",

         //// Test Dependencies
         "org.scalameta"                   %%% "munit"                           % "0.7.29"        % Test
       )
     )

lazy val service =
   project.in(file("service"))
     .dependsOn(sharedComponentsJvm)
     .dependsOn(storage)
     .dependsOn(sharedTestComponents % Test)
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
         Dependency.doobieCore,
         Dependency.http4sCirce,
         Dependency.http4sDsl,
         Dependency.http4sEmberClient,
         Dependency.http4sEmberServer,
         Dependency.log4catsCore,
         Dependency.log4catsSlf4j,
         Dependency.slf4jSimple,
         Dependency.munit % "test,it",
         Dependency.munitCatsEffect % "test,it",
         Dependency.munitScalaCheck % "test,it",
         Dependency.munitScalaCheckEffect % "test,it",
         Dependency.munitTestcontainersScala % "it",
         Dependency.munitTestcontainersScalaPG % "it"
       ),
       Compile / resourceGenerators += Def.task {
         val versionFile = (Compile / resourceManaged).value / "sketch.version"
         val versionLines = Seq(
           version.value,
           git.gitHeadCommit.value.getOrElse("no commit hash")
         )
         IO.writeLines(versionFile, versionLines)
         Seq(versionFile)
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
 * sharedComponents == contract between modules/services, for instance
 * `frontend -> storage`, `frontend -> sketch`, `sketch -> storage`, etc.
 *
 * I.e. be cautious with breaking changes
 */
lazy val sharedComponents =
  crossProject(JSPlatform, JVMPlatform).in(file("shared-components"))
    .disablePlugins(plugins.JUnitXmlReportPlugin)
    .settings(commonSettings: _*)
    .configs(IntegrationTest)
    .settings(Defaults.itSettings: _*)
    .settings(
      name := "shared-components",
      libraryDependencies ++= Seq(
        Dependency.cats,
        Dependency.circeCore,
        Dependency.fs2Core
      )
    )
    .jvmSettings(
      libraryDependencies ++= Seq(
        Dependency.circeParser,
        Dependency.fs2Core,
        Dependency.munit % "test,it",
        Dependency.munitCatsEffect % "test,it",
        Dependency.munitScalaCheck % "test,it",
        Dependency.munitScalaCheckEffect % "test,it"
      )
    )
    // TODO causes "Referring to non-existent class org.scalajs.testing.bridge.Bridge" error
    //.jsSettings(inConfig(IntegrationTest)(ScalaJSPlugin.testConfigSettings): _*)
    .jsSettings(
      fork := false
    )

lazy val sharedComponentsJvm =
  sharedComponents.jvm.dependsOn(sharedTestComponents % Test)

/*
 * Shared for backend (jvm) only.
 * It might be necessary separated shared dependencies for jvm and js.
 * For instance, FileContentContext needs to be platform specific.
 * 
 * Don't include any domain specific class in this module,
 * for instance a dependency on `sharedComponents`
 * (i.e. it is a domain agnostic module/lib).
 */
lazy val sharedTestComponents =
   project.in(file("shared-test-components"))
     .disablePlugins(plugins.JUnitXmlReportPlugin)
     .settings(commonSettings: _*)
     .settings(
       name := "shared-test-components",
       libraryDependencies ++= Seq(
         Dependency.cats,
         Dependency.catsEffect,
         Dependency.fs2Core,
         Dependency.fs2Io,
         Dependency.http4sCirce,
         Dependency.http4sEmberClient,
         Dependency.munit,
         Dependency.munitScalaCheck
       )
     )

lazy val sketch =
   project.in(file("."))
    .settings(commonSettings: _*)
    .aggregate(frontend)
    .aggregate(service)
    .aggregate(sharedComponentsJvm)
    .aggregate(sharedComponents.js)
    .aggregate(sharedTestComponents)
    .aggregate(storage)

lazy val storage =
   project.in(file("storage"))
     .dependsOn(sharedComponentsJvm)
     .dependsOn(sharedTestComponents % Test)
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
         Dependency.munitScalaCheck % "test,it",
         Dependency.munitScalaCheckEffect % "test,it",
         Dependency.munitTestcontainersScala % "it",
         Dependency.munitTestcontainersScalaPG % "it"
       )
     )

lazy val testAcceptance =
   project.in(file("test-acceptance"))
     .dependsOn(sharedTestComponents % Test)
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
         Dependency.munitCatsEffect % Test
       )
     )

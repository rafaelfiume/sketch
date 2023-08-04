import com.typesafe.sbt.packager.docker._
import sbt.{enablePlugins, IO}
import scala.util.Properties

val ScalaVersion = "3.3.0"

enablePlugins(GitVersioning)

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"
inThisBuild(
  List(
    scalaVersion := "3.3.0",
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

// TODO IntegrationTests has been deprecated in sbt 1.9.0
// See https://eed3si9n.com/sbt-1.9.0
val IntegrationTests = config("it").extend(Test)

lazy val auth0 =
   project.in(file("auth0"))
     .dependsOn(sharedAuth0 % "test->test;compile->compile")
     .dependsOn(sharedTestComponents % Test)
     //.dependsOn(storage) // TODO Not yet....
     .disablePlugins(plugins.JUnitXmlReportPlugin)
     .settings(commonSettings: _*)
     .configs(IntegrationTests)
     .settings(
       inConfig(IntegrationTests)(Defaults.testSettings ++ scalafixConfigSettings(IntegrationTests))
     )
     .settings(
       name := "auth0",
       libraryDependencies ++= Seq(
         Dependency.bouncycastle,
         Dependency.cats,
         Dependency.catsEffect,
         Dependency.circeCore,
         Dependency.circeGeneric,
         Dependency.circeParser,
         Dependency.ciris,
         Dependency.fs2Core,
         Dependency.http4sCirce,
         Dependency.http4sDsl,
         Dependency.http4sEmberServer,
         Dependency.jwtCirce,
         Dependency.log4catsCore,
         Dependency.log4catsSlf4j,
         Dependency.slf4jSimple,
         Dependency.munit % "test,it",
         Dependency.munitCatsEffect % "test,it",
         Dependency.munitScalaCheck % "test,it",
         Dependency.munitScalaCheckEffect % "test,it"
       )
     )

lazy val service =
   project.in(file("service"))
     .dependsOn(sharedComponents)
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

lazy val sharedAuth0 =
    project.in(file("shared-auth0"))
    .dependsOn(sharedComponents)
    .dependsOn(sharedTestComponents % Test)
    .disablePlugins(plugins.JUnitXmlReportPlugin)
    .settings(commonSettings: _*)
    .configs(IntegrationTest)
    .settings(Defaults.itSettings: _*)
    .settings(
      name := "shared-auth0",
      libraryDependencies ++= Seq(
        Dependency.jbcrypt,
        Dependency.cats,
        Dependency.circeCore,
        Dependency.circeParser,
        Dependency.fs2Core,
        Dependency.munit % "test,it",
        Dependency.munitCatsEffect % "test,it",
        Dependency.munitScalaCheck % "test,it",
        Dependency.munitScalaCheckEffect % "test,it"
      )
    )

lazy val sharedComponents =
    project.in(file("shared-components"))
    .dependsOn(sharedTestComponents % Test)
    .disablePlugins(plugins.JUnitXmlReportPlugin)
    .settings(commonSettings: _*)
    .configs(IntegrationTest)
    .settings(Defaults.itSettings: _*)
    .settings(
      name := "shared-components",
      libraryDependencies ++= Seq(
        Dependency.cats,
        Dependency.circeCore,
        Dependency.circeParser,
        Dependency.fs2Core,
        Dependency.http4sCirce,
        Dependency.http4sDsl,
        Dependency.munit % "test,it",
        Dependency.munitCatsEffect % "test,it",
        Dependency.munitScalaCheck % "test,it",
        Dependency.munitScalaCheckEffect % "test,it"
      )
    )

/*
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
         Dependency.circeParser,
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
    .aggregate(auth0)
    .aggregate(service)
    .aggregate(sharedAuth0)
    .aggregate(sharedComponents)
    .aggregate(sharedTestComponents)
    .aggregate(storage)

lazy val storage =
   project.in(file("storage"))
     .dependsOn(sharedAuth0 % "test->test;compile->compile")
     .dependsOn(sharedComponents)
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
       )
     )

lazy val testAcceptance =
   project.in(file("test-acceptance"))
     .dependsOn(sharedTestComponents % Test)
     .disablePlugins(plugins.JUnitXmlReportPlugin)
     .enablePlugins(GatlingPlugin)
     .settings(commonSettings: _*)
     .settings(
       name := "test-acceptance",
       libraryDependencies ++= Seq(
         Dependency.cats % Test,
         Dependency.catsEffect % Test,
         Dependency.circeCore % Test,
         Dependency.http4sCirce % Test,
         Dependency.http4sEmberClient % Test,
         Dependency.gatlingHighcharts % Test,
         Dependency.gatlingTestFramework % Test,
         Dependency.munit % Test,
         Dependency.munitCatsEffect % Test
       )
     )

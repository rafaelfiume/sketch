import com.typesafe.sbt.packager.docker._
import sbt.{enablePlugins, IO}
import scala.util.Properties

val ScalaVersion = "3.7.1"

enablePlugins(GitVersioning)

inThisBuild(
  List(
    scalaVersion := "3.7.1",
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
  scalacOptions := scalacOptions.value.filterNot(_ == "-source:3.0-migration") ++ Seq("-source:future", "-Wunused:all"),
  fork := true
)

// IntegrationTests has been deprecated in sbt 1.9.0
// See https://eed3si9n.com/sbt-1.9.0
val IntegrationTests = config("it").extend(Test)

lazy val auth =
  project
    .in(file("auth"))
    .dependsOn(sharedAccessControl % "compile->compile;test->test")
    .dependsOn(sharedAuth % "compile->compile;test->test")
    .dependsOn(sharedAccountManagement)
    .dependsOn(sharedComponents % "compile->compile;test->test")
    .dependsOn(sharedTestComponents % Test)
    .dependsOn(storage)
    .dependsOn(testContracts % "test->test")
    .disablePlugins(plugins.JUnitXmlReportPlugin)
    .settings(commonSettings: _*)
    .settings(
      name := "auth",
      libraryDependencies ++= Seq(
        Dependency.jbcrypt,
        Dependency.bouncycastle,
        Dependency.catsEffect,
        Dependency.circeGeneric,
        Dependency.http4sEmberServer,
        Dependency.jwtCirce,
        Dependency.http4sEmberClient % Test,
        Dependency.munit % Test,
        Dependency.munitCatsEffect % Test,
        Dependency.munitScalaCheck % Test,
        Dependency.munitScalaCheckEffect % Test
      )
    )

lazy val service =
  project
    .in(file("service"))
    .dependsOn(auth)
    .dependsOn(sharedAccessControl % "compile->compile;test->test")
    .dependsOn(sharedAuth % "compile->compile;test->test")
    .dependsOn(sharedComponents % "compile->compile;test->test")
    .dependsOn(sharedDomain % "compile->compile;test->test")
    .dependsOn(sharedTestComponents % Test)
    .dependsOn(storage)
    .dependsOn(testContracts % "test->test")
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
        Dependency.catsEffect,
        Dependency.circeGeneric,
        Dependency.ciris,
        Dependency.http4sDsl,
        Dependency.http4sEmberClient,
        Dependency.http4sEmberServer,
        Dependency.log4catsSlf4j,
        Dependency.slf4jSimple,
        Dependency.munit % "test,it",
        Dependency.munitCatsEffect % "test,it",
        Dependency.munitScalaCheck % "test,it",
        Dependency.munitScalaCheckEffect % "test,it"
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
      dockerBaseImage := "openjdk:22-jdk-slim",
      dockerCommands += Cmd("USER", "root"),
      dockerCommands ++= Seq(
        Cmd("RUN", "apt-get update -y && apt-get install -y curl")
      ),
      dockerCommands += Cmd("USER", "1001:0"),
      dockerUpdateLatest := true,
      dockerUsername := Some("rafaelfiume"),
      dockerRepository := Some("docker.io")
    )

lazy val sharedAccessControl =
  project
    .in(file("shared-access-control"))
    .dependsOn(sharedAuth % "compile->compile;test->test")
    .dependsOn(sharedComponents)
    .dependsOn(sharedTestComponents % Test)
    .disablePlugins(plugins.JUnitXmlReportPlugin)
    .settings(commonSettings: _*)
    .settings(
      name := "shared-access-control",
      libraryDependencies ++= Seq(
        Dependency.catsEffect,
        Dependency.munit % Test,
        Dependency.munitCatsEffect % Test,
        Dependency.munitScalaCheck % Test,
        Dependency.munitScalaCheckEffect % Test
      )
    )

lazy val sharedAuth =
  project
    .in(file("shared-auth"))
    .dependsOn(sharedComponents)
    .dependsOn(sharedTestComponents % Test)
    .disablePlugins(plugins.JUnitXmlReportPlugin)
    .settings(commonSettings: _*)
    .settings(
      name := "shared-auth",
      libraryDependencies ++= Seq(
        Dependency.jbcrypt,
        Dependency.http4sEmberClient,
        Dependency.munitCatsEffect % Test,
        Dependency.munitScalaCheckEffect % Test
      )
    )

lazy val sharedAccountManagement =
  project
    .in(file("shared-account-management"))
    .dependsOn(sharedAccessControl)
    .dependsOn(sharedAuth % "compile->compile;test->test")
    .disablePlugins(plugins.JUnitXmlReportPlugin)
    .settings(commonSettings: _*)
    .configs(IntegrationTests)
    .settings(
      inConfig(IntegrationTests)(Defaults.testSettings ++ scalafixConfigSettings(IntegrationTests))
    )
    .settings(
      name := "shared-account-management",
      libraryDependencies ++= Seq(
        Dependency.circeGeneric,
        Dependency.http4sEmberClient,
        Dependency.http4sEmberServer % "it",
        Dependency.munitCatsEffect % Test,
        Dependency.munitScalaCheckEffect % Test
      )
    )

lazy val sharedComponents =
  project
    .in(file("shared-components"))
    .dependsOn(sharedTestComponents % Test)
    .dependsOn(testContracts % "test->test")
    .disablePlugins(plugins.JUnitXmlReportPlugin)
    .settings(commonSettings: _*)
    .settings(
      name := "shared-components",
      libraryDependencies ++= Seq(
        Dependency.cats,
        Dependency.circeCore,
        Dependency.circeGeneric,
        Dependency.circeParser,
        Dependency.http4sCirce,
        Dependency.http4sDsl,
        Dependency.log4catsSlf4j,
        Dependency.slf4jSimple,
        Dependency.catsLaws % Test,
        Dependency.munit % Test,
        Dependency.munitCatsEffect % Test,
        Dependency.munitDiscipline % Test,
        Dependency.munitScalaCheck % Test,
        Dependency.munitScalaCheckEffect % Test
      )
    )

lazy val sharedDomain =
  project
    .in(file("shared-domain"))
    .dependsOn(sharedAuth % "compile->compile;test->test")
    .dependsOn(sharedComponents)
    .dependsOn(sharedTestComponents % Test)
    .disablePlugins(plugins.JUnitXmlReportPlugin)
    .settings(commonSettings: _*)
    .settings(
      name := "shared-domain",
      libraryDependencies ++= Seq(
        Dependency.fs2Core
      )
    )

/*
 * Don't include any domain specific class in this module,
 * for instance a dependency on `sharedComponents`.
 * I.e, this is a domain agnostic module/lib.
 */
lazy val sharedTestComponents =
  project
    .in(file("shared-test-components"))
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
        Dependency.http4sEmberServer,
        Dependency.log4catsSlf4j,
        Dependency.munit,
        Dependency.munitScalaCheck
      )
    )

lazy val sketch =
  project
    .in(file("."))
    .settings(commonSettings: _*)
    .aggregate(auth)
    .aggregate(service)
    .aggregate(sharedAccessControl)
    .aggregate(sharedAuth)
    .aggregate(sharedAccountManagement)
    .aggregate(sharedComponents)
    .aggregate(sharedDomain)
    .aggregate(sharedTestComponents)
    .aggregate(storage)

lazy val storage =
  project
    .in(file("storage"))
    .dependsOn(sharedAccessControl % "compile->compile;test->test")
    .dependsOn(sharedAuth % "compile->compile;test->test")
    .dependsOn(sharedComponents)
    .dependsOn(sharedDomain % "compile->compile;test->test")
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
        Dependency.catsEffect,
        Dependency.ciris,
        Dependency.doobieCirce,
        Dependency.doobieCore,
        Dependency.doobiePostgres,
        Dependency.doobieHikari,
        Dependency.flyway,
        Dependency.flywayPostgres,
        Dependency.log4catsSlf4j,
        Dependency.munit % "test,it",
        Dependency.munitCatsEffect % "test,it",
        Dependency.munitScalaCheck % "test,it",
        Dependency.munitScalaCheckEffect % "test,it",
        Dependency.munitTestcontainersScala % "it",
        Dependency.munitTestcontainersScalaPG % "it"
      )
    )

lazy val testAcceptance =
  project
    .in(file("test-acceptance"))
    .dependsOn(auth % Test)
    .dependsOn(sharedAccountManagement % "test->test")
    .dependsOn(sharedTestComponents % Test)
    .dependsOn(testContracts % "test->test")
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
        Dependency.gatlingHighcharts % Test,
        Dependency.gatlingTestFramework % Test,
        Dependency.logstashLogbackEncoder % Test,
        Dependency.munit % Test,
        Dependency.munitCatsEffect % Test
      )
    )

lazy val testContracts =
  project
    .in(file("test-contracts"))
    .disablePlugins(plugins.JUnitXmlReportPlugin)
    .settings(commonSettings: _*)
    .settings(
      name := "test-contracts"
    )

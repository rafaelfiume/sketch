import sbt.{enablePlugins, IO}
import scala.util.Properties

val ScalaVersion = "3.1.1"

lazy val buildNumber: String =
  Properties
    .envOrNone("BUILD_NUMBER")
    .map(buildNumber => s".${System.currentTimeMillis()}.$buildNumber")
    .getOrElse("dev")

lazy val commonSettings = Seq(
  scalaVersion := ScalaVersion,
  organization := "org.fiume",
  version := buildNumber,
  run / fork := true,
  scalacOptions := scalacOptions.value.filterNot(_ == "-source:3.0-migration") :+ "-source:future"
)

val IntegrationTests = config("it").extend(Test)

lazy val service =
  (project in file("service"))
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
      assembly / mainClass := Some("org.fiume.sketch.app.Main"),
      assembly / assemblyMergeStrategy := {
        case file if Assembly.isConfigFile(file)     => MergeStrategy.concat
        case PathList("buildinfo", _*)               => MergeStrategy.discard
        case PathList("META-INF", "versions", _ @_*) => MergeStrategy.deduplicate
        case PathList("META-INF", "services", _ @_*) => MergeStrategy.deduplicate
        case PathList("META-INF", _*)                => MergeStrategy.discard
        case f if f.endsWith("LICENSE")              => MergeStrategy.discard
        case f if f.endsWith("NOTICE")               => MergeStrategy.discard
        case f if f.endsWith("io.netty.versions.properties") =>
          MergeStrategy.discard
        case f if f.endsWith("module-info.class") => MergeStrategy.discard
        case _                                    => MergeStrategy.deduplicate
      },
      assembly / test := Def
        .sequential(Test / test, IntegrationTest / test)
        .value
    )

lazy val sketch =
  (project in file("."))
    .settings(commonSettings: _*)
    .aggregate(service)

enablePlugins(GitVersioning)

ThisBuild / envFileName := "service/environments/dev.env"

ThisBuild / parallelExecution := false

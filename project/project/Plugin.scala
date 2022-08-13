import sbt._

// format: off
object Plugin {
  private object Version {
    val Assembly        = "0.14.6"
    val DependencyGraph = "0.9.2"
    val DotEnv          = "2.1.204"
    val Git             = "1.0.2"
    val Scalafix        = "0.10.1"
    val ScalaFmt        = "2.4.6"
    val Tpolecat        = "0.4.1"
  }

  val Assembly        = "com.eed3si9n"              %  "sbt-assembly"         % Version.Assembly
  val DependencyGraph = "net.virtual-void"          %  "sbt-dependency-graph" % Version.DependencyGraph
  val DotEnv          = "au.com.onegeek"            %% "sbt-dotenv"           % Version.DotEnv
  val Git             = "com.typesafe.sbt"          %  "sbt-git"              % Version.Git
  val Scalafix        = "ch.epfl.scala"             %  "sbt-scalafix"         % Version.Scalafix
  val ScalaFmt        = "org.scalameta"             %  "sbt-scalafmt"         % Version.ScalaFmt
  val Tpolecat        = "io.github.davidgregory084" %  "sbt-tpolecat"         % Version.Tpolecat
}

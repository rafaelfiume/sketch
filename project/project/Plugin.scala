import sbt._

// format: off
object Plugin {
  private object Version {
    val Assembly        = "0.14.6"
    val DependencyGraph = "0.9.0"
    val DotEnv          = "2.1.204"
    val Git             = "2.0.0"
    val Scalafix        = "0.9.34"
    val ScalaFmt        = "2.4.6"
    val Tpolecat        = "0.1.22"
  }

  val Assembly        = "com.eed3si9n"              %  "sbt-assembly"         % Version.Assembly
  val DependencyGraph = "net.virtual-void"          %  "sbt-dependency-graph" % Version.DependencyGraph
  val DotEnv          = "au.com.onegeek"            %% "sbt-dotenv"           % Version.DotEnv
  val Git             = "com.github.sbt"          %  "sbt-git"              % Version.Git
  val Scalafix        = "ch.epfl.scala"             %  "sbt-scalafix"         % Version.Scalafix
  val ScalaFmt        = "org.scalameta"             %  "sbt-scalafmt"         % Version.ScalaFmt
  val Tpolecat        = "io.github.davidgregory084" %  "sbt-tpolecat"         % Version.Tpolecat
}

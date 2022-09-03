import sbt._

// format: off
object Plugin {
  private object Version {
    val DependencyGraph    = "0.9.2"
    val DotEnv             = "3.0.0"
    val Git                = "2.0.0"
    val SbtNativePackager  = "1.9.11"
    val Scalafix           = "0.10.1"
    val ScalaFmt           = "2.4.6"
    val Tpolecat           = "0.4.1"
  }

  // TODO Use sbt-explicit-dependencies ?
  val DependencyGraph   = "net.virtual-void"           %  "sbt-dependency-graph"  % Version.DependencyGraph
  val DotEnv            = "nl.gn0s1s"                  %% "sbt-dotenv"            % Version.DotEnv
  val Git               = "com.github.sbt"             %  "sbt-git"               % Version.Git
  val SbtNativePackager = "com.github.sbt"             %% "sbt-native-packager"   % Version.SbtNativePackager
  val Scalafix          = "ch.epfl.scala"              %  "sbt-scalafix"          % Version.Scalafix
  val ScalaFmt          = "org.scalameta"              %  "sbt-scalafmt"          % Version.ScalaFmt
  val Tpolecat          = "io.github.davidgregory084"  %  "sbt-tpolecat"          % Version.Tpolecat
}

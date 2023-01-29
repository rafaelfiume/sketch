addSbtPlugin(Plugin.DependencyGraph)
addSbtPlugin(Plugin.Git)
addSbtPlugin(Plugin.SbtNativePackager)
addSbtPlugin(Plugin.Scalafix)
addSbtPlugin(Plugin.ScalaFmt)
addSbtPlugin(Plugin.Tpolecat)

// Frontend
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.10.0")

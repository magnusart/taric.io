import sbt._

import Defaults._

// Comment to get more information during initialization
logLevel := Level.Warn

resolvers += Classpaths.typesafeReleases

resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies += sbtPluginExtra(
  m = "com.github.mpeltonen" % "sbt-idea" % "1.3.0-SNAPSHOT", // Plugin module name and version
  sbtV = "0.12",    // SBT version
  scalaV = "2.9.2"    // Scala version compiled the plugin
)

//addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.3.0-SNAPSHOT")
libraryDependencies += sbtPluginExtra(
  m = "com.typesafe.sbt" % "sbt-start-script" % "0.6.0", // Plugin module name and version
  sbtV = "0.12",    // SBT version
  scalaV = "2.9.2"    // Scala version compiled the plugin
)

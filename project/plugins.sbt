import sbt._

import Defaults._

// Comment to get more information during initialization
logLevel := Level.Warn

resolvers += Classpaths.typesafeReleases

resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

// This resolver declaration is added by default SBT 0.12.x
resolvers += "sbt-plugin-releases" at "http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"

// Revolver
addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.1")

// SBT Dependency Graph
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.3")

addSbtPlugin("com.orrsella" % "sbt-sublime" % "1.0.4")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.0.1")

//addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.3.0-SNAPSHOT")

// libraryDependencies += sbtPluginExtra(
//   m = "com.github.mpeltonen" % "sbt-idea" % "1.3.0-SNAPSHOT", // Plugin module name and version
//   sbtV = "0.12",    // SBT version
//   scalaV = "2.9.2"    // Scala version compiled the plugin
// )


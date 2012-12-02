import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "taric.io"
    val appVersion      = "1.0.0-SNAPSHOT"

    val appDependencies = Seq(
      "org.bouncycastle" % "bcprov-jdk15on" % "1.47",
      "org.bouncycastle" % "bcpkix-jdk15on" % "1.47",
      "org.bouncycastle" % "bcpg-jdk15on" % "1.47",
      "com.h2database" % "h2" % "1.3.168",
      "org.scalaquery" % "scalaquery_2.9.0-1" % "0.9.5",
      "org.scalaj" %% "scalaj-time" % "0.6",
      "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.1-seq",
      "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.1-seq"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      // Add your own project settings here      
    )

}

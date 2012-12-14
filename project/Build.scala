import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtStartScript

object TaricBuild extends Build {
  val Organization = "taric.io"
  val Version      = "0.0.1-SNAPSHOT"
  val ScalaVersion = "2.9.2"

  lazy val taric = Project(
    id = "taric",
    base = file("."),
    settings = defaultSettings ++
    Seq(SbtStartScript.stage in Compile := Unit),
    aggregate = Seq(timport)
    )

  lazy val timport = Project(
    id = "taric-import",
    base = file("taric-import"),
    dependencies = Seq(),
    settings = defaultSettings ++
    SbtStartScript.startScriptForClassesSettings ++
    Seq(libraryDependencies ++= Dependencies.akkaComponent ++
                                Dependencies.crypto ++
                                Dependencies.db ++
                                Dependencies.fileIO)
    )

  lazy val defaultSettings = Defaults.defaultSettings ++ Seq(
    resolvers ++= Seq("Typesafe Releases Repo" at "http://repo.typesafe.com/typesafe/releases/",
                      "Typesafe Snapshot Repo" at "http://repo.typesafe.com/typesafe/snapshots/",
                      "Central Repo" at "http://repo1.maven.org/maven2/",
                      "Scala-Tools Maven2 Releases Repository" at "http://scala-tools.org/repo-releases",
                      "Codahale Repo" at "http://repo.codahale.com"),

    // compile options
    scalacOptions ++= Seq("-encoding", "UTF-8", "-optimise", "-deprecation", "-unchecked"),
    javacOptions  ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),

    // disable parallel tests
    parallelExecution in Test := false
  )

  object Dependencies {
    import Dependency._
    val akkaComponent = Seq(akkaActor, akkaRemote, akkaTestKit, scalaTest, jUnit, scalaTime, scalaSTM)
    val crypto = Seq(bcprov, bcpkix, bcpg)
    val db = Seq(h2, scalaquery)
    val fileIO = Seq(scalaIoCore, scalaIoFile)
  }

  object Dependency {
      object Version {
        val Akka      = "2.0.4"
        val Scalatest = "1.6.1"
        val JUnit     = "4.5"
        val ScalaTime = "0.6"
        val ScalaSTM  = "0.6"
        val Jerkson   = "0.5.0"
        val Bouncycastle = "1.47"
        val H2        = "1.3.168"
        val ScalaQuery = "0.9.5"
        val ScalaIO = "0.4.1-seq"
      }

      // ---- Application dependencies ----
      val akkaActor   = "com.typesafe.akka"   % "akka-actor"              % Version.Akka
      val akkaRemote  = "com.typesafe.akka"   % "akka-remote"             % Version.Akka
      val akkaTestKit = "com.typesafe.akka"   % "akka-testkit"            % Version.Akka
      val scalaTime   = "org.scalaj"          % "scalaj-time_%s".format(ScalaVersion) % Version.ScalaTime
      val scalaSTM    = "org.scala-tools"    %% "scala-stm"               % Version.ScalaSTM
      val bcprov      = "org.bouncycastle" % "bcprov-jdk15on" % Version.Bouncycastle
      val bcpkix      = "org.bouncycastle" % "bcpkix-jdk15on" % Version.Bouncycastle
      val bcpg        = "org.bouncycastle" % "bcpg-jdk15on" % Version.Bouncycastle
      val h2          = "com.h2database" % "h2" % Version.H2
      val scalaquery  = "org.scalaquery" % "scalaquery_2.9.0-1" % Version.ScalaQuery
      val scalaIoCore = "com.github.scala-incubator.io" %% "scala-io-core" % Version.ScalaIO
      val scalaIoFile = "com.github.scala-incubator.io" %% "scala-io-file" % Version.ScalaIO


      // ---- Test dependencies ----
      val scalaTest   = "org.scalatest"       % "scalatest_%s".format(ScalaVersion)  % Version.Scalatest  % "test"
      val jUnit       = "junit"               % "junit"                    % Version.JUnit      % "test"
    }
}

import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtStartScript

object TaricBuild extends Build {
  val Organization = "taric.io"
  val Version      = "0.0.1-SNAPSHOT"
  val ScalaVersion = "2.10.0"

  lazy val buildSettings = Seq(
    organization := Organization,
    version      := Version,
    scalaVersion := ScalaVersion
  )

  lazy val taric = Project(
    id = "taric",
    base = file("."),
    settings = defaultSettings ++ buildSettings ++
    Seq(SbtStartScript.stage in Compile := Unit),
    aggregate = Seq(timport)
  )

  lazy val timport = Project(
    id = "taric-import",
    base = file("taric-import"),
    dependencies = Seq(),
    settings = defaultSettings ++ buildSettings ++
    SbtStartScript.startScriptForClassesSettings ++
    Seq(libraryDependencies ++= Dependencies.akkaComponent ++
                                Dependencies.crypto ++
                                //Dependencies.db ++
                                Dependencies.io)
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
    val akkaComponent = Seq(akkaActor, akkaRemote, akkaTestKit, jUnit)
    val crypto = Seq(bcprov, bcpkix, bcpg)
    val db = Seq(h2, slick)
    val io = Seq(commonsNet)
  }

  object Dependency {
      object Version {
        val Akka          = "2.1.0"
        val Scalatest     = "1.6.1"
        val JUnit         = "4.5"
        val Jerkson       = "0.5.0"
        val Bouncycastle  = "1.47"
        val H2            = "1.3.168"
        val CommonsNet    = "3.1"
        val Slick         = "1.0.0"
      }

      // ---- Application dependencies ----
      val akkaActor   = "com.typesafe.akka" %% "akka-actor"              % Version.Akka
      val akkaRemote  = "com.typesafe.akka" %% "akka-remote"             % Version.Akka
      val akkaTestKit = "com.typesafe.akka" %% "akka-testkit"            % Version.Akka
      val bcprov      = "org.bouncycastle" % "bcprov-jdk15on" % Version.Bouncycastle
      val bcpkix      = "org.bouncycastle" % "bcpkix-jdk15on" % Version.Bouncycastle
      val bcpg        = "org.bouncycastle" % "bcpg-jdk15on" % Version.Bouncycastle
      val h2          = "com.h2database" % "h2" % Version.H2
      val slick       = "com.typesafe" %% "slick" % Version.Slick
      val commonsNet  = "commons-net" % "commons-net" % Version.CommonsNet



    // ---- Test dependencies ----
      val scalaTest   = "org.scalatest"       %% "scalatest" % Version.Scalatest  % "test"
      val jUnit       = "junit"               % "junit"                    % Version.JUnit      % "test"
    }
}

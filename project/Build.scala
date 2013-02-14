import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtStartScript
import com.github.retronym.SbtOneJar

object TaricBuild extends Build {
  val Organization = "taric.io"
  val Version = "0.0.1-SNAPSHOT"
  val ScalaVersion = "2.10.0"

  lazy val buildSettings = Seq(
    organization := Organization,
    version := Version,
    scalaVersion := ScalaVersion
  )

  lazy val taric = Project(
    id = "taric",
    base = file( "." ),
    settings = defaultSettings ++ buildSettings ++
      Seq( SbtStartScript.stage in Compile := Unit ),
    aggregate = Seq( timport, parse, core )
  )

  lazy val timport = Project(
    id = "taric-import",
    base = file( "taric-import" ),
    dependencies = Seq( ),
    settings = jarSettings ++ defaultSettings ++ buildSettings ++ SbtOneJar.oneJarSettings ++
      SbtStartScript.startScriptForClassesSettings ++
      Seq(
        libraryDependencies ++= Dependencies.akkaComponent ++
          Dependencies.crypto ++
          Dependencies.io ++
          Dependencies.date ++
          Dependencies.test
      )
  )

  lazy val core = Project(
    id = "taric-core",
    base = file( "taric-core" ),
    dependencies = Seq( ),
    settings = jarSettings ++ defaultSettings ++ buildSettings ++ SbtOneJar.oneJarSettings ++
      SbtStartScript.startScriptForClassesSettings ++
      Seq(
        libraryDependencies ++=
          Dependencies.akkaComponent ++
            Dependencies.db ++
            Dependencies.test
      )
  )

  lazy val parse = Project(
    id = "taric-parse",
    base = file( "taric-parse" ),
    dependencies = Seq( ),
    settings = jarSettings ++ defaultSettings ++ buildSettings ++ SbtOneJar.oneJarSettings ++
      SbtStartScript.startScriptForClassesSettings ++
      Seq(
        libraryDependencies ++=
          Dependencies.akkaComponent ++
            Dependencies.db ++
            Dependencies.test
      )
  )

  lazy val defaultSettings = Defaults.defaultSettings ++ Seq(
    resolvers ++= Seq(
      "Typesafe Releases Repo" at "http://repo.typesafe.com/typesafe/releases/",
      "Typesafe Snapshot Repo" at "http://repo.typesafe.com/typesafe/snapshots/",
      "Central Repo" at "http://repo1.maven.org/maven2/",
      "Scala-Tools Maven2 Releases Repository" at "http://scala-tools.org/repo-releases",
      "Codahale Repo" at "http://repo.codahale.com",
      "Sonatype Repo" at "http://oss.sonatype.org/content/repositories/releases/"
    ),

    // compile options
    scalacOptions ++= Seq( "-encoding", "UTF-8", "-optimise", "-deprecation", "-unchecked" ),
    javacOptions ++= Seq( "-Xlint:unchecked", "-Xlint:deprecation" ),

    // disable parallel tests
    parallelExecution in Test := false
  )

  lazy val jarSettings = Seq( exportJars := true )

  object Dependencies {

    import Dependency._

    val akkaComponent = Seq( akkaActor, akkaRemote, akkaTestKit, commonsCodec )
    val crypto = Seq( bcprov, bcpkix, bcpg )
    val db = Seq( redisClient )
    val io = Seq( commonsNet, scalaIO )
    val date = Seq( scalaTime )
    val test = Seq( akkaTestKit, scalaTest )
  }

  object Dependency {

    object Version {
      val Akka = "2.1.0"
      val ScalaTest = "2.0.M5b"
      val Bouncycastle = "1.47"
      val CommonsNet = "3.1"
      val CommonsCodec = "1.7"
      val RedisClient = "2.9"
      val ScalaIO = "0.4.1"
      val ScalaTime = "0.6"
    }

    // ---- Application dependencies ----
    val akkaActor = "com.typesafe.akka" %% "akka-actor" % Version.Akka
    val akkaRemote = "com.typesafe.akka" %% "akka-remote" % Version.Akka
    val bcprov = "org.bouncycastle" % "bcprov-jdk15on" % Version.Bouncycastle
    val bcpkix = "org.bouncycastle" % "bcpkix-jdk15on" % Version.Bouncycastle
    val bcpg = "org.bouncycastle" % "bcpg-jdk15on" % Version.Bouncycastle
    val commonsNet = "commons-net" % "commons-net" % Version.CommonsNet
    val commonsCodec = "commons-codec" % "commons-codec" % Version.CommonsCodec

    val redisClient = "net.debasishg" %% "redisclient" % Version.RedisClient
    val scalaIO = "com.github.scala-incubator.io" %% "scala-io-core" % Version.ScalaIO
    val scalaTime = "org.scalaj" % "scalaj-time_2.10.0-M7" % Version.ScalaTime

    // ---- Test dependencies ----
    val akkaTestKit = "com.typesafe.akka" %% "akka-testkit" % Version.Akka % "test"
    val scalaTest = "org.scalatest" % "scalatest_2.10" % Version.ScalaTest % "test"
  }

}

import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import spray.revolver.RevolverPlugin._

object TaricBuild extends Build {
  val Organization = "io.taric"
  val Version = "0.0.1-SNAPSHOT"
  val ScalaVersion = "2.10.0"

  lazy val buildSettings = Seq(
    organization := Organization,
    version := Version,
    scalaVersion := ScalaVersion
  )

  lazy val formatSettings = SbtScalariform.scalariformSettings ++ Seq(
    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test    := formattingPreferences
  )

  def formattingPreferences = {
    import scalariform.formatter.preferences._
    FormattingPreferences()
    .setPreference( RewriteArrowSymbols, true )
    .setPreference( AlignParameters, true )
    .setPreference( AlignSingleLineCaseStatements, true )
    .setPreference( SpaceInsideParentheses, true )
  }


  lazy val taric = Project(
    id = "taric",
    base = file( "." ),
    settings = defaultSettings ++ buildSettings ++ depGraphSettings,
    aggregate = Seq( timport, parse, core )
  )

  lazy val timport = Project(
    id = "taric-import",
    base = file( "taric-import" ),
    dependencies = Seq( core ),
    settings = jarSettings ++ defaultSettings ++ buildSettings ++ depGraphSettings ++ Revolver.settings ++
      Seq(
        libraryDependencies ++= Dependencies.crypto ++
          Dependencies.io ++
          Dependencies.date ++
          Dependencies.test
      )
  )

  lazy val core = Project(
    id = "taric-core",
    base = file( "taric-core" ),
    dependencies = Seq( ),
    settings = jarSettings ++ defaultSettings ++ buildSettings ++ depGraphSettings ++ Seq(
        libraryDependencies ++= Dependencies.akka ++ Dependencies.b64
      )
  )

  lazy val parse = Project(
    id = "taric-parse",
    base = file( "taric-parse" ),
    dependencies = Seq( core ),
    settings = jarSettings ++ defaultSettings ++ buildSettings ++ depGraphSettings ++ Revolver.settings ++
      Seq(
        libraryDependencies ++= Dependencies.test
      )
  )

  lazy val defaultSettings = Defaults.defaultSettings ++ formatSettings ++ Seq(
    resolvers ++= Seq(
      "Typesafe Releases Repo" at "http://repo.typesafe.com/typesafe/releases/",
      "Typesafe Snapshot Repo" at "http://repo.typesafe.com/typesafe/snapshots/",
      "Central Repo" at "http://repo1.maven.org/maven2/",
      "Scala-Tools Maven2 Releases Repository" at "http://scala-tools.org/repo-releases",
      "Codahale Repo" at "http://repo.codahale.com",
      "Sonatype Repo" at "http://oss.sonatype.org/content/repositories/releases/",
      "Spray repo" at "http://repo.spray.io"
    ),

    // compile options
    scalacOptions ++= Seq( "-encoding", "UTF-8", "-optimise", "-deprecation", "-unchecked" ),
    javacOptions  ++= Seq( "-Xlint:unchecked", "-Xlint:deprecation" ),

    // disable parallel tests
    parallelExecution in Test := false
  )

 lazy val depGraphSettings = net.virtualvoid.sbt.graph.Plugin.graphSettings
  lazy val jarSettings = Seq( exportJars := true )

  object Dependencies {

    import Dependency._

    val akka   = Seq( akkaActor, akkaRemote, akkaTestKit )
    val crypto = Seq( bcprov, bcpkix, bcpg )
    val b64    = Seq( codec )
    val io     = Seq( scalaIO )
    val date   = Seq( scalaTime )
    val test   = Seq( akkaTestKit, scalaTest )
  }

  object Dependency {
    // ---- Dependency versions ----
    object Version {
      val Akka          = "2.1.0"
      val ScalaTest     = "2.0.M5b"
      val Bouncycastle  = "1.47"
      val ScalaIO       = "0.4.2"
      val ScalaTime     = "0.6"
      val CommonsCodec  = "1.8"
    }

    // ---- Application dependencies ----
    val akkaActor     = "com.typesafe.akka" %% "akka-actor" % Version.Akka
    val akkaRemote    = "com.typesafe.akka" %% "akka-remote" % Version.Akka
    
    val bcprov        = "org.bouncycastle" % "bcprov-jdk15on" % Version.Bouncycastle
    val bcpkix        = "org.bouncycastle" % "bcpkix-jdk15on" % Version.Bouncycastle
    val bcpg          = "org.bouncycastle" % "bcpg-jdk15on" % Version.Bouncycastle

    val scalaIO       = "com.github.scala-incubator.io" %% "scala-io-core" % Version.ScalaIO
    val scalaTime     = "org.scalaj" % "scalaj-time_2.10.0-M7" % Version.ScalaTime

    // ---- Test dependencies ----
    val akkaTestKit   = "com.typesafe.akka" %% "akka-testkit" % Version.Akka % "test"
    val scalaTest     = "org.scalatest" % "scalatest_2.10" % Version.ScalaTest % "test"

    val codec         = "commons-codec"    % "commons-codec"  % Version.CommonsCodec
  }

}

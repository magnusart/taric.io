package io.taric
package services

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import akka.testkit.{TestProbe, TestActorRef, TestKit, ImplicitSender}
import scala.concurrent.duration._
import concurrent.{Future, Await}
import akka.pattern.ask
import io.taric.TestConfiguration._
import akka.actor.{ActorRef, ActorSystem, Actor, Props}
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.scalatest.BeforeAndAfterAll
import akka.util.Timeout
import collection.script.Update
import services.RemoteResources._
import services.RemoteResources.ComputeLatestVersion
import services.RemoteResources.LatestVersionReport
import services.RemoteResources.FetchRemoteResource

/**
 * File created: 2013-01-14 16:36
 * 
 * Copyright Solvies AB 2013
 * For licensing information see LICENSE file
 */
class RemoteResourcesFlowSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with FlatSpec with ShouldMatchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("MockSystem"))


  override def afterAll {
    system.shutdown()
  }

  val totUrl = "TOT"
  val difUrl = "DIF"

  "Actor RemoteResources" should "emit messages containing the latest version for files when ComputeLatestVersion is sent" in {
    val probe = TestProbe()

    implicit val mockedDependencies = new ResourcesDependencies {
      def fetchFileListing(url: String): Future[List[String]] = url match {
        case "TOT"=> Future( totFiles )
        case "DIF" => Future( difFiles )
      }
      def fetchFilePlainTextLines(url: String, fileName: String): Future[Stream[String]] = ???
      def reportBus: ActorRef = probe.ref
    }

    val testRef = system.actorOf(Props(new RemoteResources() ), "remote-resources1")
    testRef ! ComputeLatestVersion( totPattern, totUrl )
    testRef ! ComputeLatestVersion( difPattern, difUrl )
    probe.expectMsgAllOf( LatestVersionReport( 3090 ), LatestVersionReport( 3094 ))
  }

  it should "emit messages containing FlatFileRecords when command FetchRemoteResource is sent" in {
    val probe = TestProbe()

    implicit val mockedDependencies = new ResourcesDependencies {
      def fetchFileListing(url: String): Future[List[String]] = ???

      def fetchFilePlainTextLines(url: String, fileName: String): Future[Stream[String]] = (url, fileName) match {
        case ("TOT", "KA") => Future( TestConfiguration.kaFile.split("\n").toStream )
        case ("TOT", "KI") => Future( TestConfiguration.kiFile.split("\n").toStream )
        case ("TOT", "KJ") => Future( TestConfiguration.kjFile.split("\n").toStream )
      }

      def reportBus: ActorRef = probe.ref
    }

    val actorUnderTest = system.actorOf(Props(new RemoteResources() ), "remote-resources2")

    actorUnderTest ! FetchRemoteResource( "TOT", "KA" )

    val msgsA = checkForFlatFileRecords( probe, 46 )
    msgsA.length should be ( 46 )
    msgsA.reduceLeft(_ && _) should be (true)
    probe.expectNoMsg()

    actorUnderTest ! FetchRemoteResource( "TOT", "KI")

    val msgsI = checkForFlatFileRecords( probe, 9 )
    msgsI.length should be (9)
    msgsI.reduceLeft(_ &&  _) should be (true)
    probe.expectNoMsg( 20 millis )

    actorUnderTest ! FetchRemoteResource( "TOT", "KJ")

    val msgsJ = checkForFlatFileRecords( probe, 9 )
    msgsJ.length should be (9)
    msgsJ.reduceLeft(_ && _) should be (true)
    probe.expectNoMsg( 20 millis )
  }

  def checkForFlatFileRecords( probe:TestProbe, maxMessages:Int ) = probe.receiveWhile( 20 millis, 20 millis, maxMessages ) {
    case r:FlatFileRecordReport => true
    case e @ _ => println(s"Got incorrect message $e."); false
  }
}

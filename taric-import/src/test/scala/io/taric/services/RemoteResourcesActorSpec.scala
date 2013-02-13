package io.taric
package services

import org.scalatest.{FlatSpec, BeforeAndAfterAll}
import org.scalatest.matchers.ShouldMatchers
import akka.testkit.{TestProbe, TestKit, ImplicitSender}
import scala.concurrent.duration._
import concurrent.Future
import io.taric.TestData._
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import TestSetup._
import services.CommandBus._
import services.EventBus._
import domains.{ManageSystemConfigurationHardCoded, FetchRemoteResources}
import akka.util.Timeout
import controllers.ImportController

/**
 * File created: 2013-01-14 16:36
 *
 * Copyright Solvies AB 2013
 * For licensing information see LICENSE file
 */

class RemoteResourcesActorSpec( _system:ActorSystem ) extends TestKit( _system ) with ImplicitSender with FlatSpec with ShouldMatchers with BeforeAndAfterAll {
  def this( ) = this( ActorSystem( "RemoteResourcesActorSystem", testConf ) )
  val probe = TestProbe( )
  implicit val timeout = Timeout( 3 seconds )

  implicit val eventProducer = new EventProducer {
    val eventBus:ActorRef = probe.ref
  }

  override def afterAll {
    system.shutdown( )
  }


  val totUrl = "TOT"
  val difUrl = "DIF"

  "Actor RemoteResources" should "emit messages containing the latest version for files when FetchListing is sent" in {

    implicit val mockedDependencies = new FetchRemoteResources {
      def fetchFileListing( url:String ):Future[List[String]] = url match {
        case "TOT" => Future( totFiles )
        case "DIF" => Future( difFiles )
      }

      def fetchFilePlainTextLines( url:String, fileName:String ):Future[Stream[String]] = ???
    }

    val testRef = system.actorOf( Props( new RemoteResources( ) ), "remote-resources1" )

    implicit val commandProducer = new CommandProducer {
      def commandBus:ActorRef = testRef
    }

    val agg = ImportController.aggregateListings( ManageSystemConfigurationHardCoded.totPattern, ManageSystemConfigurationHardCoded.difPattern, totUrl, difUrl )

    agg.mapTo[Listings].map( _ should be( Listings( Listing( "TOT", totFilteredFiles , 3090 ), Listing( "DIF", difFilteredFiles, 3094 ) ) ) )

  }

  it should "emit messages containing FlatFileRecords when command FetchRemoteResource is sent" in {

    implicit val mockedDependencies = new FetchRemoteResources {
      def fetchFileListing( url:String ):Future[List[String]] = Future( List.empty )

      def fetchFilePlainTextLines( url:String, fileName:String ):Future[Stream[String]] = (url, fileName) match {
        case ("TOT", "KA") => Future( TestData.kaFile.split( "\n" ).toStream )
        case ("TOT", "KI") => Future( TestData.kiFile.split( "\n" ).toStream )
        case ("TOT", "KJ") => Future( TestData.kjFile.split( "\n" ).toStream )
      }
    }

    val resourcesRef = system.actorOf( Props( new RemoteResources( ) ), "remote-resources2" )

    resourcesRef ! FetchRemoteResource( "TOT", "KA" )

    val msgsA = checkForFlatFileRecords( probe, 46 )
    msgsA.length should be( 46 )
    msgsA.reduceLeft( _ && _ ) should be( true )
    probe.expectNoMsg( )

    resourcesRef ! FetchRemoteResource( "TOT", "KI" )

    val msgsI = checkForFlatFileRecords( probe, 9 )
    msgsI.length should be( 9 )
    msgsI.reduceLeft( _ && _ ) should be( true )
    probe.expectNoMsg( 20 millis )

    resourcesRef ! FetchRemoteResource( "TOT", "KJ" )

    val msgsJ = checkForFlatFileRecords( probe, 9 )
    msgsJ.length should be( 9 )
    msgsJ.reduceLeft( _ && _ ) should be( true )
    probe.expectNoMsg( 20 millis )
  }

  private[this] def checkForFlatFileRecords( probe:TestProbe, maxMessages:Int ) = probe
    .receiveWhile( 1500 millis, 200 millis, maxMessages ) {
    case r:ProducedFlatFileRecord => true
    case e => println( s"Got incorrect message $e." ); false
  }
}

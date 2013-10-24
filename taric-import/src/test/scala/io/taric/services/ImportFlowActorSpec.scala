package io.taric
package services

import akka.actor.{ Props, ActorRef, ActorSystem }
import akka.testkit.{ TestProbe, TestKit }
import org.scalatest.{ GivenWhenThen, FeatureSpec, BeforeAndAfterAll }
import org.scalatest.matchers.ShouldMatchers
import io.taric.TestSetup._
import io.taric.controllers.TaricImportFSM
import io.taric.{ TestData, ImportApplication }
import concurrent.Future
import services.CommandBus._
import services.EventBus._
import domains._
import scala.concurrent.duration._
import akka.routing.Listen
import TestData._
import services.EventBus.VersionUrlsAggregate
import services.CommandBus.FetchRemoteResource
import services.EventBus.TaricPathPattern
import services.EventBus.TotDifUrls

/**
 * File created: 2013-01-20 22:18
 *
 * Copyright Solvies AB 2013
 * For licensing information see LICENSE file
 */
class ImportFlowActorSpec( _system: ActorSystem ) extends TestKit( _system ) with FeatureSpec
  with GivenWhenThen with ShouldMatchers with BeforeAndAfterAll {
  def this() = this( ActorSystem( "TestSystem3", testConf ) )
  val totUrl = s"${ManageSystemConfigurationHardCoded.webUrl}${ManageSystemConfigurationHardCoded.totPath}"
  val difUrl = s"${ManageSystemConfigurationHardCoded.webUrl}${ManageSystemConfigurationHardCoded.difPath}"

  override def afterAll {
    system.shutdown()
  }

  info( "As subscriber to Taric updates" )
  info( "I want to have changes to the Taric list imported and peristed automatically" )
  info( "So that I do not have to manually check and import for updates" )

  // Domain logic mocks setup
  implicit val remoteRes = new FetchRemoteResources {
    def fetchFileListing( url: String ): Future[List[String]] = url match {
      case t if t == totUrl ⇒ Future( TestData.totFiles )
      case d if d == difUrl ⇒ Future( TestData.difFiles )
    }
    def fetchFilePlainTextLines( url: String, fileName: String ): Future[Stream[String]] = ( url, fileName ) match {
      case ( totUrl, "3090_KA.tot.gz.pgp" ) ⇒ Future( TestData.kaFile.split( "\n" ).toStream )
      case ( totUrl, "3090_KI.tot.gz.pgp" ) ⇒ Future( TestData.kiFile.split( "\n" ).toStream )
      case ( totUrl, "3090_KJ.tot.gz.pgp" ) ⇒ Future( TestData.kjFile.split( "\n" ).toStream )
      case _                                ⇒ Future( Stream.empty )
    }
  }
  implicit val configMock = ManageSystemConfigurationHardCoded

  // Probes
  val eventProbe = TestProbe()
  val commandProbe = TestProbe()
  val remoteEventProbe = TestProbe()

  val app = new ImportApplication {
    val systemRef = system
    val commandBusRef: ActorRef = systemRef.actorOf( Props[CommandBus], "command-bus" )
    val eventBusRef: ActorRef = systemRef.actorOf( Props[EventBus], "event-bus" )

    // Dependency injection
    implicit val eventProducer = new EventProducer { val eventBus: ActorRef = eventBusRef }
    implicit val commandProducer = new CommandProducer { val commandBus: ActorRef = commandBusRef }

    val controller = systemRef.actorOf( Props( new TaricImportFSM() ), "taric-controller" )
    val systemRes = systemRef.actorOf( Props( new ApplicationResources() ), "app-resources" )
    val remoteResources = systemRef.actorOf( Props( new RemoteResources() ), "remote-resources" )
    val parserEventBusRef = remoteEventProbe.ref

    // Message spies
    eventBusRef ! Listen( eventProbe.ref )
    commandBusRef ! Listen( commandProbe.ref )
  }

  scenario( "First import, import and persist all Taric product codes (happy flow)." ) {
    Given( "new taric data is available" )
    And( "system configuration is prepared" )

    And( "The system is in an idle state" )
    app.prepareSystem
    When( "the start import event is published" )
    app.controller ! StartImport
    // Verify start import
    eventProbe.expectMsg( StartedImport )
    Then( "the import should gather systemRef configuration" )
    commandProbe.expectMsgAllOf( FetchCurrentVersion, FetchTaricUrls )

    eventProbe.expectMsg( Prepared )

    commandProbe.expectMsgAllOf(
      FetchListing( ManageSystemConfigurationHardCoded.totPattern, totUrl ),
      FetchListing( ManageSystemConfigurationHardCoded.difPattern, difUrl ) )

    Then( "browse for new remote resources" )
    Then( "determine which of these resources to fetch" )
    commandProbe.expectMsgAllOf( 1 seconds,
      FetchRemoteResource( totUrl, "3090_KA.tot.gz.pgp" ),
      FetchRemoteResource( totUrl, "3090_KI.tot.gz.pgp" ),
      FetchRemoteResource( totUrl, "3090_KJ.tot.gz.pgp" ) )

    Then( "fetch the remote resources, yielding line records" )
    eventProbe.expectMsgClass( classOf[ProducedFlatFileRecord] )


    // Then( "parse the line records into structured TaricCodes" )
    // Then( "merge information about existing codes, new codes and replaced codes" )
    // Then( "persist codes into the persistent data store" )
  }
}

package io.taric
package services

import akka.actor.{Props, ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{GivenWhenThen, FeatureSpec, BeforeAndAfterAll}
import org.scalatest.matchers.ShouldMatchers
import io.taric.TestSetup._
import io.taric.controllers.TaricImportFSM
import io.taric.{TestData, ImportApplication}
import concurrent.Future
import services.CommandBus.{CommandProducer, StartImport}
import akka.routing.Listen
import services.ReportBus.ReportProducer
import services.EventBus.EventProducer
import domains.FetchRemoteResources

/**
 * File created: 2013-01-20 22:18
 *
 * Copyright Solvies AB 2013
 * For licensing information see LICENSE file
 */
class ImportFlowActorSpec( _system:ActorSystem ) extends TestKit( _system ) with ImplicitSender with FeatureSpec
with GivenWhenThen with ShouldMatchers with BeforeAndAfterAll {
  def this( ) = this( ActorSystem( "TestSystem3", testConf ) )

  override def afterAll {
    system.shutdown( )
  }

  info( "As subscriber to Taric updates" )
  info( "I want to have changes to the Taric list imported and peristed automatically" )
  info( "So that I do not have to manually check and import for updates" )

  scenario( "First import, import and persist all Taric product codes (happy flow)." ) {
    Given( "new taric data is available" )
    And( "a persistent datastore is available" )
    And( "The system is in an idle state" )
    app.prepareSystem

    When( "the start import event is published" )
    app.commandBusRef ! StartImport

    Then( "the import should gather systemRef configuration" )
    Then( "browse for new remote resources" )
    Then( "determine which of these resources to fetch" )
    Then( "fetch the remote resources, yielding line records" )
    Then( "parse the line records into structured TaricCodes" )
    Then( "merge information about existing codes, new codes and replaced codes" )
    Then( "persist codes into the persistent data store" )
    pending
  }

  val app = new ImportApplication {
    val systemRef = system
    val commandBusRef:ActorRef = systemRef.actorOf( Props[CommandBus], "command-bus" )
    val eventBusRef:ActorRef = systemRef.actorOf( Props[EventBus], "event-bus" )
    val reportBusRef:ActorRef = systemRef.actorOf( Props[ReportBus], "report-bus" )

    // Dependency injection
    implicit val reportProducer = new ReportProducer {val reportBus:ActorRef = reportBusRef}
    implicit val eventProducer = new EventProducer {val eventBus:ActorRef = eventBusRef}
    implicit val commandProducer = new CommandProducer {val commandBus:ActorRef = commandBusRef}
    implicit val remoteRes = new FetchRemoteResources {
      def fetchFileListing( url:String ):Future[List[String]] = url match {
        case "/www1/distr/taric/flt/tot/" => Future( TestData.totFiles )
        case "/www1/distr/taric/flt/dif/" => Future( TestData.difFiles )
      }

      def fetchFilePlainTextLines( url:String, fileName:String ):Future[Stream[String]] = fileName match {
        case "3090_KA.tot.gz.pgp" => Future( TestData.kaFile.split( "\n" ).toStream )
        case "3090_KI.tot.gz.pgp" => Future( TestData.kiFile.split( "\n" ).toStream )
        case "3090_KJ.tot.gz.pgp" => Future( TestData.kjFile.split( "\n" ).toStream )
        case "3091_KA.dif.gz.pgp" => Future( Stream.empty )
        case "3091_KI.dif.gz.pgp" => Future( Stream.empty )
        case "3091_KJ.dif.gz.pgp" => Future( Stream.empty )
        case "3092_KA.dif.gz.pgp" => Future( Stream.empty )
        case "3092_KI.dif.gz.pgp" => Future( Stream.empty )
        case "3092_KJ.dif.gz.pgp" => Future( Stream.empty )
        case "3093_KA.dif.gz.pgp" => Future( Stream.empty )
        case "3093_KI.dif.gz.pgp" => Future( Stream.empty )
        case "3093_KJ.dif.gz.pgp" => Future( Stream.empty )
        case "3094_KA.dif.gz.pgp" => Future( Stream.empty )
        case "3094_KI.dif.gz.pgp" => Future( Stream.empty )
        case "3094_KJ.dif.gz.pgp" => Future( Stream.empty )
      }
    }

    val controller:ActorRef = systemRef.actorOf( Props( new TaricImportFSM ), "taric-controller" )
    val systemRes:ActorRef = systemRef.actorOf( Props( new ApplicationResources ), "app-resources" )
    val remoteResources:ActorRef = systemRef.actorOf( Props( new RemoteResources ), "remote-resources" )

    val eventLogger = system.actorOf( Props[EventLogger], "event-logger" )
    eventBusRef ! Listen( eventLogger )
  }
}

package io.taric

import akka.actor.{ ActorRef, Props, ActorSystem }
import com.typesafe.config._
import domains.{ ManageSystemConfigurationHardCoded, FetchRemoteResources }
import scala.concurrent.duration._
import services._
import akka.routing.Listen
import controllers.TaricImportFSM
import services.CommandBus.{ CommandProducer, StartImport }
import services.EventBus.EventProducer
import concurrent.Future
import unpure.TaricWebIO

object ImportApp extends App {

  val app = new ImportApplication {

    val config = ConfigFactory.load()
    val systemRef = ActorSystem( "TaricImportSystem", config )

    // Message buses
    val commandBusRef: ActorRef = systemRef.actorOf( Props[CommandBus], "command-bus" )
    val eventBusRef: ActorRef = systemRef.actorOf( Props[EventBus], "event-bus" )

    // Dependency injection for services
    implicit val eventProducer = new EventProducer { val eventBus: ActorRef = eventBusRef }
    implicit val commandProducer = new CommandProducer { val commandBus: ActorRef = commandBusRef }
    implicit val remoteRes = TaricWebIO
    implicit val fetchConfig = ManageSystemConfigurationHardCoded

    // Services
    val controller: ActorRef = systemRef.actorOf( Props( new TaricImportFSM ), "taric-controller" )
    val systemRes: ActorRef = systemRef.actorOf( Props( new ApplicationResources ), "app-resources" )
    val remoteResources: ActorRef = systemRef.actorOf( Props( new RemoteResources ), "remote-resources" )

  }

  app.startSystem
}

trait ImportApplication {
  def systemRef: ActorSystem
  def commandBusRef: ActorRef
  def eventBusRef: ActorRef
  def controller: ActorRef
  def systemRes: ActorRef
  def remoteResources: ActorRef

  private[this] def registerListeners {
    commandBusRef ! Listen( systemRes )
    commandBusRef ! Listen( remoteResources )
  }

  // Start scheduler
  private[this] def startScheduler = ( systemRef scheduler ) schedule ( 0 seconds, 6 hours, controller, StartImport )

  def prepareSystem {
    registerListeners
  }

  def startSystem {
    prepareSystem
    startScheduler
    systemRef.log.info( "Started {} for Taric.io import module.", systemRef.name )
  }
}

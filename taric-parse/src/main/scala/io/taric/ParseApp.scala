package io.taric

import akka.actor.{ ActorRef, Props, ActorSystem }
import com.typesafe.config._
import scala.concurrent.duration._
import services._
import akka.routing.Listen
import services.CommandBus.CommandProducer
import services.EventBus.EventProducer
import concurrent.Future
import akka.routing._

object ParseApp extends App with ParseApplication {

  val config = ConfigFactory.load( "application.conf" )
  val systemRef = ActorSystem( "TaricParseSystem", config )

  // Message buses
  val commandBusRef: ActorRef = systemRef.actorOf( Props[CommandBus], "command-bus" )
  val eventBusRef: ActorRef = systemRef.actorOf( Props[EventBus], "event-bus" )

  // Dependency injection for services
  implicit val eventProducer = new EventProducer { val eventBus: ActorRef = eventBusRef }
  implicit val commandProducer = new CommandProducer { val commandBus: ActorRef = commandBusRef }

  // Services
  val parser = systemRef.actorOf( Props( new Parser ), "parser" )
  val aggregator = systemRef.actorOf( Props( new BatchAggregator ), "aggregator" )
  val workers = systemRef.actorOf( Props( new TaricCodeConverterWorker ).withRouter( FromConfig() ), "parserWorkers" )

  this.startSystem
}

trait ParseApplication {
  def systemRef: ActorSystem
  def commandBusRef: ActorRef
  def eventBusRef: ActorRef
  def parser: ActorRef
  def aggregator: ActorRef
  def workers: ActorRef

  private[this] def registerListeners {
    commandBusRef ! Listen( parser )
    commandBusRef ! Listen( workers )
    eventBusRef ! Listen( aggregator )
  }

  def prepareSystem {
    registerListeners
  }

  def startSystem {
    prepareSystem
    systemRef.log.info( s"Started ${systemRef.name} for Taric.io parse module." )
  }
}

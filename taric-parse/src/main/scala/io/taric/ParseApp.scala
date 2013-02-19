package io.taric

import akka.actor.{ ActorRef, Props, ActorSystem }
import com.typesafe.config._
import scala.concurrent.duration._
import services._
import akka.routing.Listen
import services.CommandBus.CommandProducer
import services.EventBus.EventProducer
import concurrent.Future

object ParseApp extends App {

  val app = new ParseApplication {

    val config = ConfigFactory.load()
    val systemRef = ActorSystem( "TaricParseSystem", config )

    // Message buses
    val commandBusRef: ActorRef = systemRef.actorOf( Props[CommandBus], "command-bus" )
    val eventBusRef: ActorRef = systemRef.actorOf( Props[EventBus], "event-bus" )

    // Dependency injection for services
    implicit val eventProducer = new EventProducer { val eventBus: ActorRef = eventBusRef }
    implicit val commandProducer = new CommandProducer { val commandBus: ActorRef = commandBusRef }

    // Services
    val parser: ActorRef = systemRef.actorOf( Props( new Parser ), "remote-resources" )

  }

  app.startSystem
}

trait ParseApplication {
  def systemRef: ActorSystem
  def commandBusRef: ActorRef
  def eventBusRef: ActorRef
  def parser: ActorRef

  private[this] def registerListeners {
    commandBusRef ! Listen( parser )
  }

  def prepareSystem {
    registerListeners
  }

  def startSystem {
    prepareSystem
    systemRef.log.info( s"Started ${systemRef.name} for Taric.io parse module." )
  }
}

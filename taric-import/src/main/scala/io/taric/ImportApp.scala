package io.taric

import akka.actor.{ Props, ActorSystem }
import com.typesafe.config._
import io.taric.Actors.EventBus
import akka.routing.Listen

object ImportApp extends App {
  val config = ConfigFactory.load()

  val system = ActorSystem("TaricImportSystem", config)

  val eventBus = system.actorOf(Props[EventBus], "event-bus")
}


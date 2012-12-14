package io.taric

import akka.actor._
import io.taric.Contracts.Event
import akka.routing.Listeners
import scala.collection.JavaConversions._

object Actors {

    class EventBus extends Actor with ActorLogging with Listeners {

      def receive = listenerManagement orElse {
        case ev: Event => gossip(ev)
      }

      // We want to forward the events
      override def gossip(msg: Any) = listeners foreach (_ forward msg)
    }

}
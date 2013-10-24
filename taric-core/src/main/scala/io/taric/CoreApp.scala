package io.taric

import akka.actor.{ ActorRef, Props, ActorSystem }
import com.typesafe.config._
import akka.routing.RoundRobinRouter
import scala.concurrent.duration._
import akka.routing.Listen
/**
 * User: Magnus Andersson
 * Date: 2013-01-10
 * Time: 22:39
 */
object CoreApp extends App {
  val config = ConfigFactory.load()

  val system = ActorSystem( "TaricCoreSystem", config )

}

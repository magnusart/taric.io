package io.taric

import akka.actor.{ActorRef, Props, ActorSystem}
import com.typesafe.config._
import akka.routing.RoundRobinRouter
import scala.concurrent.duration._
import services._
import akka.routing.Listen
import controllers.TaricImportFSM
import services.EventBus.StartImport
;

object ImportApp extends App {
  val config = ConfigFactory.load( )

  val system = ActorSystem( "TaricImportSystem", config )

  system.log.info( "Stared Actorsystem {} for Taric.io ImportApp.", system.name )

  val commandBus  = system.actorOf( Props[CommandBus], "command-bus" )
  val eventBus    = system.actorOf( Props[EventBus],   "event-bus"   )
  val reportBus   = system.actorOf( Props[ReportBus],  "report-bus"  )

  // Controller
  val taricController = system.actorOf( Props[TaricImportFSM], "taric-controller" )

  // ActorServices
  val systemRes = system.actorOf( Props[ApplicationResources], "app-resources" )

  // Register Controller with report bus
  reportBus ! Listen( taricController )

  // Register ActorServices with command bus
  commandBus ! Listen( systemRes )

  system.log.info( "Created actors and message buses." )
  system.log.info( "Starting import scheduler." )

  // Start scheduler
  ( system scheduler ) schedule(0 seconds, 60.seconds, eventBus, StartImport)

  system.log.info( "System ready." )

}

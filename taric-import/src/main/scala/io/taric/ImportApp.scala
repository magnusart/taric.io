package io.taric

import akka.actor.{ActorRef, Props, ActorSystem}
import com.typesafe.config._
import io.taric.services._
import akka.routing.{RoundRobinRouter, Listen}
import akka.util.duration._
import io.taric.models._
import services._
import akka.routing.Listen
;

object ImportApp extends App {
  val config = ConfigFactory.load()

  val system = ActorSystem("TaricImportSystem", config)

  val eventBus = system.actorOf(Props[EventBus], "event-bus")

  // ActorServices
  val taricBrowser = system.actorOf(Props[TaricFtpBrowser], "taric-ftp")
  val taricReader = system.actorOf(Props[TaricReader], "taric-reader")
  val pgpDecryptor = system.actorOf(Props[PgpDecryptor], "pgp-decryptor")
  val gzipDecompressor = system.actorOf(Props[GzipDecompressor], "gzip-decompressor")
  val taricParser = system.actorOf(Props[TaricParser], "taric-parser")

  // Routed ActorServices
  val taricDebugPrinter1 = system.actorOf(Props[DebugLogger], "debug-logger-1")
  val taricDebugPrinter2 = system.actorOf(Props[DebugLogger], "debug-logger-2")
  val taricDebugPrinter3 = system.actorOf(Props[DebugLogger], "debug-logger-3")
  val routees = Vector[ActorRef](taricDebugPrinter1, taricDebugPrinter2, taricDebugPrinter3)

  // Route these
  val debugRouter = system.actorOf(Props().withRouter(RoundRobinRouter(routees = routees)))

  // Register ActorServices with event bus
  eventBus ! Listen(taricBrowser)
  eventBus ! Listen(taricReader)
  eventBus ! Listen(pgpDecryptor)
  eventBus ! Listen(gzipDecompressor)
  eventBus ! Listen(taricParser)
  eventBus ! Listen(debugRouter)

  val taricHttpResouce = "http://distr.tullverket.se/distr/taric/flt/tot/3030_KA.tot.gz.pgp"

  // TODO Magnus Andersson (2012-12-15) Move this to configuration
  val totalFiles = "ftp://M10746-1:kehts3qW@distr.tullverket.se:21/www1/distr/taric/flt/tot/"
  val diffFiles = "ftp://M10746-1:kehts3qW@distr.tullverket.se:21/www1/distr/taric/flt/dif/"

  // Start scheduler
  //(system scheduler) schedule(0.milliseconds, 30.seconds, eventBus, TaricKaResource( taricHttpResouce ))
  (system scheduler) schedule(0 seconds, 30.seconds, eventBus, TaricTotalResourceFtp( totalFiles ))
  (system scheduler) schedule(15 seconds, 30.seconds, eventBus, TaricDiffResourceFtp( diffFiles ))

}


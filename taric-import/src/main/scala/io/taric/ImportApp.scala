package io.taric

import akka.actor.{ActorRef, Props, ActorSystem}
import com.typesafe.config._
import akka.routing.RoundRobinRouter
import scala.concurrent.duration._
import io.taric.domains._
import services._
import akka.routing.Listen
import controllers.TaricImportFSM
;

object ImportApp extends App {
  val config = ConfigFactory.load()

  val system = ActorSystem("TaricImportSystem", config)

  system.log.info("Stared Actorsystem {} for Taric.io ImportApp.", system.name)

  val commandBus = system.actorOf(Props[CommandBus], "command-bus")
  val reportBus = system.actorOf(Props[ReportBus], "report-bus")

  // Controller
  val taricController = system.actorOf(Props[TaricImportFSM], "taric-controller")

  // ActorServices
  val systemRes = system.actorOf(Props[ApplicationResources], "app-resources")
  val taricBrowser = system.actorOf(Props[TaricFtpBrowser], "taric-ftp")
  val pgpDecryptor = system.actorOf(Props[PgpDecryptor], "pgp-decryptor")
  val gzipDecompressor = system.actorOf(Props[GzipDecompressor], "gzip-decompressor")
  val taricParser = system.actorOf(Props[TaricStreamParser], "taric-parser")
  val sqlPersister = system.actorOf(Props[SqlPersister], "taric-sql-persister")
  val plainTextPersister = system.actorOf(Props[PlainTextPersister], "plain-text-persister")
  // Routed ActorServices
  val taricDebugPrinter1 = system.actorOf(Props[DebugLogger], "debug-logger-1")
  val taricDebugPrinter2 = system.actorOf(Props[DebugLogger], "debug-logger-2")
  val taricDebugPrinter3 = system.actorOf(Props[DebugLogger], "debug-logger-3")
  val routees = Vector[ActorRef](taricDebugPrinter1, taricDebugPrinter2, taricDebugPrinter3)

  // Route these
  val debugRouter = system.actorOf(Props().withRouter(RoundRobinRouter(routees = routees)))

  // Register Controller with report bus
  reportBus ! Listen(taricController)

  // Register ActorServices with command bus
  commandBus ! Listen(systemRes)
  commandBus ! Listen(taricBrowser)
  commandBus ! Listen(pgpDecryptor)
  commandBus ! Listen(gzipDecompressor)
  commandBus ! Listen(taricParser)
  commandBus ! Listen(debugRouter)
  commandBus ! Listen(sqlPersister)
  commandBus ! Listen(plainTextPersister)

  system.log.info("Created actors and message buses.")

  system.log.info("Starting import scheduler.")

  // Start scheduler
  (system scheduler) schedule(0 seconds, 60.seconds, reportBus, ReadyToStartImport)

  system.log.info("System ready.")

}

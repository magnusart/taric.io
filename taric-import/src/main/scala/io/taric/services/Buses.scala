package io.taric.services

import akka.actor.{ActorRef, ActorLogging, Actor}
import akka.routing.Listeners
import akka.actor.Status.Failure
import io.taric.domains.{TaricRecord, FlatFileRecord}

/**
 * File created: 2013-01-20 21:28
 *
 * Copyright Solvies AB 2013
 * For licensing information see LICENSE file
 */
class CommandBus extends Actor with ActorLogging with Listeners {

  import CommandBus.Command

  def receive = listenerManagement orElse {
    case ev:Command   => gossip( ev )( sender )
    case Failure( f ) => log.error( "Actor sent failure: {}. Message: {}", f, f.getStackTraceString )
    case ar:AnyRef    => log.error( "Got a unkown object on the command bus: {}.", ar )
  }
}
object CommandBus {
  trait CommandProducer {
    def commandBus:ActorRef
  }

  sealed trait Command
  case object StartImport extends Command

  case object FetchCurrentVersion extends Command
  case object FetchTaricUrls extends Command
  case class ReplaceCurrentVersion( ver:Int ) extends Command

  case object BrowseFTP extends Command
  case class ComputeLatestVersion( pattern:String, url:String ) extends Command
  case class FetchRemoteResource( url:String, fileName:String ) extends Command

  case class ParseFlatFileRecord( record:FlatFileRecord ) extends Command
  case class ParsedAsTaric( code:TaricRecord ) extends Command
}

class EventBus extends Actor with ActorLogging with Listeners {

  import EventBus.Event

  def receive = listenerManagement orElse {
    case ev:Event     => gossip( ev )
    case Failure( f ) => log.error( "Actor sent failure: {}. Message: {}", f, f.getStackTraceString )
    case ar:AnyRef    => log.error( "Got a unkown object on the event bus: {}.", ar )
  }
}
object EventBus {
  trait EventProducer {
    def eventBus:ActorRef
  }

  sealed trait Event
  case object StartedImport extends Event
  case object ImportFinished extends Event
  case class ReplacedCurrentVersion( oldVer:Int, newVer:Int ) extends Event
  case class ProducedFlatFileRecord( record:FlatFileRecord ) extends Event
}

class ReportBus extends Actor with ActorLogging with Listeners {

  import ReportBus.Report

  def receive = listenerManagement orElse {
    case rp:Report => gossip( rp )
    case Failure( f ) => log.error( "Actor sent failure: {}. Message: {}", f, f.getStackTraceString )
    case ar:AnyRef => log.error( "Got a unkown object on the report bus: {}.", ar )
  }
}
object ReportBus {
  trait ReportProducer {
    def reportBus:ActorRef
  }

  sealed trait Report
  case class CurrentVersion( ver:Int ) extends Report
  case class LatestVersionInListing( ver:Int ) extends Report

  case class TaricPathPattern( path:String, pattern:String )
  case class TaricUrls( taricFtpUrl:String, tot:TaricPathPattern, dif:TaricPathPattern ) extends Report
  case class VersionUrlsAggregate( ver:Int, urls:TaricUrls ) extends Report
}

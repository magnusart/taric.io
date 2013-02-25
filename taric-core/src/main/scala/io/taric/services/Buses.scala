package io.taric
package services

import akka.actor.{ ActorRef, ActorLogging, Actor }
import akka.routing.Listeners
import akka.actor.Status.Failure

/**
 * File created: 2013-01-20 21:28
 *
 * Copyright Solvies AB 2013
 * For licensing information see LICENSE file
 */
trait TaricRecord

case class FlatFileRecord( line: String )

class CommandBus extends Actor with ActorLogging with Listeners {

  import CommandBus.Command

  def receive = listenerManagement orElse {
    case cmd: Command ⇒ gossip( cmd )( sender ); log.debug( s"Command: $cmd" )
    case Failure( f ) ⇒ log.error( s"Actor sent failure: $f. Message: ${f.getStackTraceString}" )
    case a            ⇒ log.error( s"Got a unkown object on the report bus: $a." )
  }
}

object CommandBus {
  trait CommandProducer {
    def commandBus: ActorRef
  }

  sealed trait Command
  case object StartImport extends Command

  case object FetchCurrentVersion extends Command
  case object FetchTaricUrls extends Command
  case class ReplaceCurrentVersion( ver: Int ) extends Command

  case class FetchListing( pattern: String, url: String ) extends Command
  case class FetchRemoteResource( url: String, fileName: String ) extends Command

  case class ParseFlatFileRecord( record: FlatFileRecord ) extends Command
}

class EventBus extends Actor with ActorLogging with Listeners {

  import EventBus.Event

  def receive = listenerManagement orElse {
    case ev: Event    ⇒ gossip( ev )( sender ); log.debug( s"Event: $ev" )
    case Failure( f ) ⇒ log.error( s"Actor sent failure: $f. Message: ${f.getStackTraceString}" )
    case a            ⇒ log.error( s"Got a unkown object on the report bus: $a." )
  }
}
object EventBus {
  trait EventProducer {
    def eventBus: ActorRef
  }

  sealed trait Event
  case object StartedImport extends Event
  case object Prepared extends Event
  case object FinishedBrowsing extends Event
  case object ImportFinished extends Event
  case class ReplacedCurrentVersion( oldVer: Int, newVer: Int ) extends Event
  case class ProducedFlatFileRecord( record: FlatFileRecord ) extends Event
  case class LastFlatFileRecordForFile( record: FlatFileRecord ) extends Event
  case class BatchCompleted( batchId: String, noOfRecords: Int ) extends Event

  case class CurrentVersion( ver: Int ) extends Event
  case class Listing( url: String, files: List[String], latestVer: Int ) extends Event
  case class Listings( tot: Listing, dif: Listing ) extends Event

  case class TaricPathPattern( path: String, pattern: String )
  case class TotDifUrls( TaricUrl: String, tot: TaricPathPattern, dif: TaricPathPattern ) extends Event
  case class VersionUrlsAggregate( ver: Int, urls: TotDifUrls ) extends Event

  case class ParsedAsTaric( code: TaricRecord ) extends Event
}

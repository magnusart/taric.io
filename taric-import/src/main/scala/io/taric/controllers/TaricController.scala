package io.taric
package controllers

import akka.actor.{Actor, FSM}
import akka.pattern.{ask, pipe}
import io.taric.domains._
import io.taric.ImportApp._
import scala.concurrent.duration._
import org.apache.commons.net.ftp.FTPClient
import java.io.InputStream
import akka.routing.Deafen
import akka.event.LoggingAdapter
import concurrent.Future
import scala.Option
import akka.util.Timeout
import services.EventBus._
import services.ReportBus._
import services.CommandBus._
import TaricImportFSM._

object TaricImportFSM {
  sealed trait State

  case object Idle extends State
  case object Deaf extends State
  case object Preparing extends State
  case object BrowsingFTP extends State

  sealed trait Data
  case object Uninitialized extends Data
}

/**
 * Cycles through the following states when importing.
 * Idle ->
 * Preparing (system properties) ->
 * BrowsingFTP ->
 */
class TaricImportFSM extends Actor with FSM[State, Data] {
  implicit val logger = log

  startWith( Idle, Uninitialized )

  // Cleanup
  onTransition {
    case _ -> Idle =>
      log.debug( "Transitioning to state Idle, trying to clean up connections." )
  }

  when( Idle ) {
    case Event( StartImport, _ ) =>
      log.debug( "Starting taric import." )
      goto( Preparing ) forMax ( 20 seconds )
  }

  onTransition {
    case Idle -> Preparing =>
      implicit val timeout = Timeout( 15 seconds )
      aggregateVersionUrls( timeout ) pipeTo reportBus
      log.debug( "Transitioning Idle -> Preparing" )
  }

  // Aggregate system preferences needed
  def aggregateVersionUrls( implicit timeout:akka.util.Timeout ) = {
    val verFuture = ( commandBus ? FetchCurrentVersion )
    val urlsFuture = ( commandBus ? FetchTaricUrls )
    for {
      CurrentVersion( ver ) <- verFuture.mapTo[CurrentVersion]
      urls:TaricUrls <- urlsFuture.mapTo[TaricUrls]
    } yield VersionUrlsAggregate( ver, urls )
  }

  when( Preparing ) {
    case Event( VersionUrlsAggregate( ver, TaricUrls( url, tot, dif ) ), _ ) =>
      log.debug( "Got system preferences" )
      goto( BrowsingFTP ) forMax ( 2 minutes )
  }

  whenUnhandled {
    case Event( StateTimeout, _ ) => goto( Idle )
    case Event( msg, data ) => goto( Idle ) using data
    case Event( ImportFinished, data ) => goto( Idle ) using data
  }

  onTermination {
    case StopEvent( _, state, stateData ) if ( state != Idle ) =>
      log.debug( "Stopping FSM." )
      eventBus ! Deafen( self )
  }

  initialize
}

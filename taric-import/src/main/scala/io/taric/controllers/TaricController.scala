package io.taric
package controllers

import akka.actor.{Actor, FSM}
import akka.pattern.{ask, pipe}
import scala.concurrent.duration._
import akka.util.Timeout
import services.EventBus._
import services.CommandBus._
import TaricImportFSM._
import services.CommandBus.FetchListing
import controllers.TaricImportFSM.TaricRemote
import domains.LocatingTaricFiles._
import services.EventBus.VersionUrlsAggregate
import services.CommandBus.FetchRemoteResource
import services.EventBus.CurrentVersion
import services.CommandBus.FetchListing
import controllers.TaricImportFSM.TaricRemote
import services.EventBus.Listing
import services.EventBus.TaricPathPattern
import services.EventBus.Listings
import services.EventBus.TotDifUrls
import services.EventBus

object TaricImportFSM {
  sealed trait State

  case object Idle extends State
  case object Deaf extends State
  case object Preparing extends State
  case object BrowsingFiles extends State

  sealed trait Data
  case object Uninitialized extends Data
  case class TaricRemote( currentVer:Int, urls:TotDifUrls ) extends Data
}

/**
 * Cycles through the following states when importing.
 * Idle ->
 * Preparing (systemRef properties) ->
 * BrowsingFiles ->
 */
object ImportController {
  implicit val timeout = Timeout( 15 seconds )

  // Aggregate systemRef preferences needed
  def aggregateVersionUrls( implicit c:CommandProducer ) = {
    val verFuture = ( c.commandBus ? FetchCurrentVersion )
    val urlsFuture = ( c.commandBus ? FetchTaricUrls )
    for {
      CurrentVersion( ver ) <- verFuture.mapTo[CurrentVersion]
      urls:TotDifUrls <- urlsFuture.mapTo[TotDifUrls]
    } yield VersionUrlsAggregate( ver, urls )
  }

  def aggregateListings( totPat:String, difPat:String, totUrl:String, difUrl:String )( implicit c:CommandProducer ) = {
    val totVerFuture = ( c.commandBus ? FetchListing( totPat, totUrl ) )
    val difVerFuture = ( c.commandBus ? FetchListing( difPat, difUrl ) )
    for {
      tot <- totVerFuture.mapTo[Listing]
      dif <- difVerFuture.mapTo[Listing]
    } yield Listings( tot, dif )
  }

  def determineFilesToFetch( listings:Listings, currentVer:Int )( implicit c:CommandProducer ) = listings match {
    case Listings( Listing( totUrl, totFiles, totVer ), Listing( difUrl, difFiles, difVer ) ) =>
      if( shouldGetSnapshot( currentVer, totVer ) )
        filesIncluding( totVer, totFiles ) map ( FetchRemoteResource( totUrl, _ ) ) foreach ( c.commandBus ! _ )
      if( shouldGetDeltas( currentVer, totVer, difVer ) )
        filesLaterThan( totVer, difFiles ) map ( FetchRemoteResource( difUrl, _ ) ) foreach ( c.commandBus ! _ )
  }

  private[this] def shouldGetDeltas( currentVer:Int, totVer:Int, difVer:Int ) = currentVer < totVer && totVer < difVer
  private[this] def shouldGetSnapshot( currentVer:Int, totVer:Int ) = currentVer < totVer

}

class TaricImportFSM( implicit c:CommandProducer, e:EventProducer ) extends Actor with FSM[State, Data] {
  import ImportController._
  implicit val logger = log

  startWith( Idle, Uninitialized )

  when( Idle ) {
    case Event( StartImport, _ ) =>
      e.eventBus ! StartedImport
      goto( Preparing ) forMax ( 20 seconds )
  }

  onTransition {
    case Idle -> Preparing =>
      aggregateVersionUrls pipeTo self
  }

  when( Preparing ) {
    case Event( VersionUrlsAggregate( ver, t@TotDifUrls( url, tot, dif ) ), _ ) =>
      e.eventBus ! Prepared
      goto( BrowsingFiles ) forMax ( 2 minutes ) using ( TaricRemote( ver, t ) )
  }

  onTransition {
    case Preparing -> BrowsingFiles => nextStateData match {
      case TaricRemote( currentVer, TotDifUrls( url, TaricPathPattern( totPath, totPat ), TaricPathPattern( difPath, difPat ) ) ) =>
        val listings = aggregateListings( totPat, difPat, url + totPath, url + difPath )
        listings.mapTo[Listings].map( determineFilesToFetch( _, currentVer ) )
      case state =>
        log.debug( s"Found unexpected state $state. Transitioning to Idle state." )
        goto( Idle )
    }
    log.debug( "Transitioning Preparing -> BrowsingFiles" )
  }

  when( BrowsingFiles ) {
    case Event( _, _ ) =>
      e.eventBus ! FinishedBrowsing
      stay()

  }

  // Cleanup
  onTransition {
    case state -> Idle =>
      log.debug( s"Transitioning to state Idle from $state" )
  }

  whenUnhandled {
    case Event( StateTimeout, _ ) => log.info( "Got timeout." ); goto( Idle )
    case Event( msg, data ) => log.info( s"Unhandeled message $msg with data $data." ); goto( Idle ) using data
  }

  onTermination {
    case StopEvent( _, state, stateData ) if ( state != Idle ) =>
      log.debug( "Stopping FSM." )
  }

  initialize
}

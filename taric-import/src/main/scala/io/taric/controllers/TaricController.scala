package io.taric
package controllers

import akka.actor.{Actor, FSM}
import akka.pattern.{ask, pipe}
import io.taric.domains._
import io.taric.ImportApp._
import scala.concurrent.duration._
import org.apache.commons.net.ftp.FTPClient
import java.io.{ InputStream }
import akka.routing.Deafen
import io.taric.domains.OpenResources
import io.taric.domains.CurrentVersion
import io.taric.domains.BrowsingFtpForVersions
import io.taric.domains.TaricUrls
import io.taric.domains.BrowsingResult
import akka.util.Timeout
import akka.event.LoggingAdapter
import concurrent.{Promise, Await, Future}
import scala.Option
import util.{Failure, Success}
import akka.util.Timeout
import akka.util.Timeout._

/**
 * Copyright Solvies AB 2012
 * User: magnus
 * Date: 2012-12-17
 * Time: 21:57
 */
object TaricHandler {
  def doCleanup(stateData:Data, nextStateData:Data)(implicit log:LoggingAdapter) = (stateData, nextStateData) match {
    case (_, OpenResources(client, _, streams, _)) =>
      cleanup(client, streams)
      log.debug("Result from cleanup. Client open = {}.", isClientOpen(client) )
    case (OpenResources(client, _, streams, _), _) =>
      cleanup(client, streams)
      log.debug("Result from cleanup. Client open = {}.", isClientOpen(client) )
    case (_, _) =>
      log.debug("No connections to clean up")
  }

  def cleanup(client:Option[FTPClient] = None, streams:Option[List[InputStream]] = None) = {
    closeStreams(streams)
    closeConnection(client)
  }

  private[this] def isClientOpen( client:Option[FTPClient] ) = client.map( _.isConnected ) getOrElse( false )

  private[this] def closeStreams(streamsOpt:Option[List[InputStream]]) = streamsOpt.map(_.foreach(_.close()))

  private[this] def closeConnection(client:Option[FTPClient]) = client.map {
    con => if(con.isConnected) {
      con.logout()
      con.disconnect()
    }
  }

}

/**
 * Cycles through the following states when importing.
 * Idle ->
 * Preparing (system properties) ->
 * BrowsingFTP ->
 * OpeningStreams ->
 * Decrypting ->
 * Unzipping ->
 * Parsing ->
 * Persisting ->
 */
class TaricImportFSM extends Actor with FSM[State, Data] {
  implicit val logger = log

  startWith(Idle, Uninitialized)

  // Cleanup
  onTransition {
    case _ -> Idle =>
      log.debug("Transitioning to state Idle, trying to clean up connections.")
      TaricHandler.doCleanup(stateData, nextStateData)
  }

  when(Idle) {
    case Event(ReadyToStartImport, _) =>
      log.debug("Starting taric import.")
      goto(Preparing) forMax( 20 seconds )
  }

  onTransition {
    case Idle -> Preparing =>
      implicit val timeout = Timeout(15 seconds)
      aggregateVersionUrls(timeout) pipeTo reportBus
      log.debug("Transitioning Idle -> Preparing")
  }

  // Aggregate system preferences needed
  def aggregateVersionUrls( implicit timeout:akka.util.Timeout ) ={
    val verFuture = (commandBus ? FetchCurrentVersion)
    val urlsFuture = (commandBus ? FetchTaricUrls)
    for {
      CurrentVersion(ver) <- verFuture.mapTo[CurrentVersion]
      urls:TaricUrls <- urlsFuture.mapTo[TaricUrls]
    } yield VersionUrlsAggregate( ver, urls )
  }

  when(Preparing) {
    case Event( VersionUrlsAggregate( ver, TaricUrls( url, tot, dif ) ), _) =>
      log.debug("Got system preferences")
      goto( BrowsingFTP ) using( BrowsingFtpForVersions( ver, url, tot, dif ) ) forMax( 2 minutes )
  }

  onTransition {
    case Preparing -> BrowsingFTP =>
      log.debug("Transitioning Preparing -> BrowsingFTP")
      nextStateData match {
        case BrowsingFtpForVersions( ver, url, tot, dif ) =>
          commandBus ! BrowseFTP( ver, url, tot, dif)
      }
  }

  when(BrowsingFTP) {
    case Event(BrowsingResult(files, client), data:BrowsingFtpForVersions) =>
      log.debug("Done browsing FTP streams.")
      goto( OpeningStreams ) using OpenResources( client, files ) forMax( 30 seconds )
  }

  onTransition {
    case BrowsingFTP -> OpeningStreams =>
      log.debug("Transitioning BrowsingFTP -> OpeningStreams")
      nextStateData match {
        case OpenResources( Some( client ), Some( files ), _, _ ) => commandBus ! OpenStreams( files, client )
      }
  }

  when(OpeningStreams) {
    case Event( StreamsOpened( openStreams ), data:OpenResources )=>
      log.debug("Streams now open.")
      goto( Decrypting ) using( data.copy( streams = Option( openStreams ) ) ) forMax( 30 seconds )
  }

  onTransition {
    case OpeningStreams -> Decrypting =>
      implicit val timeout = Timeout(60 seconds)
      log.debug("Transitioning OpeningStreams -> Decrypting")
      nextStateData match {
        case OpenResources( _, _, Some( openStreams ), _ ) =>
          convertToStreamsDecrypted( openStreams ) pipeTo( reportBus )
      }
  }

  private[this] def convertToStreamsDecrypted(openStreams:List[InputStream])
                                             (implicit timeout:akka.util.Timeout) = for {
    aggregate <- Future( aggregateDecryptedStreams( openStreams ) )
    streams <- aggregate
  } yield StreamsDecrypted( streams )

  private[this] def aggregateDecryptedStreams(openStreams:List[InputStream])
                                       (implicit timeout:akka.util.Timeout):Future[List[InputStream]] = {
    val streamFutures = Future.sequence( openStreams.map( commandBus ? DecryptStream( _ ) ) )
    streamFutures.mapTo[List[StreamDecrypted]].map(_.map(_.stream))
  }

  when(Decrypting) {
    case Event( StreamsDecrypted( decryptedStreams ), data:OpenResources )  =>
      log.debug("Decrypting streams.")
      goto(Unzipping) using( data.copy( streams = Some( decryptedStreams ) ) ) forMax( 30 seconds )
  }

  onTransition {
    case Decrypting -> Unzipping =>
      implicit val timeout = Timeout(60 seconds)
      log.debug("Transitioning Decrypting -> Unzipping")
      nextStateData match {
        case OpenResources( _, _, Some( decryptedStreams ), _ ) =>
          aggregateUnzippedStreams( decryptedStreams ).mapTo[List[InputStream]]
            .map( StreamsUnzipped( _ ) ).pipeTo( reportBus )
      }
  }

  private[this] def aggregateUnzippedStreams( decryptedStreams:List[InputStream] )
                                      (implicit timeout:akka.util.Timeout) = {
    val streamFutures = for {
      stream <- decryptedStreams
    } yield ( commandBus ? UnzipStream( stream ) )

    Future.sequence( for ( unzippedFuture <- streamFutures )
    yield unzippedFuture.mapTo[StreamUnzipped].map(_.stream) )
  }

  when(Unzipping) {
    case Event( StreamsUnzipped( unzippedStreams ), data:OpenResources ) =>
      log.debug("Unzipping streams.")
      goto(Parsing) using ( data.copy( streams = Option( unzippedStreams ) ) ) forMax ( 60 seconds )
  }

  onTransition {
    case Unzipping -> Parsing =>
      implicit val timeout = Timeout(60 seconds)
      log.debug("Transitioning Unzipping -> Parsing")
      nextStateData match {
        case OpenResources( _, _, Some( unzippedStreams ), _) =>
          log.debug("Got {} unzipped streams: {}.", unzippedStreams.length, unzippedStreams)
          convertToTaricCodeStreams( aggregateParsedStreams( unzippedStreams ) )
            .map( StreamsParsed( _ ) ).pipeTo( reportBus )
      }
  }

  private[this] def aggregateParsedStreams( unzippedStreams:List[InputStream])
                                          (implicit timeout:akka.util.Timeout):List[Future[StreamParsed]] = for {
    stream <- unzippedStreams
  } yield ( commandBus ? ParseStream( stream ) ).mapTo[StreamParsed]

  private[this] def convertToTaricCodeStreams(streamFutures:List[Future[StreamParsed]]) = for {
    parsedStreams <- Future.sequence( streamFutures ).mapTo[List[StreamParsed]]
  } yield sortStreamsParsed( parsedStreams ).map( _.stream )

  private[this] def isBothKFile(l:String, r:String) = l.take(1) == "K" && r.take(1) == "K"
  private[this] def isBothDFile(l:String, r:String) = l.take(1) == "D" && r.take(1) == "D"
  private[this] def sameTypeSort(l:String, r:String) = l.drop(1) < r.drop(1)
  private[this] def differentTypeSort(l:String, r:String) = l.take(1) > r.take(1) // Reversed Since D comes before K in the alphabet

  private[this] def sortStreamsParsed( parsedStreams:List[StreamParsed] ) = parsedStreams.sortWith {
    case (StreamParsed(l,_), StreamParsed(r,_)) if(isBothKFile(l,r) || isBothDFile(l,r)) => sameTypeSort(l,r)
    case (StreamParsed(l,_), StreamParsed(r,_)) => differentTypeSort(l,r)
  }

  when(Parsing) {
    case Event( StreamsParsed( parsedStreams ), data:OpenResources ) =>
      log.debug("Parsing Streams.")
      // We are now guaranteed to have the streams sorted in the right
      goto(Persisting) using( data.copy( parsedStreams = Option( parsedStreams ) ) ) forMax( 60 seconds )
  }

  onTransition {
    case Parsing -> Persisting =>
    log.debug("Transitioning Parsing -> Persisting")
    nextStateData match {
      case OpenResources( _, _, _, Some( parsedStreams ) ) =>
        commandBus ! PersistCodes( parsedStreams )
    }
  }


  when(Persisting) {
    case Event(FinishedPersisting, data) =>
      log.debug("Persisting streams.")
      goto (Idle)
  }

  whenUnhandled {
    case Event( StateTimeout, _ ) => goto( Idle )
    case Event(msg, data) => goto (Idle) using data
    case Event(ImportFinished, data) => goto (Idle) using data
  }

  onTermination{
    case StopEvent(_, state, stateData) if (state != Idle) =>
      log.debug("Stopping FSM.")
      stateData match {
        case OpenResources(client, _, streams, _) =>
          log.debug("Cleanup of connections.")
          TaricHandler.cleanup(client, streams)
      }
      log.debug("Disconnecting from report bus.")
      reportBus ! Deafen(self)
  }

  initialize
}

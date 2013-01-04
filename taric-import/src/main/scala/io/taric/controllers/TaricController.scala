package io.taric
package controllers

import akka.actor.{Actor, FSM}
import akka.pattern.{ask, pipe}
import io.taric.models._
import io.taric.ImportApp._
import scala.concurrent.duration._
import org.apache.commons.net.ftp.FTPClient
import java.io.InputStream
import akka.routing.Deafen
import io.taric.models.FTPConnection
import io.taric.models.CurrentVersion
import io.taric.models.BrowsingFtpForVersions
import io.taric.models.TaricUrls
import io.taric.models.BrowsingResult
import akka.util.Timeout
import akka.event.LoggingAdapter
import scala.concurrent.Future
import scala.Option

/**
 * Copyright Solvies AB 2012
 * User: magnus
 * Date: 2012-12-17
 * Time: 21:57
 */
object TaricHandler {
  def doCleanup(stateData:Data, nextStateData:Data)(implicit log:LoggingAdapter) = (stateData, nextStateData) match {
    case (_, FTPConnection(client, _, streams)) => cleanup(client, streams)
    log.debug("Result from cleanup. Client open = {}.", isClientOpen(client) )
    case (FTPConnection(client, _, streams), _) => cleanup(client, streams)
    log.debug("Result from cleanup. Client open = {}.", isClientOpen(client) )
  }

  def cleanup(client:Option[FTPClient] = None, streams:Option[List[Option[InputStream]]] = None) = {
    closeStreams(streams)
    closeConnection(client)
  }

  private def isClientOpen( client:Option[FTPClient] ) = client.map( _.isConnected ) getOrElse( false )

  private def closeStreams(streams:Option[List[Option[InputStream]]]) = for {
    stream <- streams.getOrElse(List.empty)
    s <- stream
  } yield s.close()


  private def closeConnection(client:Option[FTPClient]) = client.map {
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
      implicit val timeout = Timeout(60 seconds)
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
    case Event(BrowsingResult(fileNames, client), data:BrowsingFtpForVersions) =>
      log.debug("Done browsing FTP streams.")
      val fileStrings = for( file <- fileNames.getOrElse(List.empty) ) yield file.getName
      goto( OpeningStreams ) using FTPConnection( client, Option( fileStrings ) ) forMax( 30 seconds )
  }

  onTransition {
    case BrowsingFTP -> OpeningStreams =>
      log.debug("Transitioning BrowsingFTP -> OpeningStreams")
      nextStateData match {
        case FTPConnection( Some( client ), Some( fileStrings ), _ ) => commandBus ! OpenStreams( fileStrings, client )
      }
  }

  when(OpeningStreams) {
    case Event( StreamsOpened( openStreams ), data:FTPConnection )=>
      log.debug("Streams now open.")
      goto( Decrypting ) using( data.copy( streams = Option( openStreams ) ) ) forMax( 30 seconds )
  }

  onTransition {
    case OpeningStreams -> Decrypting =>
      implicit val timeout = Timeout(60 seconds)
      log.debug("Transitioning OpeningStreams -> Decrypting")
      nextStateData match {
        case FTPConnection( _, _, Some( openStreams ) ) =>
          Future( aggregateDecryptedStreams( openStreams ) ).mapTo[List[Option[InputStream]]]
            .map( StreamsDecrypted( _ ) ).pipeTo( reportBus )

      }
  }

  private def aggregateDecryptedStreams(openStreams:List[Option[InputStream]])
                                       (implicit timeout:akka.util.Timeout) = {
    val streamFutures = for {
      streamOpt <- openStreams
      stream <- streamOpt
    } yield ( commandBus ? DecryptStream( stream ) )

    Future.sequence( for ( decryptedFuture <- streamFutures )
    yield decryptedFuture.mapTo[StreamDecrypted].map(_.stream) )
  }

  when(Decrypting) {
    case Event( StreamsDecrypted( decryptedStreams ), data:FTPConnection )  =>
      log.debug("Decrypting streams.")
      goto(Unzipping) using( data.copy( streams = Option( decryptedStreams ) ) ) forMax( 30 seconds )
  }

  onTransition {
    case Decrypting -> Unzipping =>
      implicit val timeout = Timeout(60 seconds)
      log.debug("Transitioning Decrypting -> Unzipping")
      nextStateData match {
        case FTPConnection( _, _, Some( decryptedStreams ) ) =>
          aggregateUnzippedStreams( decryptedStreams ).mapTo[List[Option[InputStream]]]
            .map( StreamsUnzipped( _ ) ).pipeTo(reportBus)
      }
  }

  private def aggregateUnzippedStreams( decryptedStreams:List[Option[InputStream]] )
                                      (implicit timeout:akka.util.Timeout) = {
    val streamFutures = for {
      streamOpt <- decryptedStreams
      stream <- streamOpt
    } yield ( commandBus ? UnzipStream( stream ) )

    Future.sequence( for ( unzippedFuture <- streamFutures )
    yield unzippedFuture.mapTo[StreamUnzipped].map(_.stream) )
  }

  when(Unzipping) {
    case Event( StreamsUnzipped( unzippedStreams ), data:FTPConnection ) =>
      log.debug("Unzipping streams.")
      goto(Parsing) using ( data.copy( streams = Option( unzippedStreams ) ) ) forMax ( 2 minutes )
  }

  when(Parsing) {
    case _ =>
      log.debug("Parsing streams.")
      goto(Persisting)
  }

  when(Persisting) {
    case _ =>
      log.debug("Persisting streams.")
      goto (Idle)
  }

  whenUnhandled {
    case Event( StateTimeout, _ ) => goto( Idle )
    case Event(msg, data) => goto (Idle) using data
  }

  onTermination{
    case StopEvent(_, state, stateData) if (state != Idle) =>
      stateData match {
        case FTPConnection(client, _, streams) => TaricHandler.cleanup(client, streams)
      }
      reportBus ! Deafen(self)
  }

  initialize
}

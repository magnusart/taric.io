package io.taric
package services

import akka.actor._
import models._
import io.taric.models.TaricKaParser._
import io.taric.ImportApp.{commandBus, reportBus}
import akka.routing.Listeners
import scala.concurrent.Future

import org.bouncycastle.openpgp.{PGPLiteralData, PGPCompressedData, PGPObjectFactory}
import akka.pattern.pipe
import akka.util.Timeout
import scala.concurrent.duration._
import org.apache.commons.net.ftp.FTPClient
import java.util.zip.GZIPInputStream
import java.io._
import java.net.URL
import java.security._
import models.TaricKaStream
import models.OpenStreams
import models.BrowseFTP
import models.ParseStream
import models.UnzipStream
import models.CurrentVersion
import models.StoreCurrentVersion
import models.PathFileName
import models.DecryptStream
import models.StreamsOpened
import models.TaricCode
import models.TaricPathPattern
import models.TaricKaResource
import models.StreamUnzipped
import models.TaricUrls
import models.BrowsingResult
import models.TaricKaCode
import models.StreamDecrypted


class CommandBus extends Actor with ActorLogging with Listeners {
  def receive = listenerManagement orElse {
    case ev: Command => gossip(ev)(sender)
    log.debug("Command bus {}.", ev)
    case ar:AnyRef => log.error("Got a unkown object on the command bus: %s.".format(ar))
  }
}

class ReportBus extends Actor with ActorLogging with Listeners {
  def receive = listenerManagement orElse {
    case ev: Report => gossip(ev)
    log.debug("Report bus {}.", ev)
    case ar:AnyRef =>
      log.error("Got a unkown object on the report bus: %s.".format(ar))
  }
}

class ApplicationResources extends Actor with ActorLogging {
  var currentVersion = 0

  // TODO Magnus Andersson (2012-12-15) Move this to configuration
  val taricFtpUrl = "ftp://M10746-1:kehts3qW@distr.tullverket.se:21"

  val totalPath = "/www1/distr/taric/flt/tot/"
  val totalPattern = """^\d{4}_(KA|KI|KJ).tot.gz.pgp"""

  val diffPath = "/www1/distr/taric/flt/dif/"
  val diffPattern = """^\d{4}_(DA|DI|DJ).tot.gz.pgp"""

  def receive = {
    // TODO 2012-12-31 (Magnus Andersson) Store this in AppDB or filesystem
    case FetchCurrentVersion => sender ! CurrentVersion(currentVersion)
      log.debug("Current version {} requested.", currentVersion)

    case StoreCurrentVersion(ver) => currentVersion = ver
    log.debug("New current version {} to be stored.", currentVersion)

    case FetchTaricUrls => sender ! TaricUrls(taricFtpUrl,
        TaricPathPattern(totalPath, totalPattern),
        TaricPathPattern(diffPath, diffPattern) )
  }
}

class TaricFtpBrowser extends Actor with ActorLogging {
  import io.taric.utilities.FtpUtility._
  implicit val loggger = log

  override def receive = {
    case BrowseFTP( ver, ftpUrl, TaricPathPattern(tpath, tpat), TaricPathPattern(dpath, dpat)) =>
      implicit val url = ftpUrl
      connectToFtp {
        implicit ftpClient:FTPClient => Future {
          // Get latest snapshots (actually three of them)
          // TODO: Magnus Andersson (2013-01-03) There is a bug here, always returns 0.
          val latestTot = getLatestFile(tpath, tpat)
          log.debug("Latest total file version {}.", latestTot)

          if (latestTot > ver) {
            val tot = getFiles(latestTot - 1, tpath, tpat)
            val dif = getFiles(latestTot, dpath, dpat)
            reportBus ! BrowsingResult( Option( (tot ++ dif).toList ), Option(ftpClient) )
            commandBus ! StoreCurrentVersion( latestTot )
          } else {
            log.debug("Did not find any newer versions (latest: {}) than current version {}.", latestTot, ver)
          }
        }
      }

    case OpenStreams( files, ftpClient ) => {
      log.debug("Opening Streams for taric files: {}.", files)
      implicit val timeout = Timeout(15 seconds)
      implicit val client = ftpClient
      Future (
        for ( PathFileName(path, fileName) <- files ) yield getFileStream( path, fileName )
      ).mapTo[List[InputStream]].map( StreamsOpened( _ ) ).pipeTo( reportBus )
    }
  }
}


class TaricReader extends Actor with ActorLogging {
  private[this] def parseFromURL( url:String ):InputStream = new URL(url).openStream
  implicit val timeout = Timeout(60 seconds)

  override def receive = {
    case TaricKaResource( url ) => Future( parseFromURL( url ) ) map
      ( TaricKaStream( _ ) ) pipeTo commandBus
  }
}

class PgpDecryptor extends Actor with ActorLogging {
  private[this] def decryptPgp(messageStream:InputStream):InputStream = {
    import org.bouncycastle.jce.provider.BouncyCastleProvider
    import org.bouncycastle.openpgp.PGPUtil

    Security.addProvider(new BouncyCastleProvider())

    val armored = PGPUtil.getDecoderStream(messageStream)
    val pgpF = new PGPObjectFactory(armored)
    val compressed = pgpF.nextObject().asInstanceOf[PGPCompressedData]
    val pgpF2 = new PGPObjectFactory(compressed getDataStream )
    pgpF2.nextObject() // Skip signature list
    val literal = pgpF2.nextObject().asInstanceOf[PGPLiteralData]
    literal.getInputStream
  }

  override def receive = {
    case DecryptStream( stream ) =>
      Future( decryptPgp( stream ) ).mapTo[InputStream].map( StreamDecrypted( _ ) ).pipeTo( sender )
  }
}

class GzipDecompressor extends Actor with ActorLogging {
  private[this] def unzipStream(stream:InputStream):InputStream = new GZIPInputStream(stream)

  override def receive = {
    case UnzipStream( stream ) => Future( unzipStream( stream ) )
      .mapTo[InputStream]
      .map( StreamUnzipped( _ ) )
      .pipeTo( sender )
  }
}

class TaricParser extends Actor with ActorLogging {

  private[this] def lineReader( stream: InputStream ):Stream[String] = {
    val reader = new BufferedReader( new InputStreamReader( stream ) )
    Stream.continually(reader readLine)

  }

  private[this] def parseTaricStream( stream:InputStream ):Stream[TaricCode] = {
    log.debug("Parsing Taric stream.")
    val reader = lineReader( stream )
    log.debug("Reader {}.", reader)
    val streamType:String = reader.take(1).toList(0).drop(10).take(2)
    log.debug("Stream type {}.", streamType)
    routeParser(streamType, reader.drop(1))
  }

  private[this] def parseKaCodes(stream:Stream[String]) = for {
    line <- stream
  } yield TaricCode(prodcode(line), startDate(line), endDate(line))

  private[this] def routeParser(streamType:String, reader:Stream[String]) = streamType match {
    case t @ "KA" =>
      log.debug("Got a {} stream", t)
      parseKaCodes(reader)
    case t @ "KI" =>
      log.debug("Got a {} stream", t)
      Stream.empty
    case t @ "KJ" =>
      log.debug("Got a {} stream", t)
      Stream.empty
    case t @ "DA" =>
      log.debug("Got a {} stream", t)
      Stream.empty
    case t @ "DI" =>
      log.debug("Got a {} stream", t)
      Stream.empty
    case t @ "DJ" =>
      log.debug("Got a {} stream", t)
      Stream.empty
    case _ =>
      log.error("Got an unsupported stream type {}.", streamType)
      Stream.empty
  }

  override def receive = {
    case ParseStream( stream ) =>
      Future( parseTaricStream( stream ) ) map
      ( _.map(TaricKaCode( _ ) ) foreach ( commandBus ! _ ) )
  }
}

class Persist extends Actor with ActorLogging {
  override def receive = null
}

class DebugLogger extends Actor with ActorLogging {
  override def receive = {
    case TaricKaCode( taricCode ) => {
      log.debug( taricCode toString )
    }
  }
}
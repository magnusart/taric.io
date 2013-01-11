package io.taric
package services

import akka.actor._
import models._
import io.taric.models.TaricKAParser._
import io.taric.ImportApp.{commandBus, reportBus}
import akka.routing.Listeners
import concurrent.{Await, Future}

import org.bouncycastle.openpgp.{PGPLiteralData, PGPCompressedData, PGPObjectFactory}
import akka.pattern.{ pipe, ask }
import akka.util.Timeout
import scala.concurrent.duration._
import org.apache.commons.net.ftp.FTPClient
import java.util.zip.GZIPInputStream
import java.io._
import java.net.URL
import java.security._
import models.OpenStreams
import models.BrowseFTP
import models.ParseStream
import models.UnzipStream
import models.CurrentVersion
import models.StoreCurrentVersion
import models.PathFileName
import models.DecryptStream
import models.StreamsOpened
import models.ExistingTaricCode
import models.TaricPathPattern
import models.StreamUnzipped
import models.TaricUrls
import models.BrowsingResult
import models.StreamDecrypted
import models.TaricCode
import utilities.IOLogic
import akka.actor.Status.Failure
import scala.io.Source

class CommandBus extends Actor with ActorLogging with Listeners {
  def receive = listenerManagement orElse {
    case ev: Command => gossip(ev)(sender)
    log.debug("Command bus {}.", ev)
    case Failure(f) => log.error("Actor sent failure: {}. Message: {}", f, f.getStackTraceString)
    case ar:AnyRef  => log.error("Got a unkown object on the command bus: {}.", ar)
  }
}

class ReportBus extends Actor with ActorLogging with Listeners {
  def receive = listenerManagement orElse {
    case ev: Report => gossip(ev)
      log.debug("Report bus {}.", ev)
    case Failure(f) => log.error("Actor sent failure: {}. Message: {}", f, f.getStackTraceString)
    case ar:AnyRef  => log.error("Got a unkown object on the report bus: {}.", ar)
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
  import io.taric.utilities.IOLogic._
  implicit val loggger = log

  private[this] def openFileStreams( files:List[PathFileName] )(implicit timeout:Timeout, ftpClient:FTPClient) = Future {
    for ( PathFileName(path, fileName) <- files ) yield getFileStream( path, fileName )
  }

  private[this] def aggregateStreams( future:Future[List[InputStream]] ) = for {
    streams <- future.mapTo[List[InputStream]]
  } yield StreamsOpened( streams.filterNot( _ == null ) )

  override def receive = {
    case BrowseFTP( ver, ftpUrl, TaricPathPattern(tpath, tpat), TaricPathPattern(dpath, dpat)) =>
      implicit val url = ftpUrl
      connectToFtp {
        implicit ftpClient:FTPClient => Future {
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

      aggregateStreams( openFileStreams(files) ).pipeTo( reportBus )
    }
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
      Future( decryptPgp( stream ) )
        .mapTo[InputStream]
        .map( StreamDecrypted( _ ) )
        .pipeTo( sender )
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
  import scalax.io._
  import java.io.{ InputStreamReader, BufferedReader }
  implicit val logger = log

  private[this] def parseTaricStream( stream:InputStream ) = {
    log.debug("Converting stream {}.", stream)
    val in = new BufferedReader( new InputStreamReader( stream ) )
    val header = in.readLine // Buggy LongTraversable, cannot peek on one line and then continue elsewhere easily.
    val reader:LongTraversable[String] = Resource.fromReader( in ).lines()
    val streamType:String = TaricCommonParser.taricType( header )
    log.debug("Stream type {}.", streamType)
    TaricParser.routeParser(streamType, reader)
  }

  override def receive = {
    case ParseStream( stream ) =>
      Future( parseTaricStream( stream ) )
        .mapTo[(String, LongTraversable[TaricCode])]
        .map { case (stype, stream) => StreamParsed( stype, stream ) }
        .pipeTo( sender )
  }
}

class SqlPersister extends Actor with ActorLogging {
  import scalax.io._
  import java.io.File

  def filterCodes(hs:String) = hs.startsWith("03") || hs.startsWith("1603") || hs.startsWith("1604")

  override def receive = {
    case PersistCodes( streams ) =>
      implicit val codec = Codec.UTF8
      implicit val timeout = Timeout( 10 seconds )
      val verFuture = commandBus ? FetchCurrentVersion
      val CurrentVersion( ver ) = Await.result(verFuture, 10 seconds).asInstanceOf[CurrentVersion]
      val fileName = s"import-$ver.sql"
      val f = new File(fileName)
      if( f.isFile ) f.delete()
      log.debug("Using {} as filename", fileName)
      val file = Resource.fromFile(fileName)
      streams foreach { stream =>
        val codes = for ( code:TaricCode <- stream if filterCodes(code.hs) ) yield code

        codes foreach {
          //PRDKOD_ID, PRDKOD_PRDKODTYP_ID, PRDKOD_HS, PRDKOD_HSSUB, PRDKOD_CN, PRDKOD_PRECISION, PRDKOD_STARTDATTID, PRDKOD_AKTIV, PRDKOD_BESKRIVNING, PRDKOD_UPPDATTID, PRDKOD_UPPANV
          case code @ ExistingTaricCode(_, startDate, endDate) =>
            file.write(s"INSERT INTO PRODUKTKOD VALUES(ID, 1, '${code.hs}', '${code.hsSub}', '${code.cn}', '${code.pres}', TO_DATE('$startDate', 'yyyyMMdd'), 'J', '', SYSDATE, 'taric-import');\n")
            if(endDate.isDefined) file.write(s"INSERT INTO PRODUKTKOD VALUES(ID, 1, '${code.hs}', '${code.hsSub}', '${code.cn}', '${code.pres}', TO_DATE('$endDate.get', 'yyyyMMdd'), 'N', '', SYSDATE, 'taric-import');\n")
          case NewTaricCode(oldCode, newCode, startDate) => log.debug("New taric code {}, replacing {} at {}", oldCode, newCode, startDate)
          case ReplaceTaricCode(oldCode, newCode, newDate) => log.debug("Replace taric code {} with {} at {}", oldCode, newCode, newDate)
          case u @ _ => log.error("Got unknown element {}", u)
        }
      }
      reportBus ! FinishedPersisting
  }
}

class DebugLogger extends Actor with ActorLogging {
  override def receive = {
    case t:TaricCode if (t.code.startsWith("0304")) => {
      log.debug( t.code toString )
    }
  }
}
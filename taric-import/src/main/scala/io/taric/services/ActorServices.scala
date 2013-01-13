package io.taric
package services

import akka.actor._
import models._
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
import java.security._
import akka.actor.Status.Failure
import org.joda.time.DateTime
import utilities.IOLogic
import models.BrowseFTP
import models.UnzipStream
import akka.actor.Status.Failure
import models.NewTaricCode
import models.PathFileName
import models.DecryptStream
import models.BrowsingResult
import models.OpenStreams
import models.ParseStream
import models.ExistingTaricCode
import models.CurrentVersion
import models.ReplaceTaricCode
import models.StoreCurrentVersion
import models.PersistText
import models.StreamsOpened
import models.PersistCodes
import models.TaricPathPattern
import models.StreamUnzipped
import models.TaricUrls
import models.StreamDecrypted

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

class TaricStreamParser extends Actor with ActorLogging {
  import scalax.io._
  import java.io.{ InputStreamReader, BufferedReader }
  implicit val logger = log

  private[this] def parseTaricStream( stream:InputStream ) = {
    val header = IOLogic.readHeaderLine( stream )
    val reader = IOLogic.getReader( stream )

    val streamType:String = TaricParser.taricPostType( header )

    log.debug("Stream type {}.", streamType)

    TaricParser.routeParser(streamType, reader)
  }

  override def receive = {
    case ParseStream( stream ) =>
      //commandBus ! PersistText( stream )
      Future( parseTaricStream( stream ) )
        .mapTo[(String, LongTraversable[TaricCode])]
        .map { case (stype, in) => StreamParsed( stype, in ) }
        .pipeTo( sender )
  }
}

class PlainTextPersister extends Actor with ActorLogging {
  import scalax.io._
  import java.io.{ InputStreamReader, BufferedReader }

  def receive = {
    case PersistText( stream ) =>
      log.debug(s"Persisting $stream as plain text.")
      val in = new BufferedReader( new InputStreamReader( stream ) )
      val out = Resource.fromFile ( "out.taric"  )
      val reader:LongTraversable[String] = Resource.fromReader( in ).lines()
      out writeStrings( reader, "\n" )
  }
}

class SqlPersister extends Actor with ActorLogging {
  import scalax.io._
  import java.io.File
  import TaricCode._
  import SqlExpressionEvaluator._

  private[this] def filterCodes(hs:String) = hs.startsWith("03") || hs.startsWith("1604") || hs.startsWith("1605")
  private[this] def getFile(ver:Int) = {
    val fileName = s"import-$ver.sql"
     val f = new File(fileName)
    if( f.isFile ) {
      val oldFileName = fileName.dropRight(4) + "-" + DateTime.now().toString("yyyy-MM-dd_hhssSSS") + ".sql"
      f.renameTo(new File( oldFileName ) )
      log.debug("Renaming file {} to {}.", fileName, oldFileName)
    }
    log.debug("Using {} as filename", fileName)
    Resource.fromFile(fileName)
  }

  override def receive = {
    case PersistCodes( streams ) =>
      implicit val codec = Codec.UTF8
      implicit val t1 = Timeout( 1 seconds )
      val verFuture = commandBus ? FetchCurrentVersion
      val CurrentVersion( ver ) = Await.result(verFuture, 1 seconds).asInstanceOf[CurrentVersion]

      val stream = streams(0)
      val sqls = for {
        code:TaricCode <- stream
        if ( filterCodes(code.hs) )
      } yield asSqlString( code )

      implicit val t2 = Timeout(30 seconds)
      log.debug(s"Got a list of SQL-strings: $sqls.")
      Future {
        val out = getFile( ver )
        sqls foreach( out.write( _ ) )
        reportBus ! FinishedPersisting
      }
  }
}

class DebugLogger extends Actor with ActorLogging {
  override def receive = {
    case t:TaricCode if (t.code.startsWith("0304")) => {
      log.debug( t.code toString )
    }
  }
}

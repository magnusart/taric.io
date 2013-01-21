package io.taric
package services

import akka.actor._
import domains._
import concurrent.Future

import akka.pattern.pipe
import domains.FlatFileRecord

import CommandBus._
import EventBus._
import ReportBus._

class RemoteResources( implicit d:FetchRemoteResources, r:ReportProducer, e:EventProducer ) extends Actor with ActorLogging {

  import LocatingTaricFiles._

  private[this] def fetchFileListing( pattern:String, url:String ) = for {
    fileNames <- d.fetchFileListing( url )
    filteredNames <- Future( fileNames.filter( filterFileType( pattern, _ ) ) )
    fileVersion <- Future( latestFileVersion( filteredNames ) )
    report <- Future( LatestVersionInListing( fileVersion ) )
  } yield report

  private[this] def fetchRemoteFileLines( url:String, fileName:String ) = for {
    lines <- d.fetchFilePlainTextLines( url, fileName )
    records <- Future( ( lines map FlatFileRecord ) )
    reports <- Future( ( records map ProducedFlatFileRecord ) )
  } yield reports

  private[this] def emitAll( futureRecords:Future[Stream[ProducedFlatFileRecord]] ) =
    for( records <- futureRecords )
    yield records.foreach( e.eventBus ! _ )

  def receive = {
    case ComputeLatestVersion( pattern, url ) => fetchFileListing( pattern, url ) pipeTo ( r.reportBus )
    case FetchRemoteResource( url, fileName ) => emitAll( fetchRemoteFileLines( url, fileName ) )
  }
}

class TaricCodeConverterWorker( implicit r:ReportProducer ) extends Actor with ActorLogging {

  import TaricCodeExtensions._

  def receive = {
    case ParseFlatFileRecord( record ) => record.asTaricCode match {
      case Right( rec ) => r.reportBus ! ParsedAsTaric( rec )
      case Left( m ) => log.error( m )
    }
  }
}


class ApplicationResources( implicit e:EventProducer ) extends Actor with ActorLogging {

  var currentVersion = 0

  // TODO Magnus Andersson (2012-12-15) Move this to configuration
  val taricFtpUrl = "ftp://M10746-1:kehts3qW@distr.tullverket.se:21"

  val totalPath = "/www1/distr/taric/flt/tot/"
  val totalPattern = """^\d{4}_(KA|KI|KJ).tot.gz.pgp"""

  val difPath = "/www1/distr/taric/flt/dif/"
  val difPattern = """^\d{4}_(DA|DI|DJ).dif.gz.pgp"""

  def receive = {
    // TODO 2012-12-31 (Magnus Andersson) Store this in AppDB or filesystem
    case FetchCurrentVersion => sender ! CurrentVersion( currentVersion )

    case ReplaceCurrentVersion( ver ) =>
      val oldVer = currentVersion
      currentVersion = ver
      e.eventBus ! ReplacedCurrentVersion( oldVer, ver )

    case FetchTaricUrls => sender ! TaricUrls(
      taricFtpUrl,
      TaricPathPattern( totalPath, totalPattern ),
      TaricPathPattern( difPath, difPattern )
    )
  }
}

class EventLogger extends Actor with ActorLogging {
  def receive = {
    case e:Event => log.debug(s"$e")
  }
}

class DebugLogger extends Actor with ActorLogging {
  def receive = {
    case t:TaricCode if ( t.code.startsWith( "0304" ) ) => {
      log.debug( t.code toString )
    }
  }
}

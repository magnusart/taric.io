package io.taric
package services

import akka.actor._
import domains._
import concurrent.Future

import akka.pattern.pipe
import services.RemoteResources.ResourcesDependencies
import domains.FlatFileRecord
import services.TaricCodeConverterWorker.TaricCodeConverterDependencies

import CommandBus._
import EventBus._
import ReportBus._

class RemoteResources( implicit val dep:ResourcesDependencies ) extends Actor with ActorLogging {

  import LocatingTaricFiles._

  val reportBus = dep.reportBus
  val eventBus = dep.eventBus

  private[this] def fetchFileListing( pattern:String, url:String ) = for {
    fileNames <- dep.fetchFileListing( url )
    filteredNames <- Future( fileNames.filter( filterFileType( pattern, _ ) ) )
    fileVersion <- Future( latestFileVersion( filteredNames ) )
    report <- Future( NewLatestVersion( fileVersion ) )
  } yield report

  private[this] def fetchRemoteFileLines( url:String, fileName:String ) = for {
    lines <- dep.fetchFilePlainTextLines( url, fileName )
    records <- Future( ( lines map FlatFileRecord ) )
    reports <- Future( ( records map ProducedFlatFileRecord ) )
  } yield reports

  private[this] def emitAll( futureRecords:Future[Stream[ProducedFlatFileRecord]] ) =
    for( records <- futureRecords )
    yield records.foreach( eventBus ! _ )

  def receive = {
    case ComputeLatestVersion( pattern, url ) => fetchFileListing( pattern, url ) pipeTo ( reportBus )
    case FetchRemoteResource( url, fileName ) => emitAll( fetchRemoteFileLines( url, fileName ) )
  }
}

object RemoteResources {
  trait ResourcesDependencies extends ReportProducer with EventProducer with FetchRemoteResources
}

class TaricCodeConverterWorker( implicit val dep:TaricCodeConverterDependencies ) extends
Actor with ActorLogging {

  import TaricCodeExtensions._

  val reportBus = dep.reportBus
  def receive = {
    case ParseFlatFileRecord( record ) => record.asTaricCode match {
      case Right( r ) => reportBus ! ParsedAsTaric( r )
      case Left( m ) => log.error( m )
    }
  }
}

object TaricCodeConverterWorker {
  trait TaricCodeConverterDependencies extends ReportProducer
}

class ApplicationResources extends Actor with ActorLogging {


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
    log.debug( "Current version {} requested.", currentVersion )

    case ReplaceCurrentVersion( ver ) => currentVersion = ver
    log.debug( "New current version {} to be stored.", currentVersion )

    case FetchTaricUrls => sender ! TaricUrls(
      taricFtpUrl,
      TaricPathPattern( totalPath, totalPattern ),
      TaricPathPattern( difPath, difPattern )
    )
  }
}

class DebugLogger extends Actor with ActorLogging {
  override def receive = {
    case t:TaricCode if ( t.code.startsWith( "0304" ) ) => {
      log.debug( t.code toString )
    }
  }
}

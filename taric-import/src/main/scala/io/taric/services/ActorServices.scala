package io.taric
package services

import akka.actor._
import domains._
import concurrent.Future

import akka.pattern.pipe

import CommandBus._
import EventBus._


class RemoteResources( implicit d:FetchRemoteResources, e:EventProducer ) extends Actor with ActorLogging {

  import LocatingTaricFiles._

  private[this] def fetchFilterFileListing( pattern:String, url:String ) = for {
    fileNames <- d.fetchFileListing( url )
    filteredNames <- Future( fileNames.filter( filterFileType( pattern, _ ) ) )
  } yield filteredNames

  private[this] def latestVersion( filteredNames:List[String] ) = for {
    fileVersion <- Future( latestFileVersion( filteredNames ) )
  } yield fileVersion

  private[this] def fetchRemoteFileLines( url:String, fileName:String ) = for {
    lines <- d.fetchFilePlainTextLines( url, fileName )
    records <- Future( ( lines map FlatFileRecord ) )
    reports <- Future( ( records map ProducedFlatFileRecord ) )
  } yield reports

  private[this] def emitAll( futureRecords:Future[Stream[ProducedFlatFileRecord]] ) =
    for( records <- futureRecords ) yield records.foreach( e.eventBus ! _ )

  private[this] def listComputeLatestVer( pattern:String, url:String ) =  for {
    filteredFileListing <- fetchFilterFileListing( pattern, url )
    version <- latestVersion( filteredFileListing )
  } yield Listing( url, filteredFileListing, version )

  def receive = {
    case FetchListing( pattern, url ) => listComputeLatestVer( pattern, url ) pipeTo sender
    case FetchRemoteResource( url, fileName ) => emitAll( fetchRemoteFileLines( url, fileName ) )
  }
}


class ApplicationResources( implicit c:FetchConfigurationValues, e:EventProducer ) extends Actor with ActorLogging {

  var currentVersion = 0

  def receive = {
    // TODO 2012-12-31 (Magnus Andersson) Store this in AppDB or filesystem
    case FetchCurrentVersion => sender ! CurrentVersion( currentVersion )

    case ReplaceCurrentVersion( ver ) =>
      val oldVer = currentVersion
      currentVersion = ver
      e.eventBus ! ReplacedCurrentVersion( oldVer, ver )

    case FetchTaricUrls => sender ! TotDifUrls(
      c.getValue( TaricFtpUrl ),
      TaricPathPattern( c.getValue( TotPath ), c.getValue( TotPattern ) ),
      TaricPathPattern( c.getValue( DifPath ), c.getValue( DifPattern ) )
    )
  }
}

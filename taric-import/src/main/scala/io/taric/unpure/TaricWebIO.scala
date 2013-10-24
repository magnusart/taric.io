package io.taric
package unpure

import domains.FetchRemoteResources
import concurrent.Future
import java.net.URL
import java.io.{ SequenceInputStream, InputStreamReader, BufferedReader, InputStream }
import scalax.io.{ Resource, LongTraversable }

object TaricWebIO extends FetchRemoteResources {
  // All types of files.
  val filePattern = """\d{4}_(K|D)\w(\d\d)?(_(EN|SV))?.tot.gz.pgp""".r

  def fetchFileListing( url: String ): Future[List[String]] =
    Future( parseWebListing( fetchUrl( url ) ) toList )

  import StreamPipeline._
  def fetchFilePlainTextLines( url: String, fileName: String ): Future[Stream[String]] =
    Future( getLineRecords( decryptUnzip( downloadFile( s"$url$fileName" ) ) ) )

  private[this] def fetchUrl( url: String ) = Resource fromURL ( url ) lines ()

  private[this] def parseWebListing( doc: LongTraversable[String] ) = for {
    line ← doc
    fileName ← filePattern findFirstIn line
  } yield fileName

  private[this] def downloadFile( url: String ) = new URL( url ) openStream ()

  private[this] def getLineRecords( in: InputStream ) = getReader( in ) toStream

  private[this] def getReader( stream: InputStream ): LongTraversable[String] =
    Resource fromReader ( new BufferedReader( new InputStreamReader( stream ) ) ) lines ()

}


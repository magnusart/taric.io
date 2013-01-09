package io.taric.models

import java.io.InputStream
import java.util.zip.GZIPInputStream
import org.apache.commons.net.ftp.{FTPFile, FTPClient}
import org.bouncycastle.bcpg.InputStreamPacket

sealed trait Command
sealed trait Report

case object RegisterFSM
// Idle
case object ReadyToStartImport extends Report
// Preparing
case object FetchCurrentVersion extends Command
case class CurrentVersion(ver:Int) extends Report
case object FetchTaricUrls extends Command

case class TaricPathPattern(path:String, pattern:String)
case class TaricUrls(url:String, tot:TaricPathPattern, dif:TaricPathPattern) extends Report

case class VersionUrlsAggregate( ver:Int, urls:TaricUrls ) extends Report

case class StoreCurrentVersion(ver:Int) extends Command

case class BrowseFTP(ver:Int, url:String, tot:TaricPathPattern, dif:TaricPathPattern) extends Command

case class PathFileName(path:String, fileName:String)
case class OpenStreams(files: List[PathFileName], client: FTPClient) extends Command
case class StreamsOpened(streams: List[InputStream]) extends Report
case class BrowsingResult(fileNames:Option[List[PathFileName]] = None, ftpClient:Option[FTPClient] = None) extends Report

case class DecryptStream(stream:InputStream) extends Command
case class StreamDecrypted(stream:InputStream) extends Report
case class StreamsDecrypted(streams:List[InputStream]) extends Report

case class UnzipStream( stream:InputStream ) extends Command
case class StreamUnzipped( stream:InputStream ) extends Report
case class StreamsUnzipped( streams:List[InputStream] ) extends Report

case class ParseStream( stream:InputStream ) extends Command

case object ImportFinished extends Command

case class TaricTotalResourceFtp(url:String) extends Command
case class TaricDiffResourceFtp(url:String) extends Command

case class TaricKaResource(url:String) extends Command
case class TaricKaStream(stream:InputStream) extends Command
case class TaricKaDecryptedStream(stream: InputStream) extends Command
case class TaricKaUnzippedStream(stream: GZIPInputStream) extends Command
case class TaricKaCode(taricCode:TaricCode) extends Command

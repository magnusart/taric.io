package io.taric.models

import java.io.InputStream
import java.util.zip.GZIPInputStream
import akka.actor.ActorRef
import org.apache.commons.net.ftp.{FTPFile, FTPClient}

sealed trait Event

case class RegisterFSM(eventBus:ActorRef)
case class StartImport(tot:String, dif:String) extends Event
case class BrowsingResult(isSuccess:Boolean, fileNames:Option[List[FTPFile]] = None, ftpClient:Option[FTPClient] = None)
case object ImportFinished extends Event

case class TaricTotalResourceFtp(url:String) extends Event
case class TaricDiffResourceFtp(url:String) extends Event

case class TaricKaResource(url:String) extends Event
case class TaricKaStream(stream:InputStream) extends Event
case class TaricKaDecryptedStream(stream: InputStream) extends Event
case class TaricKaUnzippedStream(stream: GZIPInputStream) extends Event
case class TaricKaCode(taricCode:TaricCode) extends Event

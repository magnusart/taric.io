package io.taric

import java.io.InputStream
import scalax.io.managed.InputStreamResource
import java.util.zip.GZIPInputStream
import io.taric.Models.TaricCode
import scalax.io.LongTraversable
import akka.actor.ActorRef

object Events {
  sealed trait Event {
    def sender:Option[ActorRef]
  }

  case class TaricTotalResourceFtp(url:String, sender:Option[ActorRef] = None) extends Event
  case class TaricDiffResourceFtp(url:String, sender:Option[ActorRef] = None) extends Event

  case class TaricKaResource(url:String, sender:Option[ActorRef] = None) extends Event
  case class TaricKaStream(stream:InputStream, sender:Option[ActorRef] = None) extends Event
  case class TaricKaDecryptedStream(stream: InputStream, sender:Option[ActorRef] = None) extends Event
  case class TaricKaUnzippedStream(stream: GZIPInputStream, sender:Option[ActorRef] = None) extends Event
  case class TaricKaCode(taricCode:TaricCode, sender:Option[ActorRef] = None) extends Event

  sealed trait Data

  case object Uninitialized extends Data
  case object BrowsingFTP extends Data
  case object Unencrypting extends Data
  case object Extracting extends Data
  case object Parsing extends Data


}
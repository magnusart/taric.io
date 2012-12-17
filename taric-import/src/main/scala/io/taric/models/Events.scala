package io.taric.models

import java.io.InputStream
import java.util.zip.GZIPInputStream
import akka.actor.ActorRef

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

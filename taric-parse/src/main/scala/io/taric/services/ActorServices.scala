package io.taric
package services

import akka.actor._
import domains._
import concurrent.Future

import akka.pattern.pipe

import CommandBus._
import EventBus._

class Parser extends Actor with ActorLogging {
  def receive = {
    case _ =>
  }
}

class TaricCodeConverterWorker( implicit e:EventProducer ) extends Actor with ActorLogging {

  import TaricCodeExtensions._

  def receive = {
    case ParseFlatFileRecord( record ) => record.asTaricCode match {
      case Right( rec ) => e.eventBus ! ParsedAsTaric( rec )
      case Left( m ) => log.error( m )
    }
  }
}
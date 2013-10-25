package io.taric
package services

import akka.actor._
import domains._
import concurrent.Future

import akka.pattern.pipe

import CommandBus._
import EventBus._

import scala.collection.immutable.TreeSet

class Parser( implicit c: CommandProducer ) extends Actor with ActorLogging {
  def receive = {
    case ProducedFlatFileRecord( record, batchId ) ⇒ c.commandBus ! ParseFlatFileRecord( record, batchId )
  }
}

class BatchAggregator( implicit c: CommandProducer )
  extends Actor
  with ActorLogging {

  implicit object ExistingOrdering extends Ordering[ExistingTaricCode] {
    def compare( a: ExistingTaricCode, b: ExistingTaricCode ) = {
      val ct = if ( a.codeType == Import ) -1 else 1

      val ed = {
        if ( b.endDate.isDefined && a.endDate.isEmpty ) -1
        else if ( a.endDate.isDefined && b.endDate.isEmpty ) 1
        else if ( a.endDate.isEmpty && b.endDate.isEmpty ) 0
        else a.endDate.get compare b.endDate.get
      }

      a.code.compare( b.code ) +
        a.startDate.compare( b.startDate ) +
        ed +
        ct
    }
  }

  var batchesLeft: Int = -1
  var fileNames: Map[String, Int] = Map.empty
  var batchTotal: Map[String, Int] = Map.empty
  var existingCodes: TreeSet[ExistingTaricCode] = TreeSet[ExistingTaricCode]()
  var newCodes: List[NewTaricCode] = List.empty
  var replaceCodes: List[ReplaceTaricCode] = List.empty

  def resetActor {
    batchesLeft = -1
    fileNames = Map.empty
    batchTotal = Map.empty
    newCodes = List.empty
    replaceCodes = List.empty
    existingCodes = TreeSet[ExistingTaricCode]()
  }

  def increaseFileNamesCounter( fileName: String ) = {
    val fileNums = fileNames
      .get( fileName )
      .map( _ + 1 )
      .getOrElse( 1 )

    fileNames = fileNames + ( fileName -> fileNums )
  }

  def checkComplete( fileName: String ): Boolean = {
    val cur: Int = fileNames( fileName )
    val total: Option[Int] = batchTotal.get( fileName )

    total.map( _ == cur ).getOrElse( false )
  }

  def tryToFinish( fileName: String ) {
    if ( checkComplete( fileName ) ) batchesLeft = batchesLeft - 1

    if ( batchesLeft == 0 ) println( "FINISHED!" )
  }

  def handleFileName( fileName: String ) {
    increaseFileNamesCounter( fileName )
    tryToFinish( fileName )
  }

  def handleExistingCode( code: ExistingTaricCode, fileName: String ) {
    existingCodes union Set( code )
    handleFileName( fileName )
  }

  def handleNewCode( code: NewTaricCode, fileName: String ) {
    newCodes = code +: newCodes
    handleFileName( fileName )
  }

  def handleReplaceCode( code: ReplaceTaricCode, fileName: String ) {
    replaceCodes = code +: replaceCodes
    handleFileName( fileName )
  }

  def receive = {
    case TotalBatches( numBatches )                      ⇒ batchesLeft = numBatches
    case ParsedAsTaric( e: ExistingTaricCode, fileName ) ⇒ handleExistingCode( e, fileName )
    case ParsedAsTaric( n: NewTaricCode, fileName )      ⇒ handleNewCode( n, fileName )
    case ParsedAsTaric( r: ReplaceTaricCode, fileName )  ⇒ handleReplaceCode( r, fileName )
    case bc @ BatchCompleted( fileName, noMessages )     ⇒ batchTotal + ( fileName -> noMessages )
    case ResetState                                      ⇒ resetActor
  }
}

class TaricCodeConverterWorker( implicit e: EventProducer )
  extends Actor
  with ActorLogging {

  import TaricCodeExtensions._

  def receive = {
    case ParseFlatFileRecord( record, batchId ) ⇒ record.asTaricCode match {
      case Right( rec ) ⇒ e.eventBus ! ParsedAsTaric( rec, batchId )
      case Left( m )    ⇒ log.error( m )
    }
  }
}

class ProductCodeRepository( implicit e: EventProducer )
  extends Actor
  with ActorLogging {

  def receive = {
    case ParsedAsTaric( e: ExistingTaricCode, batchId ) ⇒
    case ParsedAsTaric( n: NewTaricCode, batchId )      ⇒
    case ParsedAsTaric( r: ReplaceTaricCode, batchId )  ⇒
  }
}
package io.taric
package services

import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, FlatSpec}
import org.scalatest.matchers.ShouldMatchers
import akka.actor.{Props, ActorRef, ActorSystem}
import concurrent.Future
import io.taric.TestData
import akka.routing.SmallestMailboxRouter
import scala.concurrent.duration._
import TestSetup._
import io.taric.domains.{FlatFileRecord}
import services.EventBus._
import services.ReportBus._
import services.CommandBus._

/**
 * File created: 2013-01-16 14:43
 *
 * Copyright Solvies AB 2013
 * For licensing information see LICENSE file
 */
class ProductCodeConverterActorSpec( _system:ActorSystem ) extends TestKit( _system ) with ImplicitSender with FlatSpec with ShouldMatchers with BeforeAndAfterAll {

  def this( ) = this( ActorSystem( "TestSystem2", testConf) )

  override def afterAll {
    system.shutdown( )
  }

  "ProductCode converter" should "given flat line specs emit taric product codes" in {
    val probe = TestProbe( )

    implicit val mockedConverterDep = new ReportProducer {
      def reportBus:ActorRef = probe.ref
    }

    val converterRef = system.actorOf(
      Props( new TaricCodeConverterWorker() ).withRouter( SmallestMailboxRouter( 3 ) ), "converter-router"
    )

    val kaCommands = for {
      line <- TestData.kaFile.split("\n")
    } yield ParseFlatFileRecord( FlatFileRecord( line ) )

    val kiCommands = for {
      line <- TestData.kiFile.split("\n")
    } yield ParseFlatFileRecord( FlatFileRecord( line ) )

    val kjCommands = for {
      line <- TestData.kjFile.split("\n")
    } yield ParseFlatFileRecord( FlatFileRecord( line ) )

    kaCommands.foreach( converterRef ! _ )
    kiCommands.foreach( converterRef ! _ )
    kjCommands.foreach( converterRef ! _ )

    val ms = checkForTaricCodes(probe, 64)
    ms.length should be (64)
    ms.reduceLeft(_ && _) should be (true)
    probe.expectNoMsg( 20 millis )
  }

  private[this] def checkForTaricCodes( probe:TestProbe, maxMessages:Int ) = probe
    .receiveWhile( 500 millis, 20 millis, maxMessages ) {
    case r:ParsedAsTaric => true
    case e@_ => println( s"Got incorrect message $e." ); false
  }
}

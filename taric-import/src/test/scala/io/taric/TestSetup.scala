package io.taric

import com.typesafe.config.{ ConfigFactory, Config }

/**
 * File created: 2013-01-19 18:27
 *
 * Copyright Solvies AB 2013
 * For licensing information see LICENSE file
 */
object TestSetup {
  val testConf: Config = ConfigFactory.parseString( """
      akka {
        event-handlers = ["akka.event.Logging$DefaultLogger"]
        loglevel = "ERROR"
        actor {
          provider = "akka.actor.LocalActorRefProvider"
        }
      }""" )
}

package io.taric.controllers

import akka.actor.{ActorLogging, Actor, ActorRef, FSM}
import akka.pattern.{ask, pipe}
import io.taric.models._
import io.taric.ImportApp._
import akka.util.Timeout._
import akka.util.duration._
import org.apache.commons.net.ftp.{FTPFile, FTPClient}
import akka.routing.{Deafen, Listen}

/**
 * Copyright Solvies AB 2012
 * User: magnus
 * Date: 2012-12-17
 * Time: 21:57
 */

class TaricImportFSM extends Actor with FSM[State, Data] {
  var eventBuses:List[ActorRef] = List.empty

  startWith(Deaf, Uninitialized)

  private def registerWithEventBus(eventBus:ActorRef, source:ActorRef) = {
    eventBuses = eventBus +: eventBuses
    eventBus ! Listen(self)
  }

  when(Deaf) {
    case Event(RegisterFSM(eventBus), _) =>
      registerWithEventBus(eventBus, self)
      goto(Idle)
  }

  when(Idle) {
    case Event(StartImport(tot, dif), _) =>
      log.debug("Starting taric import.")
      //eventBus ! TaricTotalResourceFtp(tot)
      goto(BrowsingFTP) using (TaricFtpUrls(tot, dif))
    case Event(RegisterFSM(eventBus), _) =>
      registerWithEventBus(eventBus, self)
      stay
  }

  when(Cleanup) {
    case Event(_, fc@FTPConnection(client, streams)) =>
    // Close Open Streams
    for {
      s <- streams.getOrElse(List.empty)
      if (s.available() > 0)
    } yield s.close()

    // Close FTP Connection
    client.map {
      con => if(con.isConnected) {
        con.logout()
        con.disconnect()
      }
    }
    goto(Idle)
  }

  when(BrowsingFTP) {
    case Event(BrowsingResult(isSuccess, fileNames, client), tfu:TaricFtpUrls) =>
      log.debug("Browsing FTP stream.")
      if(isSuccess) goto(Decrypting) else goto(Cleanup) using(FTPConnection(client))
  }

  when(Decrypting) {
    case _ =>
      log.debug("Decrypting stream.")
      goto(Unzipping)
  }

  when(Unzipping) {
    case _ =>
      log.debug("Unzipping stream.")
      goto(Parsing)
  }

  when(Parsing) {
    case _ =>
      log.debug("Parsing stream.")
      goto(Persisting)
  }

  when(Persisting) {
    case _ =>
      log.debug("Persisting stream.")
      goto(Cleanup)
  }

  // TODO Magnus Andersson (2012-12-18) Do cleanup steps here to ensure connection is closed.
  onTermination{
    case StopEvent(FSM.Normal, state, data)         ⇒ eventBuses foreach( _ ! Deafen(self) )
    case StopEvent(FSM.Shutdown, state, data)       ⇒ eventBuses foreach( _ ! Deafen(self) )
    case StopEvent(FSM.Failure(cause), state, data) ⇒ eventBuses foreach( _ ! Deafen(self) )
  }

  initialize
}

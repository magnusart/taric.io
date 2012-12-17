
import akka.actor.{ActorLogging, Actor, ActorRef, FSM}
object AkkaFSMWorkSheet{
  sealed trait State
  sealed trait Data
  sealed trait Event
  case object Idle extends State
  case object Uninitialized extends Data

  case object Running extends State
  case class Message(msg:String) extends Data
  case object Startup extends Event
  case object Tell extends Event
  class ImportController extends Actor with FSM[State, Data] {
    startWith(Idle, Uninitialized)
    when(Idle) {
      case Event(Startup, Message(msg)) =>
        log.debug("Starting up with messsage {}.", msg)
        goto(Running) using(Message(msg))
    }

    onTransition {
      case Idle -> Running => log.debug("Going into active mode.")
    }

    when(Running) {
      case Event(Tell, null) => stateData match {
        case Message(msg) => log.debug("Got a message for you!: {}.", msg)
          stay
      }
    }

    whenUnhandled {
      case Event(e, s) ⇒
        log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
        stay
    }

    initialize
  }





}
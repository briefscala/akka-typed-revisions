package com.ereactive.examples.akka.alarm

object Main {

  import Alarm._
  import Door._
  import akka.actor.typed._
  import akka.actor.typed.scaladsl.Behaviors
  import akka.util.Timeout

  import scala.concurrent.duration._
  import scala.util.{Failure, Success}

  def main(args: Array[String]): Unit = {

    implicit val timeout: Timeout = Timeout(5.seconds)

    val system = ActorSystem(root(pin = 555), "system")
  }

  def aDoor(alarm: ActorRef[AlarmCmd], state: DoorState = Closed)(
    implicit timeout: Timeout): Behavior[DoorProtocol] =
    Behaviors.setup { ctx =>
      def alarmStatus(): Unit = ctx.ask(alarm)(GetAlarmStatus) { // (1.)
        case Success(status: AlarmActivated.type) =>
          ctx.log.info("The alarm is on. Can't open the door!")
          Closed
        case Success(status: AlarmDeactivated.type) =>
          ctx.log.info("The alarm is off. Opening the door.")
          Opened
        case Failure(exception) => Closed
      }

      Behaviors.receiveMessage {
        case Open | Close => alarmStatus()
          Behaviors.same
        case Opened => aDoor(alarm, Opened)
        case Closed => aDoor(alarm, Closed)
      }
    }

  def anAlarm(pinCode: Int, status: AlarmState = AlarmDeactivated): Behavior[AlarmCmd] =
    Behaviors.receive { (ctx, msg) => msg match {
      case GetAlarmStatus(repyTo: ActorRef[AlarmState]) =>
        repyTo ! status
        Behaviors.same
      case ToggleAlarm(`pinCode`) => status match {
        case AlarmActivated =>
          anAlarm(pinCode, AlarmDeactivated)
        case AlarmDeactivated =>
          anAlarm(pinCode, AlarmActivated)
      }
      case ToggleAlarm(_) => Behavior.same
    }}

  def root(pin: Int)(implicit timeout: Timeout)
  : Behavior[String] = Behaviors.setup { ctx =>
    val alarm = ctx.spawn(anAlarm(pin), "alarm")
    val door = ctx.spawn(aDoor(alarm), "door")

    /**
      * We'll use the behavior `withTimers` to periodically toggle the alarm and try opening the door
      */
    Behaviors.withTimers { timers => // (2.)
      timers.startPeriodicTimer("alarm", "toggleAlarm", 3.seconds)
      timers.startPeriodicTimer("door", "tryOpen", 1.seconds)
      Behaviors.receiveMessage {
        case "toggleAlarm" => alarm ! ToggleAlarm(pin)
          Behaviors.same
        case "tryOpen" => door ! Open
          Behaviors.same
      }
    }
  }

  object Alarm {
    sealed trait AlarmCmd
    case class GetAlarmStatus(repyTo: ActorRef[AlarmState]) extends AlarmCmd
    case class ToggleAlarm(pinCode: Int) extends AlarmCmd

    sealed trait AlarmState
    case object AlarmActivated extends AlarmState
    case object AlarmDeactivated extends AlarmState
  }

  object Door {
    sealed trait DoorProtocol

    sealed trait DoorCmd extends DoorProtocol
    case object Open extends DoorCmd
    case object Close extends DoorCmd

    sealed trait DoorState extends DoorProtocol
    case object Opened extends DoorState
    case object Closed extends DoorState
  }
}

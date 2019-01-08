package com.ereactive.examples.akka.alarm

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import com.ereactive.examples.akka.alarm.Alarm._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Main {
  def main(args: Array[String]): Unit = {

    val system: ActorSystem[SystemProtocol] = ActorSystem(root, "alarm")

    //system !
    system ! GetAlarmStatus

  }

  trait SystemProtocol
  case class AlarmResponse(message: String) extends SystemProtocol
  case object GetAlarmStatus extends SystemProtocol

  val alarmBehavior = Alarm(initialPinCode = 2121)

  val root: Behavior[SystemProtocol] = Behaviors.setup { ctx =>

    val anAlarm = ctx.spawn(alarmBehavior.anAlarm(), "alarm")

    implicit val timeout = Timeout(5, SECONDS)

    def askForStatus() = ctx.ask(anAlarm)(GetAlarmState) {
      case Success(value) => value match {
        case ActiveAlarm => AlarmResponse("alarm active")
        case InactiveAlarm => AlarmResponse("alarm not active")
        case LockedAlarm => AlarmResponse("alarm locked")
      }
      case Failure(exception) => AlarmResponse(s"error while retrieving the alarm status: $exception")
    }

    val waitingForResponse: Behavior[SystemProtocol] = Behaviors.receiveMessage {
      case AlarmResponse(response) =>
        ctx.log.info(s"the alarm response was: $response")
        Behaviors.same
    }

    val receivingCommands: Behavior[SystemProtocol] = Behaviors.receiveMessage {
      case GetAlarmStatus =>
        askForStatus()
        waitingForResponse
    }

    receivingCommands
  }
}

package com.ereactive.examples.akka.alarm

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.ereactive.examples.akka.alarm.Alarm._

class Alarm (initialPinCode: Int) {
  def anAlarm(pinCode: Int = initialPinCode, state: AlarmState = InactiveAlarm, lockedAt: Int = 3)
  : Behavior[AlarmCommand] =
    Behaviors.receiveMessage {
      case ActivateAlarm(`pinCode`) => anAlarm(pinCode, ActiveAlarm)
      case DeactivateAlarm(`pinCode`) => anAlarm(pinCode, InactiveAlarm)
      case GetAlarmState(replyTo) => replyTo ! state
        Behaviors.same
      case LockAlarm(`lockedAt`) =>
        val securityPinCode = 12345 // get it from a database
        lockedAlarm(securityPinCode)
      case _ => Behaviors.same
    }

  def lockedAlarm(securityPinCode: Int, lockedState: AlarmState = LockedAlarm): Behavior[AlarmCommand] =
    Behaviors.receiveMessage {
      case UnlockedAlarm(`securityPinCode`, newPinCode) =>
        anAlarm(newPinCode, InactiveAlarm)
      case GetAlarmState(replyTo) => replyTo ! lockedState
        Behaviors.same
      case _ => Behaviors.same
    }
}

object Alarm {
  def apply(initialPinCode: Int): Alarm = new Alarm(initialPinCode)
  sealed trait AlarmCommand
  case class ActivateAlarm(pinCode: Int) extends AlarmCommand
  case class DeactivateAlarm(pinCode: Int) extends AlarmCommand
  case class GetAlarmState(replyTo: ActorRef[AlarmState]) extends AlarmCommand
  case class LockAlarm(incorrectPinCodeTimes: Int) extends AlarmCommand
  case class UnlockedAlarm(securityPinCode: Int, newPinCode: Int) extends AlarmCommand

  sealed trait AlarmState
  case object ActiveAlarm extends AlarmState
  case object InactiveAlarm extends AlarmState
  case object LockedAlarm extends AlarmState
}

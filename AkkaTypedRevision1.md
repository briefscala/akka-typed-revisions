## Akka Typed Revision I

### A quick look at traditional Akka

You can think of Akka Typed as the same Akka you know but with more types around. If you have significant experience with Akka this is probably what you expect however, Akka Typed have significant differences that are a departure from the traditional mindset in Akka. It is in tune with Functional Programming as you might know it from other parts in the Scala scene while maintaining its distributed and asynchronous nature.

Let's take a look. Traditional Akka actors received messages on the `Receive` method which is largely untyped represented by `PartialFunction[Any, Unit]`. Let's see an example to recap on this aspect of traditional actors.

```scala
import akka.actor._
import akka.Done

val system: ActorSystem = ActorSystem("my-actor-system") // (1)

class MyActor extends Actor { // (2)
  def receive: PartialFunction[Any, Unit] = { // (3)
    case "hello" => println("hello, friend!")
    case Done => self ! PoisonPill // dead actor
  }
}

val myActor = system.actorOf(Props(new MyActor)) // (4)

myActor ! "hello" // hello, friend! (5)
myActor ! Done
myActor ! "hello again!" // this wouldn't produce any result as the myActor is now dead
```
1. Create and return the ActorSystem which hold the guardian actor named `user`.
2. Extends the trait Actor provided by Akka bringing in the abstract receive method.
3. The abstract `receive` method is typed with `akka.actor.Actor.Receive` which is a type alias to `PartialFunction[Any, Unit]`.
4. `system` can be used to spawn new actors by calling `.actorOf` the the new actor's `Prop` simply obtained by passing an instance of `MyActor`.
5. You can use the tell operator `!` to send messages to `myActor` which would allow sending whatever message you have at hand to it, even if the actor is already dead.

There is one more aspect of untyped actors the is worth revising here before we take a look at Akka Typed actors and that is `context.become`.

```scala
import akka.actor._
import akka.Done

class MyActor extends Actor { 

  def receive: PartialFunction[Any, Unit] = helloOrDead // (1)
  
  def helloOrDead: PartialFunction[Any, Unit] = { 
    case "hello" => println("hello, friend!")
    case "-$10" => println("that is nasty!")
      context.become(angryOrHello) // (2)
    case Done => self ! PoisonPill
  }
  
  def angryOrHello: PartialFunction[Any, Unit] = {
    case "hello" => println("go away!")
    case "$10" => 
      println("thanks!")
      context.become(helloOrDead) // (2)
  }
}
```
1. In this example the `receive` method is initially implemented with `helloOrDead`.
2. We use `context.become` to change what do we accept as messages right after the current message.

Although `context.become` is extremely useful for ensuring that steps are followed in a predictable order it come with many drawbacks. We won't dive onto all of them here but just on the fact that it is very hard to predict, when sending messages to `myActor` if an known messages will be handled. So, not only traditional actors are untyped (in effect), but they can change their behavior at runtime to stop handling messages that they were known to handle. There is no discoverability of that fact that could protect the code against a naive refactoring or even if it wasn't naive.

### Akka Typed Behaviors

The aim of this post is to revise Akka Typed and explore not only what change in terms of the api but how much more safety it brings us and what increase productivity we can expect from developing Akka Typed distributed applications vs untyped applications.

Let's take as an example a bulgar alarm system which is an example that has been explored before by Konrad Malawski [here](https://www.slideshare.net/ktoso/akka-typed-quick-talk-jfokus-2018). We will expand on it but it is good to start from known basis.

```scala
import akka.actor.typed._ // (1)
import akka.actor.typed.scaladsl.Behaviors
import akka.Done

def anAlarm(pinCode: Int, status: AlarmState = AlarmDeactivated): Behavior[AlarmCmd] = // (2)
  Behaviors.receiveMessage {
    case ActivateAlarm(`pinCode`) => 
      println("alarm activated")
      anAlarm(pinCode, AlarmActivated) // (3)
    case DeactivateAlarm(`pinCode`) => 
      println("alarm deactivated")
      anAlarm(pinCode, AlarmDeactivated)
  }

val alarmSystem = ActorSystem(anAlarm(222), "alarm") // (4)

alarmSystem ! ActivateAlarm(222) // alarm activated
alarmSystem ! DeactivateAlarm(222) // alarm deactivated

/*alarmSystem ! Done*/ // doesn't compile

sealed trait AlarmCmd // (5)
case class ActivateAlarm(pinCode: Int) extends AlarmCmd
case class DeactivateAlarm(pinCode: Int) extends AlarmCmd 

sealed trait AlarmState // (5)
case object AlarmActivated extends AlarmState
case object AlarmDeactivated extends AlarmState
```
1. Import the actor typed package 
2. A constructor for the alarm `Behavior`. Note that we don't need to have a class that extends `Actor` anymore. We simply have to define the behavior of our 
actor based on receiving messages given by its commands.
3. In the behavior definition we said that when we get an `AlarmCmd` message we react to it by printing a message and returning a new behavior with the new
state
4. The ActorSystem no longer have a guardian as root, user actor, as in the untyped example. Instead it takes a behavior and a name as the root actor. Another
difference is that now you can send messages directly to `system` and these will be received by the root actor.
5. The `Alarm` command and state protocols

In this trivial alarm example if we try to activate or deactivate the alarm we get the expected behavior but we couldn't send `Done` to our alarm. The compiler will have our back here and this means huge gain on productivity while developing large Akka systems but even if those systems aren't big. It is too easy to forget what I wrote yesterday in that other file in another package. With Akka Typed if you forget some aspect of the protocol the compiler have your back immediately and get you back on track.

### Ask Pattern

Ok, by now you should be wondering how we reply to the the interrogation messages, `GetState` or similar, since it could really be any actor and all typed actors need us to be aware of their protocol. The Akka Typed solution for that is straight forward. As part of our protocol we can accept a `replyTo` actor reference (in place of the implicit sender in traditional actors) of the type of our state/reply protocol. The requester actor will be providing the adapter function to convert from the internal state or reply to its own protocol. Let's see this in our alarm system to get a better feeling for it.

```scala
import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import scala.util.{Success, Failure}
import scala.concurrent.duration._
import akka.util.Timeout
import akka.Done
import Alarm._
import Door._

def main(args: Array[String]): Unit = {

  implicit val timeout: Timeout = Timeout(5.seconds)

  val system = ActorSystem(root(pin = 555), "system")
}

def aDoor(alarm: ActorRef[AlarmCmd], state: DoorState = Closed)(
  implicit timeout: Timeout): Behavior[DoorProtocol] =
    Behaviors.setup { ctx =>
      def alarmStatus(): Unit = ctx.ask(alarm)(GetAlarmStatus) { // (1)
        case Success(AlarmActivated) =>
          ctx.log.info("The alarm is on. Can't open the door!")
          Closed
        case Success(AlarmDeactivated) =>
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
  }}

def root(pin: Int): Behavior[String] = Behaviors.setup { ctx =>
  val alarm = ctx.spawn(anAlarm(pin), "alarm")
  val door = ctx.spawn(aDoor(alarm), "door")

  /**
    * We'll use the behavior `withTimers` to periodically toggle 
    * the alarm and try opening the door
    */
  Behaviors.withTimers { timers => // (2)
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
```

1. The `ctx.ask` takes the target actor, the alarm, and its interrogation command (which can be converted to function of type `ActorRef[Response] => Request`) followed by the adapter function that convert the target's responses into our expected commands.
2. `Behaviors.withTimers` allows to send us messages that will be received later, either just once or periodically, and they will become part of our protocol.

In the next session we will continue from here and explore another way that Akka Typed provides for communicating with other actors and will see how these plays out when actors are distributed.

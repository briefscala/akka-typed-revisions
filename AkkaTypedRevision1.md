## Akka Typed Revision I

### A quick look at traditional Akka

You can think of Akka Typed as the same Akka you know but with types. 
If you have significant experience with Akka this is probably what you expect however, Akka Typed have significant difference that are
a departure from traditional Akka. Akka Type is in tune with Functional Programing for distributed systems adding type safety at a distance.

Let's make a pause here for a moment and reflect what type safety really give us. Clearly, the compiler can help us discovering bugs that
could otherwise gone unnoticed until Black Friday when that the holiday offer part of the code gets executed and the application crashed.
I would argue that although these things has happened and unfortunately still happen the compiler protects only minimally against bugs in general.
There are another two aspect about developing where the compiler help is not only significant but makes a compelling economical argument. The first is 
productivity. Even a junior developer after just a couple of months using a robust compiler can be significantly more efficient than a developer 
in their mid-career developer that doesn't have a compiler at hand for their every day coding. The compiler, statically check your code as you type and fails fast.
Very fast. In fact, it fails instantly helping us recover from failure and correct the code on the spot. I would argue that many of these errors, where there
isn't a compiler, will be caught before going to production but having a compiler means that they are corrected from the get go and that translates in massive
productivity gains. The compiler does this so fast and we are so adept to it that we not longer realised, unless we stop and ponder on it because someone asked,
for example, why do we need to learn about higher kinded types adding that it seems like a burden unrelated to programming. 

But we are not that often writing new software from scratch. We do but most of our time is actually spent maintaining software. Maybe it is our software. Or a software
that we made on the job but almost immediately after we made it new business rules came in or an important library api got changed and we need to refactor our code.
This put us in a situation where the code isn't immediately recognizable and making changes is a lot harder. I would argue that as a business owner or as a developer
you want the best compiler that you can get your hands on.

The second reason that you want to have types is because types tell the story and the intention of the software so you can follow the types 
and only occasionally dive into the implementation to find out more about it when you need to. Akka Typed capitalizes on both of these
and that means that it is a significant departure from untyped Akka.

Traditional Akka actors received messages on the `Receive` method which is largely untyped represented by `PartialFunction[Any, Unit]`. Let's see an example to
recap on this aspect of traditional actors

```scala
import akka.actor._
import akka.Done

val system: ActorSystem = ActorSystem("my-actor-system") // (1.)

class MyActor extends Actor { // (2.)
  def receive: PartialFunction[Any, Unit] = { // (3.)
    case "hello" => println("hello, friend!")
    case Done => self ! PoisonPill // dead actor
  }
}

val myActor = system.actorOf(Props(new MyActor)) // (4.)

myActor ! "hello" // hello, friend! (5.)
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

  def receive: PartialFunction[Any, Unit] = helloOrDead // (1.)
  
  def helloOrDead: PartialFunction[Any, Unit] = { 
    case "hello" => println("hello, friend!")
    case "-$10" => println("that is nasty!")
      context.become(angryOrHello) // (2.)
    case Done => self ! PoisonPill
  }
  
  def angryOrHello: PartialFunction[Any, Unit] = {
    case "hello" => println("go away!")
    case "$10" => 
      println("thanks!")
      context.become(helloOrDead) // (2.)
  }
}
```
1. In this example the `receive` method is initially implemented with `helloOrDead`.
2. We use `context.become` to change what do we accept as messages right after the current message.

Although `context.become` is extremely useful for ensuring that steps are followed in a predictable order it come with many drawbacks. 
We won't dive onto all of them here but just on the fact that it is very hard to predict, when sending messages to `myActor` if an known messages will be handled.
So, not only traditional actors are untyped (in effect), but they can change their behavior at runtime to stop handling messages that they were known to handle. There is no
discoverability of that fact that could protect the code against a naive refactoring or even if it wasn't naive.

### Akka Type Behaviors

The aim of this post is to revise Akka Typed and explore not only what change in terms of the api but how much more safety it brings us and what increase productivity we can 
expect from developing Akka Typed distributed applications vs untyped applications.

Let's take as an example a bulgar alarm system which is an example that has been explored before by Konrad Malawski [here](https://www.slideshare.net/ktoso/akka-typed-quick-talk-jfokus-2018).
We will expand on it but it is good to start from known basis.

```scala
import akka.actor.typed._ // (1.)
import akka.actor.typed.scaladsl.Behaviors
import akka.Done
import Alarm._ // (2.)

def anAlarm(pinCode: Int, status: AlarmState = AlarmDeactivated): Behavior[AlarmCmd] = // (3.)
  Behaviors.receiveMessage {
    case ActivateAlarm(`pinCode`) => 
      println("alarm activated")
      anAlarm(pinCode, AlarmActivated) // (4.)
    case DeactivateAlarm(`pinCode`) => 
      println("alarm deactivated")
      anAlarm(pinCode, AlarmDeactivated)
  }

val alarmSystem = ActorSystem(anAlarm(222), "alarm") // (5.)

alarmSystem ! ActivateAlarm(222) // alarm activated
alarmSystem ! DeactivateAlarm(222) // alarm deactivated

//alarmSystem ! Done // doesn't compile

object Alarm {
  sealed trait AlarmCmd
  case class ActivateAlarm(pinCode: Int) extends AlarmCmd
  case class DeactivateAlarm(pinCode: Int) extends AlarmCmd 
  
  sealed trait AlarmState
  case object AlarmActivated extends AlarmState
  case object AlarmDeactivated extends AlarmState
}
```
1. Import the actor typed package 
2. Import out `Alarm` protocol, the commands and the state protocol
3. A constructor for the alarm `Behavior`. Note that we don't need to have a class that extends `Actor` anymore. We simply have to define the behavior of our 
actor based on receiving messages given by its commands.
4. In the behavior definition we said that when we get an `AlarmCmd` message we react to it by printing a message and returning a new behavior with the new
state
5. The ActorSystem no longer have a guardian as root, user actor, as in the untyped example. Instead it takes a behavior and a name as the root actor. Another
difference is that now you can send messages directly to `system` and these will be received by the root actor.

In this trivial alarm example if we try to activate or deactivate the alarm we get the expected behavior but we couldn't send `Done` to our alarm. The compiler
will have our back here and this means huge gain on productivity while developing large Akka systems but even if those systems aren't big. It is too easy to forget
what I wrote yesterday in that other file in another package. With Akka Typed if you forget some aspect of the protocol the compiler have your back immediately and 
get you back on track.


### Ask Pattern

Ok, by now you should be wondering how we reply to the the interrogation messages, `GetState` or similar, since it could really be any actor and all typed actors need us to be aware of their
protocol. The Akka Typed solution for that is straight forward. As part of our protocol we can accept a `replyTo` actor reference (in place of the implicit sender
in traditional actors) of the type of the state/reply protocol. The requester actor will be providing the adapter function to convert from the internal state or reply to its
own protocol. Let's see this in our alarm system to get a better feeling for it.

```scala
import akka.actor.typed._ 
import akka.actor.typed.scaladsl.Behaviors
import akka.Done
import Alarm._ 
import Door._
import scala.util.{Success, Failure}
import scala.concurrent.duration._
import akka.util.Timeout

implicit val timeout: Timeout = Timeout(5.seconds)

def aDoor(alarm: ActorRef[AlarmCmd], state: DoorState = Closed): Behavior[DoorProtocol] =
  Behaviors.setup { ctx =>
    def alarmStatus(): Unit = ctx.ask(alarm)(GetAlarmStatus) { // (1.)
      case Success(status: AlarmActivated.type) => 
        ctx.log.info("The alarm is on. Can't open the door!")
        Closed
      case Success(status: AlarmDeactivated.type) => 
        ctx.log.info("The alarm if off. Opening the door!")
        Opened
      case Failure(exception) => Closed
    }

    Behaviors.receiveMessage {
      case Open | Close => alarmStatus()
        Behaviors.same
      case Opened => aDoor(alarm, Opened)
      case Closed =>aDoor(alarm, Closed)
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

def root(): Behavior[String] = Behaviors.setup { ctx =>
  val pin = 54321
  val alarm = ctx.spawn(anAlarm(pin), "alarm")
  val door = ctx.spawn(aDoor(alarm), "door")
  
  /**
  * We'll use the behavior `withTimers` to periodically toggle the alarm and try opening the door
  */
  Behaviors.withTimers { timers => // (2.)
    timers.startPeriodicTimer("alarm", "changeAlarm", 3.seconds)
    timers.startPeriodicTimer("door", "tryDoor", 1.seconds)
    Behaviors.receiveMessage {
      case "changeAlarm" => alarm ! ToggleAlarm(pin)
        Behaviors.same
      case "tryDoor" => door ! Open
        Behaviors.same
    }
  }
}

val system = ActorSystem(root(), "system")

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

1. The `ctx.ask` takes the target actor, the alarm, and its interrogation command (which can be converted to function of type `ActorRef[Response] => Request`) followed by the adapter
function that convert the target's responses into our expected commands.
2. `Behaviors.withTimers` allows to send us messages that will be received later, either just once or periodically, and they will become part of our protocol.

In the next session we will continue from here and explore another way that Akka Type provides for communicating with other actors and will see how these plays out when actors are
distributed.

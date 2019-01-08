## Akka Typed Revision I

### A quick look at traditional Akka

You can think of Akka Typed as the same Akka you know but with types. 
If you have significant experience with akka this is probably what you expect however, Akka Typed have significant difference that are
a departure from untyped Akka. Akka Type is in tune with Functional Programing for distributed systems and add to that type safety.

Let's make a pause here for a moment and reflect what type safety really give us. Clearly the compiler can help us discovering bugs that
could otherwise gone unnoticed until Black Friday when that the holiday offer part of the code gets executed and the application crashed.
I would argue that although these things has happened and unfortunately still happen the compiler protects only minimally against bugs.
There are another two aspect about developing where the compiler help is not only significant but makes economical sense. The first is 
productivity. Even a junior developer after just a couple of months using the compiler can be significant more efficient than a developer 
in their mid-career that doesn't have a compiler at their disposition. The compiler statically checks your code as you type and fails fast.
In fact it fails instantly helping us recover from the failure and correct the code on the spot. I argue that many of these errors, where there
isn't a compiler, will be caught before going to production but having a compiler means that they are corrected from the get go.

The second reason that you want to have types is because types tell the story and the intention of the software so you can follow the types 
and only occasionally dive into the implementation to find out more about it when you need to. Akka Typed capitalizes on both of these
and that means that it is a significant departure from untyped Akka.

Traditional Akka actors received messages on the `Receive` method which is largely untyped represented by `PartialFunction[Any, Unit]`. Let's see an example to
recap this aspect of traditional actors

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
So not only traditional actors are untyped (in effect) but they can change their behavior at runtime to stop handling messages that they were known to handle. There is no
discoverability of the fact that could protect the code against a naive refactoring or even if it wasn't naive.

The aim of this post to revise Akka Typed and explore not only what change in terms of the Api but how much more safety it brings us and what increase productivity we can 
expect from developing Akka Typed distributed applications vs untyped applications.

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
will have our back here and this means huge gain on productivity while developing large Akka systems but even if those systems aren't big. It is so easy to forget
what I wrote yesterday in that other file in another package. Whit Akka Typed if you forget the compiler have your back immediately and get you back on track.


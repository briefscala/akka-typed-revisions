## Akka Typed Revision II

In the previous post we took a quick look at traditional Akka actors and exposed their dynamic type nature and how Akka Typed,
the new comer, overcome its most elusive drawback: receive method signature `PartialFuntion[Any, Unit]`. In Akka Type the inbox
is presented as a behavior that is known statically and that cannot chance. For instance, if actor A's inbox has `Behavior[P]`
it can not later change to have `Behavior[B]` where `P` and `B` are disjoint types. Internally, though, actor A can change what
it does as a result of receiving a message of type `P`. This is somewhat disconcerting from type safety and Functional Programming
stand point since giving the same input actor A may or may not produce the same result but pure functions shouldn't be an expectation from
Akka Typed actors. Usually, we choose to develop Akka applications, over other programing paradigms, to solve precisely the problem of 
mutating a state or effect to the World concurrently so impure functions or side effecting behavior is what we need.

```scala
import 
```

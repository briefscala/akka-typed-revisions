## Akka Typed Revision II

In the previous post we took a quick look at traditional Akka actors and exposed their dynamic type nature and how Akka Typed overcome this drawback i.e. a receive method with a wide open signature, `PartialFuntion[Any, Unit]`, are now statically typed behaviors. For instance, if actor A's inbox has behavior of `P` it can not later change to have behavior of `B` where `P` and `B` are disjoint types. Internally though, actor A can change what it does as a result of receiving a message of type `P`. This is somewhat disconcerting from type safety and Functional Programming stand point since giving the same input actor A may or may not produce the same result though, pure functions shouldn't be an expectation from Akka Typed actors. Usually, we choose to develop Akka applications over other programing paradigms to solve precisely the problem of mutating a state or effect to the World concurrently and predictable so impure functions or side effecting behavior that we can reason over is what we were after.

Let's take a look at would we shard an Akka Typed actor with an example.

```scala
import 
```

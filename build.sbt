name := "alarm"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % "2.5.19",
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % "2.5.19"
)
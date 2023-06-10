scalaVersion := "2.13.11"
name := "akka-bank-account"

Compile / scalacOptions ++= Seq(
  "-release:11",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Ymacro-annotations"
)

val AkkaHttpVer = "10.5.0"
val AkkaVer = "2.7.0"
val CirceVer = "0.14.5"
val ScalaTestVer = "3.2.15"
val CatsVer = "2.9.0"
val ScalaMockVer = "5.2.0"
val AkkaPersistenceVer = "1.1.0"
val LogbackVer = "1.4.7"
val AkkaHttpJsonVer = "1.39.2"
val RefinedVer = "0.10.3"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVer,
  "com.typesafe.akka" %% "akka-cluster" % AkkaVer,
  "com.typesafe.akka" %% "akka-cluster-tools" % AkkaVer,
  "com.typesafe.akka" %% "akka-coordination" % AkkaVer,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVer,
  "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVer,
  "com.typesafe.akka" %% "akka-persistence-query" % AkkaVer,
  "com.typesafe.akka" %% "akka-persistence-cassandra" % AkkaPersistenceVer,
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVer,
  "com.typesafe.akka" %% "akka-stream" % AkkaVer,

  "org.typelevel" %% "cats-core" % CatsVer,

  "eu.timepit" %% "refined" % RefinedVer,

  "io.circe" %% "circe-core" % CirceVer,
  "io.circe" %% "circe-generic" % CirceVer,
  "io.circe" %% "circe-parser" % CirceVer,
  "io.circe" %% "circe-refined" % CirceVer,
  "de.heikoseeberger" %% "akka-http-circe" % AkkaHttpJsonVer,

  "ch.qos.logback" % "logback-classic" % LogbackVer,

  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVer % Test,
  "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVer % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVer % Test,
  "org.scalatest" %% "scalatest" % ScalaTestVer % Test,
  "org.scalamock" %% "scalamock" % ScalaMockVer % Test
)


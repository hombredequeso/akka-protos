name := "akka-quickstart-scala"

version := "1.0"

scalaVersion := "2.12.5"


scalacOptions += "-Ypartial-unification"

lazy val akkaVersion = "2.5.11"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.4" % "test",
  "org.typelevel" %% "cats-core" % "1.0.1"
)

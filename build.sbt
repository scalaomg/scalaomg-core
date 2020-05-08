name := "scala-omg"
version := "1.0.0" //"0.1-SNAPSHOT"

scalaVersion := "2.12.10"

// Scalastyle
scalastyleFailOnWarning := true

// Test coverage
coverageEnabled := true
coverageExcludedPackages := ".*examples.*"

// Libraries
val akkaVersion = "2.6.4"
val akkaHttpVersion = "10.1.11"
val akkaStreamVersion = "2.6.4"
libraryDependencies ++= Seq(
  // Akka
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaStreamVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  // Others
  "org.apache.commons" % "commons-lang3" % "3.3.2",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  // Test libraries
  "org.scalatest" %% "scalatest" % "3.1.1" % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
)
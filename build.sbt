name := "scala-omg"
version := "1.0.0" //"0.1-SNAPSHOT"

scalaVersion := "2.12.10"
val akkaVersion = "2.6.4"
val akkaHttpVersion = "10.1.11"

// Scalastyle
scalastyleFailOnWarning := true

// Test coverage
coverageEnabled := true
coverageExcludedPackages := ".*examples.*"

// Libraries
libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.1.1" % "test",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream" % "2.6.4",
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.apache.commons" % "commons-lang3" % "3.3.2",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test)

// Maven
organization := "com.github.scalaomg"
homepage := Some(url("https://github.com/scalaomg"))
description := "A Scala library that simplifies client-server videogames development"
developers := List(
  Developer("gabri-16", "Gabriele Guerrini", "", url = url("https://github.com/gabri-16")),
  Developer("StefanoSalvatori", "Stefano Salvatori", "", url = url("https://github.com/StefanoSalvatori")),
  Developer("RiccardoSalvatori", "Riccardo Salvatori", "", url = url("https://github.com/RiccardoSalvatori")),
)
scmInfo := Some(
  ScmInfo(
    url("https://github.com/scalaomg/scalaomg-core"),
    "scm:git@github.com:scalaomg/scalaomg.core.git"
  )
)
licenses := Seq(("MIT License", url("http://www.opensource.org//licenses//mit-license.php")))

crossPaths := false // It avoids on appending Scala language version to the artifact name
publishArtifact in Test := false
coverageEnabled := false
pomIncludeRepository := { _ => false }
publishMavenStyle := true

publishTo := Some(
  if (isSnapshot.value) Opts.resolver.sonatypeSnapshots else Opts.resolver.sonatypeStaging
)
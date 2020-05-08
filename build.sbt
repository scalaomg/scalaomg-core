name := "scala-omg"
version := "1.0.1-SNAPSHOT"

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
  "org.scalatest" %% "scalatest" % "3.1.1" % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
)

/* Maven */
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

//Exclude test libraries
import scala.xml.{Node => XmlNode, NodeSeq => XmlNodeSeq, _}
import scala.xml.transform.{RewriteRule, RuleTransformer}

pomPostProcess := { node: XmlNode =>
  new RuleTransformer(new RewriteRule {
    override def transform(node: XmlNode): XmlNodeSeq = node match {
      case e: Elem if e.label == "dependency"
        && e.child.exists(child => child.label == "scope") =>
        def txt(label: String): String = "\"" + e.child.filter(_.label == label).flatMap(_.text).mkString + "\""
        Comment(s""" scoped dependency ${txt("groupId")} % ${txt("artifactId")} % ${txt("version")} % ${txt("scope")} has been omitted """)
      case _ => node
    }
  }).transform(node).head
}
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.4"

lazy val root = (project in file("."))
  .settings(
    name := "http-assignment",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.8.6",      // Akka Actor
      "com.typesafe.akka" %% "akka-stream" % "2.8.6",     // Akka Stream
      "com.typesafe.akka" %% "akka-http" % "10.5.3",      // Akka HTTP
      "com.typesafe.akka" %% "akka-http-spray-json" % "10.5.3",
      "io.spray" %% "spray-json" % "1.3.6",
      "mysql" % "mysql-connector-java" % "8.0.33"
    )
  )

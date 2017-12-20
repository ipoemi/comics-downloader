lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.3"
lazy val catsCore = "org.typelevel" %% "cats-core" % "1.0.0-RC2"
lazy val akkaHttp = "com.typesafe.akka" %% "akka-http-core" % "10.0.9"
lazy val betterFilesAkka = "com.github.pathikrit" %% "better-files-akka" % "2.17.1"
lazy val scalaScraper = "net.ruippeixotog" %% "scala-scraper" % "2.0.0"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.12.3",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "comics-downloader",
    libraryDependencies ++= Seq(
      catsCore, akkaHttp, betterFilesAkka, scalaScraper
    ),
    libraryDependencies ++= Seq(
      scalaTest % Test
    ),

    scalacOptions ++= Seq(
      "-deprecation",
      "-Ypartial-unification",
      "-encoding", "UTF-8",
      "-feature",
      "-unchecked",
      "-language:_"
    )
  )

lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.3"
lazy val catsCore = "org.typelevel" %% "cats-core" % "1.0.0-MF"
lazy val akkaHttp = "com.typesafe.akka" %% "akka-http-core" % "10.0.9"
lazy val betterFilesAkka = "com.github.pathikrit" %% "better-files-akka" % "2.17.1"
lazy val scalaScraper = "net.ruippeixotog" %% "scala-scraper" % "2.0.0"

lazy val macroParadise = compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
lazy val kindProjector = compilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4")

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.12.3",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "comics-downloader",
    libraryDependencies ++= Seq(
      catsCore, akkaHttp, betterFilesAkka, scalaScraper,
      macroParadise, kindProjector
    ),
    libraryDependencies ++= Seq(
      scalaTest % Test
    ),

    partialUnificationModule := "com.milessabin" % "si2712fix-plugin" % "1.2.0",

    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-feature",
      "-unchecked",
      "-language:_"
    )
  )

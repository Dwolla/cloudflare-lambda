ThisBuild / organization := "com.dwolla"
ThisBuild / homepage := Some(url("https://github.com/Dwolla/cloudflare-lambda"))
ThisBuild / description := "cloudflare-lambda lambda function"
ThisBuild / licenses += ("MIT", url("https://opensource.org/licenses/MIT"))
ThisBuild / startYear := Option(2019)
ThisBuild / scalaVersion := "2.13.8"
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("11"))
ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Sbt(List("test"), name = Option("Run tests")),
  WorkflowStep.Sbt(List("universal:packageBin"), name = Option("Package artifact")),
)
ThisBuild / developers := List(
  Developer(
    "bpholt",
    "Brian Holt",
    "bholt+cloudflare-lambda@dwolla.com",
    url("https://dwolla.com")
  ),
)
ThisBuild / libraryDependencies ++= Seq(
  compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
  compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
)

javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")

lazy val root = (project in file("."))
  .settings(
    name := "cloudflare-lambda",
    topLevelDirectory := None,
    maintainer := developers.value.head.email,
    libraryDependencies ++= {
      val natchezVersion = "0.1.6"
      val feralVersion = "0.1.0-M7"
      val specs2Version = "4.13.2"
      val awsJavaSdkVersion = "2.17.129"

      Seq(
        "org.typelevel" %% "feral-lambda-cloudformation-custom-resource" % feralVersion,
        "org.tpolecat" %% "natchez-xray" % natchezVersion,
        "org.tpolecat" %% "natchez-http4s" % "0.3.2",
        "com.dwolla" %% "fs2-aws-java-sdk2" % "3.0.0-RC1",
        "co.fs2" %% "fs2-core" % "3.2.4",
        "org.typelevel" %% "cats-core" % "2.7.0",
        "org.typelevel" %% "cats-effect" % "3.3.5",
        "com.chuusai" %% "shapeless" % "2.3.8",
        "io.circe" %% "circe-fs2" % "0.14.0",
        "com.dwolla" %% "cloudflare-api-client" % "4.0.0-M15",
        "org.http4s" %% "http4s-ember-client" % "0.23.10",
        "software.amazon.awssdk" % "kms" % awsJavaSdkVersion,
        "org.typelevel" %% "log4cats-slf4j" % "2.2.0",
        "org.typelevel" %% "log4cats-noop" % "2.2.0" % Test,
        "org.specs2" %% "specs2-core" % specs2Version % Test,
        "org.specs2" %% "specs2-mock" % specs2Version % Test,
        "org.specs2" %% "specs2-matcher-extra" % specs2Version % Test,
        "org.specs2" %% "specs2-cats" % specs2Version % Test,
        "org.typelevel" %% "cats-effect-testing-specs2" % "1.4.0" % Test,
        "org.typelevel" %% "discipline-specs2" % "1.3.1" % Test,
        "org.typelevel" %% "cats-laws" % "2.7.0" % Test,
      )
    },
  )
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)
  .enablePlugins(
    UniversalPlugin,
    JavaAppPackaging,
    ServerlessDeployPlugin,
  )

import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val playVersion = "play-28"

  private val hmrcMongoVersion = "1.3.0"

  private val bootstrapVersion = "7.19.0"

  val compile: Seq[ModuleID] = Seq(
    filters,
    ws,
    "uk.gov.hmrc"       %% s"bootstrap-backend-$playVersion" % bootstrapVersion,
    "uk.gov.hmrc"       %% "domain"                          % s"8.3.0-$playVersion",
    "uk.gov.hmrc"       %% "json-encryption"                 % s"5.1.0-$playVersion",
    "com.typesafe.play" %% "play-json-joda"                  % "2.9.2",
    "org.typelevel"     %% "cats-core"                       % "2.9.0",
    "uk.gov.hmrc"       %% "mongo-feature-toggles-client"    % "0.3.0",
    "org.typelevel"     %% "cats-effect"                     % "3.5.1"
  )

  val compileTest: Seq[ModuleID] = Seq(
    "com.github.tomakehurst" % "wiremock-jre8"                  % "2.35.0",
    "uk.gov.hmrc"            %% s"bootstrap-test-$playVersion"  % bootstrapVersion,
    "org.mockito"            %% "mockito-scala-scalatest"       % "1.17.14",
    "org.scalatestplus"      %% "scalacheck-1-16"               % "3.2.14.0",
    "com.vladsch.flexmark"   % "flexmark-all"                   % "0.62.2",
    "org.jsoup"              % "jsoup"                          % "1.15.4",
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion
  ).map(_  % "test, it")

  val jacksonVersion = "2.13.2"
  val jacksonDatabindVersion = "2.13.2.2"

  val jacksonOverrides = Seq(
    "com.fasterxml.jackson.core" % "jackson-core",
    "com.fasterxml.jackson.core" % "jackson-annotations",
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8",
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310"
  ).map(_ % jacksonVersion)

  val jacksonDatabindOverrides = Seq(
    "com.fasterxml.jackson.core" % "jackson-databind" % jacksonDatabindVersion
  )

  val akkaSerializationJacksonOverrides = Seq(
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor",
    "com.fasterxml.jackson.module" % "jackson-module-parameter-names",
    "com.fasterxml.jackson.module" %% "jackson-module-scala",
  ).map(_ % jacksonVersion)

  val all: Seq[ModuleID] = compile ++ jacksonDatabindOverrides ++ jacksonOverrides ++ akkaSerializationJacksonOverrides ++ compileTest
}

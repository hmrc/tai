import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val playVersion = "play-28"

  private val hmrcMongoVersion = "0.74.0"

  private val bootstrapVersion = "7.15.0"

  val compile = Seq(
    filters,
    ws,
    "uk.gov.hmrc"       %% s"bootstrap-backend-$playVersion" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"              % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "domain"                          % s"8.3.0-$playVersion",
    "uk.gov.hmrc"       %% "json-encryption"                 % s"5.1.0-$playVersion",
    "com.typesafe.play" %% "play-json-joda"                  % "2.9.2",
    "org.typelevel"     %% "cats-core"                       % "2.9.0",
  )

  lazy val Test: String = "test,it"

  val compileTest = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play"      % "5.1.0",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28" % hmrcMongoVersion,
    "org.pegdown"            % "pegdown"                  % "1.6.0",
    "org.jsoup"              % "jsoup"                    % "1.16.1",
    "org.scalacheck"         %% "scalacheck"              % "1.17.0",
    "org.mockito"            %% "mockito-scala-scalatest" % "1.17.14",
    "org.scalatestplus"      %% "scalacheck-1-16"         % "3.2.14.0",
    "com.github.tomakehurst" % "wiremock-jre8"            % "2.35.0",
    "com.vladsch.flexmark"   % "flexmark-all"             % "0.62.2",
    "uk.gov.hmrc"            %% s"bootstrap-test-$playVersion" % bootstrapVersion
  ).map(_ % "test,it")

  def apply(): Seq[ModuleID] = compile ++ compileTest
}

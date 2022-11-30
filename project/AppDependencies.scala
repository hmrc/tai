import sbt._

object AppDependencies {
  import play.sbt.PlayImport._

  private val playVersion = "play-28"

  private val pegdownVersion = "1.6.0"

  private val hmrcMongoVersion = "0.74.0"

  val compile = Seq(
    filters,
    ws,
    "uk.gov.hmrc"       %% s"bootstrap-backend-$playVersion" % "5.7.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"              % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "domain"                          % s"6.1.0-$playVersion",
    "uk.gov.hmrc"       %% "json-encryption"                 % s"4.10.0-$playVersion",
    "com.typesafe.play" %% "play-json-joda"                  % "2.9.2",
    "org.typelevel"     %% "cats-core"                       % "2.0.0",
  )

  lazy val Test: String = "test,it"

  val compileTest = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play"      % "5.1.0" % Test,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28" % hmrcMongoVersion % Test,
    "org.pegdown"            % "pegdown"                  % pegdownVersion % Test,
    "org.jsoup"              % "jsoup"                    % "1.13.1" % Test,
    "org.scalacheck"         %% "scalacheck"              % "1.14.3" % Test,
    "org.mockito"            % "mockito-core"             % "4.3.1",
    "org.scalatestplus"      %% "mockito-3-4"             % "3.2.10.0",
    "com.github.tomakehurst" % "wiremock-jre8"            % "2.27.2" % Test,
    "com.vladsch.flexmark"   % "flexmark-all"             % "0.35.10"
  )

  def apply(): Seq[ModuleID] = compile ++ compileTest
}

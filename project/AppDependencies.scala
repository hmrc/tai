import sbt._

object AppDependencies {
  import play.sbt.PlayImport._
  
  private val playVersion = "play-27"
  
  private val pegdownVersion = "1.6.0"

  val compile = Seq(
    filters,
    ws,
    "uk.gov.hmrc"       %% s"bootstrap-backend-$playVersion" % "5.3.0",
    "uk.gov.hmrc"       %% "domain"                  % s"6.1.0-$playVersion",
    "uk.gov.hmrc"       %% "json-encryption"         % s"4.10.0-$playVersion",
    "uk.gov.hmrc"       %% "mongo-caching"           % s"6.15.0-$playVersion" exclude("uk.gov.hmrc","time_2.11"),
    "uk.gov.hmrc"       %% "auth-client"             % s"3.2.0-$playVersion",
    "com.typesafe.play" %% "play-json-joda"          % "2.7.4"
  )

  lazy val scope: String = "test,it"

  val compileTest = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.0" % scope,
    "org.pegdown"            %  "pegdown"            % pegdownVersion % scope,
    "org.jsoup"              %  "jsoup"              % "1.13.1" % scope,
    "org.scalacheck"         %% "scalacheck"         % "1.14.3" % scope,
    "org.mockito"            %  "mockito-core"       % "3.8.0",
    "com.github.tomakehurst" %  "wiremock-jre8"      % "2.27.2" % scope
  )

  def apply(): Seq[ModuleID] = compile ++ compileTest

}



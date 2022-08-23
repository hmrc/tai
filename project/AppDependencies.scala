import sbt._

object AppDependencies {
  import play.sbt.PlayImport._
  
  private val playVersion = "play-28"
  
  private val pegdownVersion = "1.6.0"

  val compile = Seq(
    filters,
    ws,
    "uk.gov.hmrc"       %% s"bootstrap-backend-$playVersion" % "5.7.0",
    "uk.gov.hmrc"       %% "domain"                  % s"6.1.0-$playVersion",
    "uk.gov.hmrc"       %% "json-encryption"         % s"4.10.0-$playVersion",
    "uk.gov.hmrc"       %% "mongo-caching"           % s"7.0.0-$playVersion" exclude("uk.gov.hmrc","time_2.11"),
    "com.typesafe.play" %% "play-json-joda"          % "2.9.2",
    "org.typelevel"     %% "cats-core"               % "2.0.0",

  )

  lazy val scope: String = "test,it"

  val compileTest = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % scope,
    "org.pegdown"            %  "pegdown"            % pegdownVersion % scope,
    "org.jsoup"              %  "jsoup"              % "1.13.1" % scope,
    "org.scalacheck"         %% "scalacheck"         % "1.14.3" % scope,
    "org.mockito"            %  "mockito-core"       % "3.8.0",
    "org.scalatestplus"      %% "mockito-3-4"        % "3.2.3.0",
    "com.github.tomakehurst" %  "wiremock-jre8"      % "2.27.2" % scope,
    "com.vladsch.flexmark"   % "flexmark-all"        % "0.35.10"
  )

  def apply(): Seq[ModuleID] = compile ++ compileTest
}



import sbt._

object AppDependencies {
  import play.sbt.PlayImport._
  
  private val playVersion = "play-25"
  
  private val pegdownVersion = "1.6.0"
  private val scalatestVersion = "2.2.6"

  val compile = Seq(
    filters,
    ws,
    "uk.gov.hmrc" %% s"bootstrap-$playVersion" % "5.1.0",
    "uk.gov.hmrc" %% "domain"                  % s"5.10.0-$playVersion",
    "uk.gov.hmrc" %% "json-encryption"         % s"4.8.0-$playVersion",
    "uk.gov.hmrc" %% "mongo-caching"           % s"6.15.0-$playVersion" exclude("uk.gov.hmrc","time_2.11"),
    "uk.gov.hmrc" %% "auth-client"             % s"2.26.0-$playVersion"
  )

  lazy val scope: String = "test,it"

  val compileTest = Seq(
    "uk.gov.hmrc"            %% "hmrctest"           % s"3.9.0-$playVersion" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % scope,
    "org.pegdown"            %  "pegdown"            % pegdownVersion % scope,
    "org.jsoup"              %  "jsoup"              % "1.7.3" % scope,
    "org.scalacheck"         %% "scalacheck"         % "1.12.5" % scope,
    "org.mockito"            %  "mockito-core"       % "1.9.5",
    "com.github.tomakehurst" %  "wiremock"           % "2.15.0" % scope
  )

  def apply(): Seq[ModuleID] = compile ++ compileTest

}



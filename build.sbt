import sbt.Tests.{Group, SubProcess}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.{DefaultBuildSettings, SbtAutoBuildPlugin}

val appName: String = "tai"

lazy val playSettings: Seq[Setting[_]] = Seq(routesImport ++= Seq("uk.gov.hmrc.tai.binders._", "uk.gov.hmrc.domain._"))

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)
  .settings(playSettings ++ scoverageSettings: _*)
  .settings(publishingSettings: _*)
  .settings(
    libraryDependencies ++= AppDependencies(),
    retrieveManaged := true,
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    routesGenerator := InjectedRoutesGenerator,
    scalafmtOnCompile := true,
    PlayKeys.playDefaultPort := 9331,
    scalaVersion := "2.12.12"
  )
  .settings(inConfig(TemplateTest)(Defaults.testSettings): _*)
  .configs(IntegrationTest)
  .settings(DefaultBuildSettings.integrationTestSettings())
  .settings(
    resolvers += Resolver.jcenterRepo,
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xlint"),
    routesImport ++= Seq(
      "scala.language.reflectiveCalls",
      "uk.gov.hmrc.tai.model.domain.income._",
      "uk.gov.hmrc.tai.model.domain._")
  )
  .settings(majorVersion := 0)

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  val scoverageExcludePatterns =
    List("<empty>", "Reverse.*", "app.Routes.*", "uk.gov.hmrc.BuildInfo.*", "prod.*", "dev.*", "uk.gov.hmrc.tai.config")

  Seq(
    ScoverageKeys.coverageExcludedPackages := scoverageExcludePatterns.mkString("", ";", ""),
    ScoverageKeys.coverageMinimum := 97,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
}

val allPhases = "tt->test;test->test;test->compile;compile->compile"
val allItPhases = "tit->it;it->it;it->compile;compile->compile"

lazy val TemplateTest = config("tt") extend Test
lazy val TemplateItTest = config("tit") extend IntegrationTest

def oneForkedJvmPerTest(tests: Seq[TestDefinition]) = tests map { test =>
  val forkOptions =
    ForkOptions().withRunJVMOptions(Vector("-Dtest.name=" + test.name))
  Group(test.name, Seq(test), SubProcess(config = forkOptions))
}

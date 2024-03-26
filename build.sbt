import sbt.Tests.{Group, SubProcess}
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings

val appName: String = "tai"

lazy val playSettings: Seq[Setting[_]] = Seq(routesImport ++= Seq("uk.gov.hmrc.tai.binders._", "uk.gov.hmrc.domain._"))

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtDistributablesPlugin)
  .configs(IntegrationTest)
  .settings(playSettings ++ scoverageSettings: _*)
  .settings(
    majorVersion := 1,
    libraryDependencies ++= AppDependencies.all,
    retrieveManaged := true,
    update / evictionWarningOptions := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    routesGenerator := InjectedRoutesGenerator,
    PlayKeys.playDefaultPort := 9331,
    scalaVersion := "2.13.8",
    integrationTestSettings(),
    resolvers += Resolver.jcenterRepo,
    routesImport ++= Seq( "scala.language.reflectiveCalls", "uk.gov.hmrc.tai.model.domain.income._",
      "uk.gov.hmrc.tai.model.domain._"
    ),
    scalacOptions ++= Seq(
      "-Ywarn-unused",
      "-Xlint",
      "-feature",
      "-Werror",
      "-Wconf:cat=deprecation&site=uk\\.gov\\.hmrc\\.tai\\.connectors\\.BaseConnectorSpec.*:s",
      "-Wconf:cat=unused-imports&site=.*templates\\.html.*:s",
      "-Wconf:cat=unused-imports&site=.*templates\\.xml.*:s",
      "-Wconf:cat=deprecation&msg=\\.*value readRaw in object HttpReads is deprecated\\.*:s",
      "-Wconf:cat=unused&msg=\\.*private default argument in class\\.*:s",
      "-Wconf:cat=unused-imports&site=<empty>:s",
      "-Wconf:cat=unused&src=.*RoutesPrefix\\.scala:s",
      "-Wconf:cat=unused&src=.*Routes\\.scala:s",
      "-Wconf:cat=unused&src=.*ReverseRoutes\\.scala:s"
    )
  )
  .settings(inConfig(TemplateTest)(Defaults.testSettings): _*)

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  val scoverageExcludePatterns =
    List("<empty>", "Reverse.*", "app.Routes.*", "uk.gov.hmrc.BuildInfo.*", "prod.*", "dev.*", "uk.gov.hmrc.tai.config")

  Seq(
    ScoverageKeys.coverageExcludedPackages := scoverageExcludePatterns.mkString("", ";", ""),
    ScoverageKeys.coverageMinimumStmtTotal := 92,
    ScoverageKeys.coverageMinimumBranchTotal := 90,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    Test / parallelExecution  := false
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

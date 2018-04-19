import play.sbt.routes.RoutesKeys.{routesGenerator, _}
import sbt.Keys._
import sbt.Tests.{Group, SubProcess}
import sbt._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

trait MicroService {

  import uk.gov.hmrc._
  import DefaultBuildSettings._
  import TestPhases._
  val appName: String
  import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
  import uk.gov.hmrc.SbtAutoBuildPlugin

  lazy val appDependencies : Seq[ModuleID] = ???
  lazy val playSettings : Seq[Setting[_]] = Seq.empty

  lazy val scoverageSettings = {
    import scoverage.ScoverageKeys
    val scoverageExcludePatterns = List(
      "<empty>",
      "Reverse.*",
      "app.Routes.*",
      "uk.gov.hmrc.BuildInfo.*",
      "prod.*",
      "dev.*",
      "uk.gov.hmrc.tai.config")

    Seq(
      ScoverageKeys.coverageExcludedPackages := scoverageExcludePatterns.mkString("", ";", ""),
      ScoverageKeys.coverageMinimum := 97,
      ScoverageKeys.coverageFailOnMinimum := true,
      ScoverageKeys.coverageHighlighting := true,
      parallelExecution in Test := false
    )
  }

  lazy val microservice = Project(appName, file("."))
    .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)
    .settings(playSettings ++ scoverageSettings : _*)
    .settings(publishingSettings: _*)
    .settings(
      libraryDependencies ++= appDependencies,
      retrieveManaged := true,
      evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
      routesGenerator := InjectedRoutesGenerator
    )
    .settings(inConfig(TemplateTest)(Defaults.testSettings): _*)
    .configs(IntegrationTest)
    .settings(inConfig(TemplateItTest)(Defaults.itSettings): _*)
    .settings(
      Keys.fork in IntegrationTest := false,
      unmanagedSourceDirectories in IntegrationTest <<= (baseDirectory in IntegrationTest)(base => Seq(base / "it")),
      testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
      parallelExecution in IntegrationTest := false)
    .settings(
      resolvers += Resolver.jcenterRepo,
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xlint"),
      routesImport += "scala.language.reflectiveCalls"
    )

}

private object TestPhases {

  val allPhases = "tt->test;test->test;test->compile;compile->compile"
  val allItPhases = "tit->it;it->it;it->compile;compile->compile"

  lazy val TemplateTest = config("tt") extend Test
  lazy val TemplateItTest = config("tit") extend IntegrationTest

  def oneForkedJvmPerTest(tests: Seq[TestDefinition]) =
    tests map {
      test => new Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions = Seq("-Dtest.name=" + test.name))))
    }
}
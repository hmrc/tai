import sbt.Tests.{Group, SubProcess}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.SbtArtifactory

val appName: String = "tai"

lazy val playSettings: Seq[Setting[_]] = Seq(routesImport ++= Seq("uk.gov.hmrc.tai.binders._", "uk.gov.hmrc.domain._"))

val akkaResolver = "com.typesafe.akka"
val akkaVersion = "2.5.23"
val akkaHttpVersion = "10.0.15"
lazy val dependencyOverride: Set[ModuleID] = Set(
  akkaResolver %% "akka-stream"    % akkaVersion force (),
  akkaResolver %% "akka-protobuf"  % akkaVersion force (),
  akkaResolver %% "akka-slf4j"     % akkaVersion force (),
  akkaResolver %% "akka-actor"     % akkaVersion force (),
  akkaResolver %% "akka-http-core" % akkaHttpVersion force ()
)

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
  .settings(playSettings ++ scoverageSettings: _*)
  .settings(publishingSettings: _*)
  .settings(
    libraryDependencies ++= AppDependencies(),
    dependencyOverrides ++= dependencyOverride,
    retrieveManaged := true,
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    routesGenerator := InjectedRoutesGenerator,
    scalafmtOnCompile := true,
    PlayKeys.playDefaultPort := 9331
  )
  .settings(inConfig(TemplateTest)(Defaults.testSettings): _*)
  .configs(IntegrationTest)
  .settings(inConfig(TemplateItTest)(Defaults.itSettings): _*)
  .settings(
    Keys.fork in IntegrationTest := false,
    unmanagedSourceDirectories in IntegrationTest := (baseDirectory in Test)(base => Seq(base / "it")).value,
    parallelExecution in IntegrationTest := false,
    HeaderPlugin.autoImport.headerSettings(IntegrationTest),
    AutomateHeaderPlugin.autoImport.automateHeaderSettings(IntegrationTest)
  )
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

def oneForkedJvmPerTest(tests: Seq[TestDefinition]) =
  tests map { test =>
    new Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions = Seq("-Dtest.name=" + test.name))))
  }

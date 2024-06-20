/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import uk.gov.hmrc.DefaultBuildSettings
import uk.gov.hmrc.DefaultBuildSettings.*

val appName: String = "tai"

ThisBuild / majorVersion := 2
ThisBuild / scalaVersion := "2.13.12"
ThisBuild / scalafmtOnCompile := true

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) // Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    scalaSettings,
    PlayKeys.playDefaultPort := 9331,
    scoverageSettings,
    libraryDependencies ++= AppDependencies.all,
    routesImport ++= Seq(
      "scala.language.reflectiveCalls",
      "uk.gov.hmrc.tai.model.domain.income._",
      "uk.gov.hmrc.tai.model.domain._",
      "uk.gov.hmrc.tai.binders._",
      "uk.gov.hmrc.domain._"
    ),
    scalacOptions ++= Seq(
      "-unchecked",
      "-feature",
      "-Xlint:_",
      "-Werror",
      "-Wdead-code",
      "-Wunused:_",
      "-Wextra-implicit",
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

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  val scoverageExcludePatterns =
    List("<empty>", "Reverse.*", ".*Routes.*", "uk.gov.hmrc.BuildInfo.*", "prod.*", "dev.*", "uk.gov.hmrc.tai.config")

  Seq(
    ScoverageKeys.coverageExcludedPackages := scoverageExcludePatterns.mkString("", ";", ""),
    ScoverageKeys.coverageMinimumStmtTotal := 92,
    ScoverageKeys.coverageMinimumBranchTotal := 85,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}

Test / parallelExecution := false
Test / scalacOptions --= Seq("-Wdead-code", "-Wvalue-discard")

lazy val it = project
  .enablePlugins(play.sbt.PlayScala)
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(
    libraryDependencies ++= AppDependencies.test,
    DefaultBuildSettings.itSettings()
  )

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

val appName: String = "tai"

ThisBuild / majorVersion := 3
ThisBuild / scalaVersion := "3.3.4"
ThisBuild / scalafmtOnCompile := true
ThisBuild / scalacOptions ++= Seq(
  "-unchecked",
  "-feature",
  "-Wvalue-discard",
  "-Werror",
  "-Wconf:msg=unused import&src=html/.*:s",
  "-Wconf:msg=unused import&src=xml/.*:s",
  "-Wconf:msg=unused&src=.*RoutesPrefix\\.scala:s",
  "-Wconf:msg=unused&src=.*Routes\\.scala:s",
  "-Wconf:msg=unused&src=.*ReverseRoutes\\.scala:s",
  "-Wconf:msg=Flag.*repeatedly:s"
)

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) // Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    PlayKeys.playDefaultPort := 9331,
    scoverageSettings,
    libraryDependencies ++= AppDependencies.all,
    routesImport ++= Seq(
      "scala.language.reflectiveCalls",
      "uk.gov.hmrc.tai.model.domain.income._",
      "uk.gov.hmrc.tai.model.domain._",
      "uk.gov.hmrc.tai.binders._",
      "uk.gov.hmrc.domain._"
    )
  )

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  val scoverageExcludePatterns =
    List(
      "<empty>",
      "Reverse.*",
      ".*Routes.*",
      "uk.gov.hmrc.BuildInfo.*",
      "prod.*",
      "dev.*",
      "uk.gov.hmrc.tai.config",
      "testOnly.controllers",
      "uk.gov.hmrc.tai.model.nps2.AllowanceType",
      "uk.gov.hmrc.tai.repositories.deprecated.SessionRepository",
      "uk.gov.hmrc.tai.model.nps2.TaxDetail"
    )

  Seq(
    ScoverageKeys.coverageExcludedPackages := scoverageExcludePatterns.mkString("", ";", ""),
    ScoverageKeys.coverageMinimumStmtTotal := 80,
    ScoverageKeys.coverageMinimumBranchTotal := 60,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}

Test / parallelExecution := false
Test / scalacOptions ++= Seq(
  "-Wconf:msg=discarded non-Unit value:s"
)

lazy val it = project
  .enablePlugins(play.sbt.PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(
    libraryDependencies ++= AppDependencies.test,
    DefaultBuildSettings.itSettings(),
    Test / parallelExecution := false
  )

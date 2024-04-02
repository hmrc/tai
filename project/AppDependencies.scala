import sbt.*
import play.sbt.PlayImport.*

object AppDependencies {

  private val playVersion = "play-30"
  private val hmrcMongoVersion = "1.8.0"
  private val bootstrapVersion = "8.5.0"

  val compile: Seq[ModuleID] = Seq(
    filters,
    ws,
    "uk.gov.hmrc"       %% s"bootstrap-backend-$playVersion"            % bootstrapVersion,
    "uk.gov.hmrc"       %% s"domain-$playVersion"                       % "9.0.0",
    "uk.gov.hmrc"       %% s"crypto-json-$playVersion"                  % "7.6.0",
    "org.typelevel"     %% "cats-core"                                  % "2.10.0",
    "uk.gov.hmrc"       %% s"mongo-feature-toggles-client-$playVersion" % "1.3.0",
    "org.typelevel"     %% "cats-effect"                                % "3.5.4"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% s"bootstrap-test-$playVersion"  % bootstrapVersion,
    "org.mockito"            %% "mockito-scala-scalatest"       % "1.17.30",
    "org.scalatestplus"      %% "scalacheck-1-17"               % "3.2.18.0",
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion
  ).map(_  % "test")

  val all: Seq[ModuleID] = compile ++ test
}

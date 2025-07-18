import play.sbt.PlayImport.*
import sbt.*

object AppDependencies {

  private val playVersion = "play-30"
  private val bootstrapVersion = "9.16.0"

  val compile: Seq[ModuleID] = Seq(
    filters,
    ws,
    "uk.gov.hmrc"   %% s"bootstrap-backend-$playVersion"            % bootstrapVersion,
    "uk.gov.hmrc"   %% s"domain-$playVersion"                       % "10.0.0",
    "uk.gov.hmrc"   %% s"crypto-json-$playVersion"                  % "8.2.0",
    "org.typelevel" %% "cats-core"                                  % "2.13.0",
    "uk.gov.hmrc"   %% s"mongo-feature-toggles-client-$playVersion" % "1.10.0",
    "org.typelevel" %% "cats-effect"                                % "3.5.7"
  )

  val test: Seq[ModuleID] = Seq(
    "org.scalatestplus" %% "scalacheck-1-18"                                 % "3.2.19.0",
    "uk.gov.hmrc"       %% s"mongo-feature-toggles-client-test-$playVersion" % "1.10.0"
  ).map(_ % "test")

  val all: Seq[ModuleID] = compile ++ test
}

import play.sbt.PlayImport.*
import sbt.*

object AppDependencies {

  private val playVersion = "play-30"
  private val bootstrapVersion =
    "8.5.0" // The version of bootstrap should match the one provided in mongo-feature-toggles-client

  val compile: Seq[ModuleID] = Seq(
    filters,
    ws,
    "uk.gov.hmrc"             %% s"bootstrap-backend-$playVersion"            % bootstrapVersion,
    "uk.gov.hmrc"             %% s"domain-$playVersion"                       % "9.0.0",
    "uk.gov.hmrc"             %% s"crypto-json-$playVersion"                  % "7.6.0",
    "org.typelevel"           %% "cats-core"                                  % "2.10.0",
    "uk.gov.hmrc"             %% s"mongo-feature-toggles-client-$playVersion" % "1.4.0",
    "org.typelevel"           %% "cats-effect"                                % "3.5.4",
    "org.apache.xmlgraphics"   % "fop"                                        % "2.10",
  )

  val test: Seq[ModuleID] = Seq(
    "org.mockito"       %% "mockito-scala-scalatest"                         % "1.17.31",
    "org.scalatestplus" %% "scalacheck-1-17"                                 % "3.2.18.0",
    "uk.gov.hmrc"       %% s"mongo-feature-toggles-client-test-$playVersion" % "1.4.0",
    "org.apache.pdfbox"  % "pdfbox"                                          % "3.0.3",
  ).map(_ % "test")

  val all: Seq[ModuleID] = compile ++ test
}

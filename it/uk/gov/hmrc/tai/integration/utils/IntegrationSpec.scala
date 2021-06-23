/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.tai.integration.utils

import com.github.tomakehurst.wiremock.client.WireMock.{ok, post, urlEqualTo}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.util.Random

// APIs to test:
// GET /tai/nino/tax-account/2021/summary

class IntegrationSpec extends UnitSpec with GuiceOneAppPerSuite with WireMockHelper with ScalaFutures with IntegrationPatience {
  override def beforeEach() = {
    super.beforeEach()

    val authResponse =
      s"""
         |{
         |    "confidenceLevel": 200,
         |    "nino": "$nino",
         |    "name": {
         |        "name": "John",
         |        "lastName": "Smith"
         |    },
         |    "loginTimes": {
         |        "currentLogin": "2021-06-07T10:52:02.594Z",
         |        "previousLogin": null
         |    },
         |    "optionalCredentials": {
         |        "providerId": "4911434741952698",
         |        "providerType": "GovernmentGateway"
         |    },
         |    "authProviderId": {
         |        "ggCredId": "xyz"
         |    },
         |    "externalId": "testExternalId"
         |}
         |""".stripMargin

    server.stubFor(
      post(urlEqualTo("/auth/authorise"))
        .willReturn(ok(authResponse)))
  }

  override def fakeApplication =
    GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port"    -> server.port(),
        "microservice.services.des-hod.port" -> server.port(),
        "microservice.services.des-hod.host" -> "127.0.0.1",
        "microservice.services.nps-hod.port" -> server.port(),
        "microservice.services.nps-hod.host" -> "127.0.0.1",
        "auditing.enabled"                   -> false
      )
      .build()

  val nino = new Generator(new Random).nextNino
  val year = TaxYear().year

  val npsTaxAccountUrl = s"/nps-hod-service/services/nps/person/$nino/tax-account/$year"
  val npsIabdsUrl = s"/nps-hod-service/services/nps/person/$nino/iabds/$year"
  val taxCodeHistoryUrl = s"/individuals/tax-code-history/list/$nino/$year?endTaxYear=${year + 1}"
  val employmentUrl = s"/nps-hod-service/services/nps/person/$nino/employment/$year"
  val rtiUrl = s"/rti/individual/payments/nino/${nino.withoutSuffix}/tax-year/${TaxYear().twoDigitRange}"

  val taxAccountJson = FileHelper.loadFile("it/uk/gov/hmrc/tai/integration/resources/taxAccount.json")
  val iabdsJson = FileHelper.loadFile("it/uk/gov/hmrc/tai/integration/resources/iabds.json")
  val taxCodeHistoryJson = FileHelper.loadFile("it/uk/gov/hmrc/tai/integration/resources/taxCodeHistory.json")
  val employmentJson = FileHelper.loadFile("it/uk/gov/hmrc/tai/integration/resources/employment.json")
  val rtiJson = FileHelper.loadFile("it/uk/gov/hmrc/tai/integration/resources/rti.json")
}

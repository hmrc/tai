/*
 * Copyright 2023 HM Revenue & Customs
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
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.cache.AsyncCacheApi
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.test.Injecting
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.model.tai.TaxYear

import java.util.UUID
import scala.util.Random

trait IntegrationSpec
    extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with WireMockHelper with ScalaFutures with Injecting
    with IntegrationPatience {
  override def beforeEach(): Unit = {
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
        .willReturn(ok(authResponse))
    )
    server.stubFor(
      post(urlEqualTo("/pertax/authorise"))
        .willReturn(ok("{\"code\": \"ACCESS_GRANTED\", \"message\": \"Access granted\"}"))
    )
  }

  lazy val fakeAsyncCacheApi = new FakeAsyncCacheApi()

  protected def guiceAppBuilder: GuiceApplicationBuilder = GuiceApplicationBuilder()
    .configure(
      "microservice.services.auth.port"            -> server.port(),
      "microservice.services.pertax.port"          -> server.port(),
      "microservice.services.des-hod.port"         -> server.port(),
      "microservice.services.des-hod.host"         -> "127.0.0.1",
      "microservice.services.nps-hod.port"         -> server.port(),
      "microservice.services.citizen-details.port" -> server.port(),
      "microservice.services.nps-hod.host"         -> "127.0.0.1",
      "microservice.services.if-hod.host"          -> "127.0.0.1",
      "microservice.services.if-hod.port"          -> server.port(),
      "microservice.services.hip-hod.port"         -> server.port(),
      "microservice.services.hip-hod.host"         -> "127.0.0.1",
      "auditing.enabled"                           -> false,
      "cache.isEnabled"                            -> false
    )
    .overrides(
      bind[AsyncCacheApi].toInstance(fakeAsyncCacheApi)
    )

  override def fakeApplication(): Application = guiceAppBuilder.build()

  val nino: Nino = new Generator(new Random).nextNino
  val year: Int = TaxYear().year
  val etag: String = "123"
  val bearerToken = "Bearer 11"

  val cidEtagUrl = s"/citizen-details/$nino/etag"
  val npsTaxAccountUrl = s"/nps-hod-service/services/nps/person/$nino/tax-account/$year"
  val hipTaxAccountUrl = s"/v1/api/person/$nino/tax-account/$year"
  val npsIabdsUrl = s"/nps-hod-service/services/nps/person/$nino/iabds/$year"
  val hipIabdsUrl = s"/v1/api/iabd/taxpayer/$nino/tax-year/$year"
  val desTaxCodeHistoryUrl = s"/individuals/tax-code-history/list/$nino/$year?endTaxYear=$year"
  val npsEmploymentUrl = s"/nps-hod-service/services/nps/person/$nino/employment/$year"
  val hipEmploymentUrl = s"/v1/api/employment/employee/$nino/tax-year/$year/employment-details"
  val rtiUrl = s"/rti/individual/payments/nino/${nino.withoutSuffix}/tax-year/${TaxYear().twoDigitRange}"

  val taxAccountJson: String = FileHelper.loadFile("taxAccount.json")
  val taxAccountHipJson: String = FileHelper.loadFile("taxAccountHip.json")
  val npsIabdsJson: String = FileHelper.loadFile("iabdsNps.json")
  val hipIabdsJson: String = FileHelper.loadFile("iabdsHip.json")
  val taxCodeHistoryJson: String = FileHelper.loadFile("taxCodeHistory.json")
  val employmentJson: String = FileHelper.loadFile("employment.json")
  val employmentHipJson: String = FileHelper.loadFile("employment-hip.json")
  val rtiJson: String = FileHelper.loadFile("rti.json")
  val etagJson: JsValue = Json.parse(s"""
                                        |{
                                        |   "etag":"$etag"
                                        |}
                                          """.stripMargin)

  def generateSessionId: String = UUID.randomUUID().toString
}

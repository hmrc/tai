/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.tai.connectors

import java.net.URL

import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.config.{DesConfig, FeatureTogglesConfig, NpsConfig}
import uk.gov.hmrc.tai.model.domain.response.{HodUpdateFailure, HodUpdateSuccess}
import uk.gov.hmrc.tai.model.IabdUpdateAmountFormats
import uk.gov.hmrc.tai.model.nps2.IabdType.NewEstimatedPay
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.WireMockHelper

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class TaxAccountConnectorSpec extends PlaySpec with WireMockHelper with MockitoSugar {

  "Tax Account Connector" when {

    "toggled to use NPS" must {

      "return Tax Account as Json in the response" in {
        val featureTogglesConfig = mock[FeatureTogglesConfig]
        val url = {
          val path = new URL(taxAccountUrlConfig.taxAccountUrlNps(nino, taxYear))
          s"${path.getPath}"
        }

        when(featureTogglesConfig.desEnabled).thenReturn(false)
        server.stubFor(get(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

        val connector = createSUT(featureTogglesConfig = featureTogglesConfig)
        val result = Await.result(connector.taxAccount(nino, taxYear), 5 seconds)

        result mustBe jsonResponse
      }

      "updateTaxCodeIncome" must {

        "update nps with the new tax code income" in {
          val featureTogglesConfig = mock[FeatureTogglesConfig]
          val url = {
            val path = new URL(iabdUrlConfig.npsIabdEmploymentUrl(nino, taxYear, NewEstimatedPay.code))
            s"${path.getPath}"
          }

          when(featureTogglesConfig.desUpdateEnabled).thenReturn(false)
          server.stubFor(post(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

          val connector = createSUT(featureTogglesConfig = featureTogglesConfig)
          val result = Await.result(connector.updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345), 5 seconds)

          result mustBe HodUpdateSuccess
        }

        "return a failure status if the update fails" in {
          val featureTogglesConfig = mock[FeatureTogglesConfig]
          val url = {
            val path = new URL(iabdUrlConfig.npsIabdEmploymentUrl(nino, taxYear, NewEstimatedPay.code))
            s"${path.getPath}"
          }

          when(featureTogglesConfig.desUpdateEnabled).thenReturn(false)
          server.stubFor(post(urlEqualTo(url)).willReturn(aResponse.withStatus(400)))

          val connector = createSUT(featureTogglesConfig = featureTogglesConfig)
          val result = Await.result(connector.updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345), 5 seconds)

          result mustBe HodUpdateFailure
        }
      }

    }


    "toggled to use DES" must {

      "return Tax Account as Json in the response" in {
        val featureTogglesConfig = mock[FeatureTogglesConfig]
        val url = {
          val path = new URL(taxAccountUrlConfig.taxAccountUrlDes(nino, taxYear))
          path.getPath
        }

        when(featureTogglesConfig.desEnabled).thenReturn(true)
        server.stubFor(get(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

        val connector = createSUT(featureTogglesConfig = featureTogglesConfig)
        val result = Await.result(connector.taxAccount(nino, taxYear), 5 seconds)

        result mustBe jsonResponse
      }

      "updateTaxCodeIncome" must {

        "update des with the new tax code income" in {
          val featureTogglesConfig = mock[FeatureTogglesConfig]
          val url = {
            val path = new URL(iabdUrlConfig.desIabdEmploymentUrl(nino, taxYear, NewEstimatedPay.code))
            path.getPath
          }

          when(featureTogglesConfig.desUpdateEnabled).thenReturn(true)
          server.stubFor(post(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

          val connector = createSUT(featureTogglesConfig = featureTogglesConfig)
          val result = Await.result(connector.updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345), 5 seconds)

          result mustBe HodUpdateSuccess
        }

        "return a failure status if the update fails" in {
          val featureTogglesConfig = mock[FeatureTogglesConfig]
          val url = {
            val path = new URL(iabdUrlConfig.desIabdEmploymentUrl(nino, taxYear, NewEstimatedPay.code))
            path.getPath
          }

          when(featureTogglesConfig.desUpdateEnabled).thenReturn(true)
          server.stubFor(post(urlEqualTo(url)).willReturn(aResponse.withStatus(400)))

          val connector = createSUT(featureTogglesConfig = featureTogglesConfig)
          val result = Await.result(connector.updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345), 5 seconds)

          result mustBe HodUpdateFailure
        }
      }

    }

  }

  private val originatorId = "testOriginatorId"
  private implicit val hc: HeaderCarrier = HeaderCarrier()
  lazy val taxAccountUrlConfig = injector.instanceOf[TaxAccountUrls]
  lazy val iabdUrlConfig = injector.instanceOf[IabdUrls]
  val taxYear = TaxYear(2017)
  val nino: Nino = new Generator(new Random).nextNino

  private val jsonResponse = Json.obj(
    "taxYear" -> 2017,
    "totalLiability" -> Json.obj(
      "untaxedInterest" -> Json.obj(
        "totalTaxableIncome" -> 123)),
    "incomeSources" -> Json.arr(
      Json.obj(
        "employmentId" -> 1,
        "taxCode" -> "1150L",
        "name" -> "Employer1",
        "basisOperation" -> 1),
      Json.obj(
        "employmentId" -> 2,
        "taxCode" -> "1100L",
        "name" -> "Employer2",
        "basisOperation" -> 2)))

  private def createSUT(npsConfig: NpsConfig = injector.instanceOf[NpsConfig],
                        desConfig: DesConfig = injector.instanceOf[DesConfig],
                        taxAccountUrls: TaxAccountUrls = injector.instanceOf[TaxAccountUrls],
                        iabdUrls: IabdUrls = injector.instanceOf[IabdUrls],
                        formats: IabdUpdateAmountFormats = injector.instanceOf[IabdUpdateAmountFormats],
                        httpHandler: HttpHandler = injector.instanceOf[HttpHandler],
                        featureTogglesConfig: FeatureTogglesConfig = injector.instanceOf[FeatureTogglesConfig]) =

    new TaxAccountConnector(npsConfig, desConfig, taxAccountUrls, iabdUrls, formats, httpHandler, featureTogglesConfig)
}
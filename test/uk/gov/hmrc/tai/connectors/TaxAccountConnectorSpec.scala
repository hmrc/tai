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

package uk.gov.hmrc.tai.connectors

import java.net.URL

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.Configuration
import play.api.http.Status._
import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, InternalServerException}
import uk.gov.hmrc.tai.config.{DesConfig, FeatureTogglesConfig, NpsConfig}
import uk.gov.hmrc.tai.factory.TaxAccountHistoryFactory
import uk.gov.hmrc.tai.model.IabdUpdateAmountFormats
import uk.gov.hmrc.tai.model.domain.response.{HodUpdateFailure, HodUpdateResponse, HodUpdateSuccess}
import uk.gov.hmrc.tai.model.nps2.IabdType.NewEstimatedPay
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.WireMockHelper

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class TaxAccountConnectorSpec extends ConnectorBaseSpec with WireMockHelper {

  trait ConnectorSetup {

    def desIsEnabled: Boolean = true

    def desUpdateIsEnabled: Boolean = true

    def apiEnabled: Boolean = true
    def featureTogglesConfig: FeatureTogglesConfig = new FeatureTogglesConfig(inject[Configuration]) {
      override def desEnabled: Boolean = desIsEnabled

      override def desUpdateEnabled: Boolean = desUpdateIsEnabled

      override def confirmedAPIEnabled: Boolean = apiEnabled
    }

    lazy val npsConfig: NpsConfig = inject[NpsConfig]
    lazy val desConfig: DesConfig = inject[DesConfig]
    lazy val iabdUrls: IabdUrls = inject[IabdUrls]

    def taxAccountUrls: TaxAccountUrls =
      new TaxAccountUrls(npsConfig, desConfig, featureTogglesConfig)

    def sut: TaxAccountConnector = new TaxAccountConnector(
      npsConfig,
      desConfig,
      taxAccountUrls,
      iabdUrls,
      inject[IabdUpdateAmountFormats],
      inject[HttpHandler],
      featureTogglesConfig
    )

    val taxYear: TaxYear = TaxYear()

    val jsonResponse: JsObject = Json.obj(
      "taxYear"        -> taxYear.year,
      "totalLiability" -> Json.obj("untaxedInterest" -> Json.obj("totalTaxableIncome" -> 123)),
      "incomeSources" -> Json.arr(
        Json.obj("employmentId" -> 1, "taxCode" -> "1150L", "name" -> "Employer1", "basisOperation" -> 1),
        Json.obj("employmentId" -> 2, "taxCode" -> "1100L", "name" -> "Employer2", "basisOperation" -> 2)
      )
    )
  }

  "Tax Account Connector" when {

    "hcWithHodHeaders is called" must {

      val originId = "Gov-Uk-Originator-Id"

      "set the originator ID to DES" when {

        "DES is enabled" in new ConnectorSetup {

          val headerCarrier: HeaderCarrier = HeaderCarrier()
          val res: HeaderCarrier = sut.hcWithHodHeaders(headerCarrier)

          res.extraHeaders must contain(originId    -> desConfig.originatorId)
          res.extraHeaders mustNot contain(originId -> npsConfig.originatorId)
        }
      }

      "set the originator ID to NPS" when {

        "DES is disabled" in new ConnectorSetup {

          override def desIsEnabled: Boolean = false

          val headerCarrier: HeaderCarrier = HeaderCarrier()
          val res: HeaderCarrier = sut.hcWithHodHeaders(headerCarrier)

          res.extraHeaders must contain(originId    -> npsConfig.originatorId)
          res.extraHeaders mustNot contain(originId -> desConfig.originatorId)
        }
      }
    }

    "toggled to use NPS" when {

      "toggled to use confirmedAPI" must {

        "return Tax Account as Json in the response" in new ConnectorSetup {

          override def apiEnabled: Boolean = false

          val url: String = {
            val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
            s"${path.getPath}"
          }

          server.stubFor(get(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

          val result: JsValue = Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

          result mustBe jsonResponse
        }

        "toggled to use non confirmedAPI" must {

          "return Tax Account as Json in the response" in new ConnectorSetup {

            val url: String = {
              val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
              s"${path.getPath}"
            }

            server.stubFor(get(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

            val result: JsValue = Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

            result mustBe jsonResponse
          }
        }

        "updateTaxCodeIncome" must {

          "update nps with the new tax code income" in new ConnectorSetup {

            override def desUpdateIsEnabled: Boolean = false

            val url: String = {
              val path = new URL(iabdUrls.npsIabdEmploymentUrl(nino, taxYear, NewEstimatedPay.code))
              s"${path.getPath}"
            }

            server.stubFor(post(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

            Await.result(
              sut.updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345),
              5 seconds
            ) mustBe HodUpdateSuccess
          }

          "return a failure status if the update fails" in new ConnectorSetup {

            override def desUpdateIsEnabled: Boolean = false

            val url: String = {
              val path = new URL(iabdUrls.npsIabdEmploymentUrl(nino, taxYear, NewEstimatedPay.code))
              s"${path.getPath}"
            }

            server.stubFor(post(urlEqualTo(url)).willReturn(aResponse.withStatus(400)))

            Await.result(
              sut.updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345),
              5 seconds
            ) mustBe HodUpdateFailure
          }
        }
      }

      "Tax Account History" must {
        "return a Success[JsValue] for valid json" in new ConnectorSetup {
          val taxCodeId = 1

          val json: JsObject = TaxAccountHistoryFactory.combinedIncomeSourcesTotalLiabilityJson(nino)

          val url: String = new URL(taxAccountUrls.taxAccountHistoricSnapshotUrl(nino, taxCodeId)).getPath

          server.stubFor(
            get(urlEqualTo(url)).willReturn(ok(json.toString))
          )

          val result: JsValue = Await.result(sut.taxAccountHistory(nino, taxCodeId), 5.seconds)

          result mustEqual json
        }

        "return a HttpException" when {

          "connector receives 4xx" in new ConnectorSetup {
            val taxCodeId = 1

            val url: String = new URL(taxAccountUrls.taxAccountHistoricSnapshotUrl(nino, taxCodeId)).getPath

            server.stubFor(
              get(urlEqualTo(url)).willReturn(
                badRequest()
              )
            )

            assertConnectorException[BadRequestException](
              sut.taxAccountHistory(nino, taxCodeId),
              BAD_REQUEST,
              ""
            )
          }

          "connector receives 5xx" in new ConnectorSetup {
            val taxCodeId = 1

            val url: String = new URL(taxAccountUrls.taxAccountHistoricSnapshotUrl(nino, taxCodeId)).getPath

            server.stubFor(
              get(urlEqualTo(url)).willReturn(
                serverError()
              )
            )

            assertConnectorException[InternalServerException](
              sut.taxAccountHistory(nino, taxCodeId),
              INTERNAL_SERVER_ERROR,
              ""
            )
          }
        }

        "toggled to use DES" when {

          "toggled to use confirmedAPI" must {

            "return Tax Account as Json in the response" in new ConnectorSetup {

              val url: String = {
                val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
                s"${path.getPath}"
              }

              server.stubFor(get(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

              val result: JsValue = Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result mustBe jsonResponse
            }

          }

          "toggled to use non confirmedAPI" must {

            "return Tax Account as Json in the response" in new ConnectorSetup {

              override def apiEnabled: Boolean = false

              val url: String = {
                val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
                s"${path.getPath}"
              }

              server.stubFor(get(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

              Await.result(sut.taxAccount(nino, taxYear), 5 seconds) mustBe jsonResponse
            }

          }
        }

        "updateTaxCodeIncome" must {

          "update des with the new tax code income" in new ConnectorSetup {
            val url: String = {
              val path = new URL(iabdUrls.desIabdEmploymentUrl(nino, taxYear, NewEstimatedPay.code))
              path.getPath
            }

            server.stubFor(post(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

            val result: HodUpdateResponse =
              Await.result(sut.updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345), 5 seconds)

            result mustBe HodUpdateSuccess
          }

          "return a failure status if the update fails" in new ConnectorSetup {
            val url: String = {
              val path = new URL(iabdUrls.desIabdEmploymentUrl(nino, taxYear, NewEstimatedPay.code))
              path.getPath
            }

            server.stubFor(post(urlEqualTo(url)).willReturn(aResponse.withStatus(400)))

            val result: HodUpdateResponse =
              Await.result(sut.updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345), 5 seconds)

            result mustBe HodUpdateFailure
          }
        }
      }
    }
  }
}

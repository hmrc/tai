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

package uk.gov.hmrc.tai.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import play.api.Configuration
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http.{BadRequestException, HeaderNames, HttpException, InternalServerException, LockedException, NotFoundException}
import uk.gov.hmrc.tai.config.{DesConfig, FeatureTogglesConfig, NpsConfig}
import uk.gov.hmrc.tai.factory.TaxAccountHistoryFactory
import uk.gov.hmrc.tai.model.IabdUpdateAmountFormats
import uk.gov.hmrc.tai.model.domain.response.{HodUpdateFailure, HodUpdateSuccess}
import uk.gov.hmrc.tai.model.nps2.IabdType.NewEstimatedPay
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.{TaiConstants, WireMockHelper}

import java.net.URL
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

    lazy val npsConfig = inject[NpsConfig]
    lazy val desConfig = inject[DesConfig]
    lazy val iabdUrls = inject[IabdUrls]

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

    val taxYear = TaxYear()

    val jsonResponse = Json.obj(
      "taxYear"        -> taxYear.year,
      "totalLiability" -> Json.obj("untaxedInterest" -> Json.obj("totalTaxableIncome" -> 123)),
      "incomeSources" -> Json.arr(
        Json.obj("employmentId" -> 1, "taxCode" -> "1150L", "name" -> "Employer1", "basisOperation" -> 1),
        Json.obj("employmentId" -> 2, "taxCode" -> "1100L", "name" -> "Employer2", "basisOperation" -> 2)
      )
    )
  }

  def verifyOutgoingDesUpdateHeaders(requestPattern: RequestPatternBuilder): Unit =
    server.verify(
      requestPattern
        .withHeader("Environment", equalTo("local"))
        .withHeader("Authorization", equalTo("Bearer Local"))
        .withHeader("Content-Type", equalTo(TaiConstants.contentType))
        .withHeader("Gov-Uk-Originator-Id", equalTo(desOriginatorId))
        .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
        .withHeader(HeaderNames.xRequestId, equalTo(requestId))
        .withHeader(
          "CorrelationId",
          matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")))

  def verifyOutgoingNpsUpdateHeaders(requestPattern: RequestPatternBuilder): Unit =
    server.verify(
      requestPattern
        .withHeader("Gov-Uk-Originator-Id", equalTo(npsOriginatorId))
        .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
        .withHeader(HeaderNames.xRequestId, equalTo(requestId))
        .withHeader(
          "CorrelationId",
          matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")))

  "Tax Account Connector" when {

    "toggled to use NPS" when {

      "toggled to use confirmedAPI" must {

        "return Tax Account as Json in the response" in new ConnectorSetup {

          override def apiEnabled: Boolean = false
          override def desIsEnabled: Boolean = false

          val url = {
            val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
            s"${path.getPath}"
          }

          server.stubFor(get(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

          val result = Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

          result mustBe jsonResponse

          verifyOutgoingNpsUpdateHeaders(getRequestedFor(urlEqualTo(url)))
        }

        "return a NOT_FOUND response code when NOT_FOUND response " in new ConnectorSetup {

          override def apiEnabled: Boolean = false
          override def desIsEnabled: Boolean = false

          val url = {
            val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
            s"${path.getPath}"
          }

          server.stubFor(
            get(urlEqualTo(url)).willReturn(aResponse()
              .withStatus(NOT_FOUND)
              .withBody("not found")))

          val result = the[NotFoundException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

          result.responseCode mustBe NOT_FOUND
        }

        "return a BAD_REQUEST response code when BAD_REQUEST response " in new ConnectorSetup {

          override def apiEnabled: Boolean = false
          override def desIsEnabled: Boolean = false

          val url = {
            val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
            s"${path.getPath}"
          }

          server.stubFor(
            get(urlEqualTo(url)).willReturn(aResponse()
              .withStatus(BAD_REQUEST)))

          val result = the[BadRequestException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

          result.responseCode mustBe BAD_REQUEST
        }

        "return a 418 response code when 418 response " in new ConnectorSetup {

          override def apiEnabled: Boolean = false
          override def desIsEnabled: Boolean = false

          val url = {
            val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
            s"${path.getPath}"
          }

          server.stubFor(
            get(urlEqualTo(url)).willReturn(aResponse()
              .withStatus(IM_A_TEAPOT)))

          val result = the[HttpException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

          result.responseCode mustBe IM_A_TEAPOT
        }

        "return a INTERNAL_SERVER_ERROR response code when INTERNAL_SERVER_ERROR response " in new ConnectorSetup {

          override def apiEnabled: Boolean = false
          override def desIsEnabled: Boolean = false

          val url = {
            val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
            s"${path.getPath}"
          }

          server.stubFor(
            get(urlEqualTo(url)).willReturn(aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)))

          val result = the[InternalServerException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

          result.responseCode mustBe INTERNAL_SERVER_ERROR
        }

        "return a SERVICE_UNAVAILABLE response code when SERVICE_UNAVAILABLE response " in new ConnectorSetup {

          override def apiEnabled: Boolean = false
          override def desIsEnabled: Boolean = false

          val url = {
            val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
            s"${path.getPath}"
          }

          server.stubFor(
            get(urlEqualTo(url)).willReturn(aResponse()
              .withStatus(SERVICE_UNAVAILABLE)))

          val result = the[HttpException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

          result.responseCode mustBe SERVICE_UNAVAILABLE
        }

        "return a LOCKED response code when LOCKED response " in new ConnectorSetup {

          override def apiEnabled: Boolean = false
          override def desIsEnabled: Boolean = false

          val url = {
            val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
            s"${path.getPath}"
          }

          server.stubFor(
            get(urlEqualTo(url)).willReturn(aResponse()
              .withStatus(LOCKED)))

          val result = the[LockedException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

          result.responseCode mustBe LOCKED
        }

        "toggled to use non confirmedAPI" must {

          "return Tax Account as Json in the response" in new ConnectorSetup {

            override def desIsEnabled: Boolean = false

            val url = {
              val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
              s"${path.getPath}"
            }

            server.stubFor(get(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

            val result = Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

            result mustBe jsonResponse

            verifyOutgoingNpsUpdateHeaders(getRequestedFor(urlEqualTo(url)))
          }

          "return a NOT_FOUND response code when NOT_FOUND response" in new ConnectorSetup {

            override def desIsEnabled: Boolean = false

            val url = {
              val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
              s"${path.getPath}"
            }

            server.stubFor(get(urlEqualTo(url)).willReturn(aResponse()
              .withStatus(NOT_FOUND)))

            val result = the[NotFoundException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

            result.responseCode mustBe NOT_FOUND

          }

          "return a BAD_REQUEST response code when BAD_REQUEST response" in new ConnectorSetup {

            override def desIsEnabled: Boolean = false

            val url = {
              val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
              s"${path.getPath}"
            }

            server.stubFor(get(urlEqualTo(url)).willReturn(aResponse()
              .withStatus(BAD_REQUEST)))

            val result = the[BadRequestException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

            result.responseCode mustBe BAD_REQUEST

          }

          "return a IM_A_TEAPOT response code when IM_A_TEAPOT response" in new ConnectorSetup {

            override def desIsEnabled: Boolean = false

            val url = {
              val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
              s"${path.getPath}"
            }

            server.stubFor(get(urlEqualTo(url)).willReturn(aResponse()
              .withStatus(IM_A_TEAPOT)))

            val result = the[HttpException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

            result.responseCode mustBe IM_A_TEAPOT

          }

          "return a INTERNAL_SERVER_ERROR response code when INTERNAL_SERVER_ERROR response " in new ConnectorSetup {

            override def desIsEnabled: Boolean = false

            val url = {
              val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
              s"${path.getPath}"
            }

            server.stubFor(get(urlEqualTo(url)).willReturn(aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)))

            val result = the[InternalServerException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

            result.responseCode mustBe INTERNAL_SERVER_ERROR
          }

          "return a SERVICE_UNAVAILABLE response code when SERVICE_UNAVAILABLE response " in new ConnectorSetup {

            override def desIsEnabled: Boolean = false

            val url = {
              val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
              s"${path.getPath}"
            }

            server.stubFor(get(urlEqualTo(url)).willReturn(aResponse()
              .withStatus(SERVICE_UNAVAILABLE)))

            val result = the[HttpException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

            result.responseCode mustBe SERVICE_UNAVAILABLE
          }

        }

        "updateTaxCodeIncome" must {

          "update nps with the new tax code income" in new ConnectorSetup {

            override def desUpdateIsEnabled: Boolean = false

            val url = {
              val path = new URL(iabdUrls.npsIabdEmploymentUrl(nino, taxYear, NewEstimatedPay.code))
              s"${path.getPath}"
            }

            server.stubFor(post(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

            Await.result(
              sut.updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345),
              5 seconds
            ) mustBe HodUpdateSuccess

            server.verify(
              postRequestedFor(urlEqualTo(url))
                .withHeader("Gov-Uk-Originator-Id", equalTo(npsOriginatorId))
                .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
                .withHeader(HeaderNames.xRequestId, equalTo(requestId))
                .withHeader("ETag", equalTo("1"))
                .withHeader("X-TXID", equalTo(sessionId))
                .withHeader(
                  "CorrelationId",
                  matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")))
          }

          "return a failure status if the update fails" in new ConnectorSetup {

            override def desUpdateIsEnabled: Boolean = false

            val url = {
              val path = new URL(iabdUrls.npsIabdEmploymentUrl(nino, taxYear, NewEstimatedPay.code))
              s"${path.getPath}"
            }

            server.stubFor(post(urlEqualTo(url)).willReturn(aResponse.withStatus(400)))

            Await.result(
              sut.updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345),
              5 seconds
            ) mustBe HodUpdateFailure
          }

          List(
            BAD_REQUEST,
            NOT_FOUND,
            IM_A_TEAPOT,
            INTERNAL_SERVER_ERROR,
            SERVICE_UNAVAILABLE
          ).foreach { httpStatus =>
            s" return a failure status for $httpStatus  response" in new ConnectorSetup {

              val url = {
                val path = new URL(iabdUrls.npsIabdEmploymentUrl(nino, taxYear, NewEstimatedPay.code))
                s"${path.getPath}"
              }

              server.stubFor(post(urlEqualTo(url)).willReturn(aResponse.withStatus(httpStatus)))

              Await.result(
                sut.updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345),
                5 seconds
              ) mustBe HodUpdateFailure
            }
          }

        }
      }

      "Tax Account History" must {
        "return a Success[JsValue] for valid json" in new ConnectorSetup {

          val taxCodeId = 1

          val json = TaxAccountHistoryFactory.combinedIncomeSourcesTotalLiabilityJson(nino)

          val url = new URL(taxAccountUrls.taxAccountHistoricSnapshotUrl(nino, taxCodeId)).getPath

          server.stubFor(
            get(urlEqualTo(url)).willReturn(ok(json.toString))
          )

          val result = Await.result(sut.taxAccountHistory(nino, taxCodeId), 5.seconds)

          result mustEqual json

          verifyOutgoingDesUpdateHeaders(getRequestedFor(urlEqualTo(url)))
        }

        "return a HttpException" when {

          "connector receives BAD_REQUEST" in new ConnectorSetup {

            val taxCodeId = 1

            val url = new URL(taxAccountUrls.taxAccountHistoricSnapshotUrl(nino, taxCodeId)).getPath

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

          "connector receives NOT_FOUND" in new ConnectorSetup {
            val taxCodeId = 1

            val url = new URL(taxAccountUrls.taxAccountHistoricSnapshotUrl(nino, taxCodeId)).getPath

            server.stubFor(
              get(urlEqualTo(url)).willReturn(
                aResponse().withStatus(NOT_FOUND)
              )
            )

            assertConnectorException[NotFoundException](
              sut.taxAccountHistory(nino, taxCodeId),
              NOT_FOUND,
              ""
            )
          }

          "connector receives IM_A_TEAPOT" in new ConnectorSetup {

            val taxCodeId = 1

            val url = new URL(taxAccountUrls.taxAccountHistoricSnapshotUrl(nino, taxCodeId)).getPath

            server.stubFor(
              get(urlEqualTo(url)).willReturn(
                aResponse().withStatus(IM_A_TEAPOT)
              )
            )

            assertConnectorException[HttpException](
              sut.taxAccountHistory(nino, taxCodeId),
              IM_A_TEAPOT,
              ""
            )
          }

          "connector receives INTERNAL_SERVER_ERROR" in new ConnectorSetup {

            val taxCodeId = 1

            val url = new URL(taxAccountUrls.taxAccountHistoricSnapshotUrl(nino, taxCodeId)).getPath

            server.stubFor(
              get(urlEqualTo(url)).willReturn(
                aResponse().withStatus(INTERNAL_SERVER_ERROR)
              )
            )

            assertConnectorException[InternalServerException](
              sut.taxAccountHistory(nino, taxCodeId),
              INTERNAL_SERVER_ERROR,
              ""
            )
          }

          "connector receives SERVICE_UNAVAILABLE" in new ConnectorSetup {

            val taxCodeId = 1

            val url = new URL(taxAccountUrls.taxAccountHistoricSnapshotUrl(nino, taxCodeId)).getPath

            server.stubFor(
              get(urlEqualTo(url)).willReturn(
                aResponse().withStatus(SERVICE_UNAVAILABLE)
              )
            )

            assertConnectorException[HttpException](
              sut.taxAccountHistory(nino, taxCodeId),
              SERVICE_UNAVAILABLE,
              ""
            )
          }
        }

        "toggled to use DES" when {

          "toggled to use confirmedAPI" must {

            "return Tax Account as Json in the response" in new ConnectorSetup {

              val url = {
                val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
                s"${path.getPath}"
              }

              server
                .stubFor(get(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

              val result = Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result mustBe jsonResponse

              verifyOutgoingDesUpdateHeaders(getRequestedFor(urlEqualTo(url)))
            }

            "return a NOT_FOUND response code when NOT_FOUND response" in new ConnectorSetup {

              val url = {
                val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
                s"${path.getPath}"
              }

              server.stubFor(get(urlEqualTo(url)).willReturn(aResponse()
                .withStatus(NOT_FOUND)))

              val result = the[NotFoundException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe NOT_FOUND

            }

            "return a BAD_REQUEST response code when BAD_REQUEST response" in new ConnectorSetup {

              val url = {
                val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
                s"${path.getPath}"
              }

              server.stubFor(get(urlEqualTo(url)).willReturn(aResponse()
                .withStatus(BAD_REQUEST)))

              val result = the[BadRequestException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe BAD_REQUEST

            }

            "return a IM_A_TEAPOT response code when IM_A_TEAPOT response" in new ConnectorSetup {

              val url = {
                val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
                s"${path.getPath}"
              }

              server.stubFor(get(urlEqualTo(url)).willReturn(aResponse()
                .withStatus(IM_A_TEAPOT)))

              val result = the[HttpException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe IM_A_TEAPOT

            }

            "return a INTERNAL_SERVER_ERROR response code when INTERNAL_SERVER_ERROR response " in new ConnectorSetup {

              val url = {
                val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
                s"${path.getPath}"
              }

              server.stubFor(get(urlEqualTo(url)).willReturn(aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)))

              val result = the[InternalServerException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe INTERNAL_SERVER_ERROR
            }

            "return a SERVICE_UNAVAILABLE response code when SERVICE_UNAVAILABLE response " in new ConnectorSetup {

              val url = {
                val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
                s"${path.getPath}"
              }

              server.stubFor(get(urlEqualTo(url)).willReturn(aResponse()
                .withStatus(SERVICE_UNAVAILABLE)))

              val result = the[HttpException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe SERVICE_UNAVAILABLE
            }

          }

          "toggled to use non confirmedAPI" must {

            "return Tax Account as Json in the response" in new ConnectorSetup {

              override def apiEnabled: Boolean = false

              val url = {
                val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
                s"${path.getPath}"
              }

              server.stubFor(get(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

              Await.result(sut.taxAccount(nino, taxYear), 5 seconds) mustBe jsonResponse

              verifyOutgoingDesUpdateHeaders(getRequestedFor(urlEqualTo(url)))
            }

            "return a NOT_FOUND response code when NOT_FOUND response" in new ConnectorSetup {

              override def apiEnabled: Boolean = false

              val url = {
                val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
                s"${path.getPath}"
              }

              server.stubFor(get(urlEqualTo(url)).willReturn(aResponse()
                .withStatus(NOT_FOUND)))

              val result = the[NotFoundException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe NOT_FOUND

            }

            "return a BAD_REQUEST response code when BAD_REQUEST response" in new ConnectorSetup {

              override def apiEnabled: Boolean = false

              val url = {
                val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
                s"${path.getPath}"
              }

              server.stubFor(get(urlEqualTo(url)).willReturn(aResponse()
                .withStatus(BAD_REQUEST)))

              val result = the[BadRequestException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe BAD_REQUEST

            }

            "return a IM_A_TEAPOT response code when IM_A_TEAPOT response" in new ConnectorSetup {

              override def apiEnabled: Boolean = false

              val url = {
                val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
                s"${path.getPath}"
              }

              server.stubFor(get(urlEqualTo(url)).willReturn(aResponse()
                .withStatus(IM_A_TEAPOT)))

              val result = the[HttpException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe IM_A_TEAPOT

            }

            "return a INTERNAL_SERVER_ERROR response code when INTERNAL_SERVER_ERROR response " in new ConnectorSetup {

              override def apiEnabled: Boolean = false

              val url = {
                val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
                s"${path.getPath}"
              }

              server.stubFor(get(urlEqualTo(url)).willReturn(aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)))

              val result = the[InternalServerException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe INTERNAL_SERVER_ERROR
            }

            "return a SERVICE_UNAVAILABLE response code when SERVICE_UNAVAILABLE response " in new ConnectorSetup {

              override def apiEnabled: Boolean = false

              val url = {
                val path = new URL(taxAccountUrls.taxAccountUrl(nino, taxYear))
                s"${path.getPath}"
              }

              server.stubFor(get(urlEqualTo(url)).willReturn(aResponse()
                .withStatus(SERVICE_UNAVAILABLE)))

              val result = the[HttpException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe SERVICE_UNAVAILABLE
            }

          }

        }

        "updateTaxCodeIncome" must {

          "update des with the new tax code income" in new ConnectorSetup {
            val url = {
              val path = new URL(iabdUrls.desIabdEmploymentUrl(nino, taxYear, NewEstimatedPay.code))
              path.getPath
            }

            server.stubFor(post(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

            val result =
              Await.result(sut.updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345), 5 seconds)

            result mustBe HodUpdateSuccess

            server.verify(
              postRequestedFor(urlEqualTo(url))
                .withHeader("Gov-Uk-Originator-Id", equalTo(desOriginatorId))
                .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
                .withHeader(HeaderNames.xRequestId, equalTo(requestId))
                .withHeader("ETag", equalTo("1"))
                .withHeader("X-TXID", equalTo(sessionId))
                .withHeader(
                  "CorrelationId",
                  matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")))
          }

          "return a failure status if the update fails" in new ConnectorSetup {
            val url = {
              val path = new URL(iabdUrls.desIabdEmploymentUrl(nino, taxYear, NewEstimatedPay.code))
              path.getPath
            }

            server.stubFor(post(urlEqualTo(url)).willReturn(aResponse.withStatus(400)))

            val result =
              Await.result(sut.updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345), 5 seconds)

            result mustBe HodUpdateFailure
          }

          List(
            BAD_REQUEST,
            NOT_FOUND,
            IM_A_TEAPOT,
            INTERNAL_SERVER_ERROR,
            SERVICE_UNAVAILABLE
          ).foreach { httpStatus =>
            s" return a failure status for $httpStatus  response" in new ConnectorSetup {

              val url = {
                val path = new URL(iabdUrls.desIabdEmploymentUrl(nino, taxYear, NewEstimatedPay.code))
                path.getPath
              }

              server.stubFor(post(urlEqualTo(url)).willReturn(aResponse.withStatus(httpStatus)))

              Await.result(
                sut.updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345),
                5 seconds
              ) mustBe HodUpdateFailure
            }
          }
        }
      }
    }
  }
}

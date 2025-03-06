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

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.when
import play.api.http.Status.*
import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{BadRequestException, HeaderNames, HttpException, InternalServerException, LockedException, NotFoundException}
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.tai.config.{DesConfig, HipConfig, NpsConfig}
import uk.gov.hmrc.tai.factory.TaxAccountHistoryFactory
import uk.gov.hmrc.tai.model.admin.HipToggleTaxAccount
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.WireMockHelper

import java.net.URL
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

// scalastyle:off number.of.methods
class TaxAccountConnectorSpec extends ConnectorBaseSpec with WireMockHelper {

  trait ConnectorSetup {

    def desIsEnabled: Boolean = true

    def desUpdateIsEnabled: Boolean = true

    def apiEnabled: Boolean = true

    lazy val npsConfig: NpsConfig = inject[NpsConfig]
    lazy val desConfig: DesConfig = inject[DesConfig]
    lazy val iabdUrls: IabdUrls = inject[IabdUrls]

    def taxAccountUrls: TaxAccountUrls =
      new TaxAccountUrls(npsConfig, desConfig)

    def sut: TaxAccountConnector = new DefaultTaxAccountConnector(
      inject[HttpHandler],
      npsConfig,
      desConfig,
      taxAccountUrls,
      app.injector.instanceOf[HipConfig],
      mockFeatureFlagService
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

  private def verifyOutgoingDesUpdateHeaders(requestPattern: RequestPatternBuilder): Unit =
    server.verify(
      requestPattern
        .withHeader("Gov-Uk-Originator-Id", equalTo(desOriginatorId))
        .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
        .withHeader(HeaderNames.xRequestId, equalTo(requestId))
        .withHeader(
          "CorrelationId",
          matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
        )
    )

  private def verifyOutgoingUpdateHeaders(requestPattern: RequestPatternBuilder): Unit =
    server.verify(
      requestPattern
        .withHeader("Gov-Uk-Originator-Id", equalTo(hipOriginatorId))
        .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
        .withHeader(HeaderNames.xRequestId, equalTo(requestId))
        .withHeader(
          "CorrelationId",
          matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
        )
    )

  private def taxAccountUrl(nino: Nino, taxYear: TaxYear): String =
    s"/v1/api/person/${nino.nino}/tax-account/${taxYear.year}"

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleTaxAccount))).thenReturn(
      Future.successful(FeatureFlag(HipToggleTaxAccount, isEnabled = true))
    )
    ()
  }

  "Tax Account Connector" when {

    "toggled to use HIP" when {

      "toggled to use confirmedAPI" must {

        "return Tax Account as Json in the response" in new ConnectorSetup {

          override def apiEnabled: Boolean = false
          override def desIsEnabled: Boolean = false

          val url: String = taxAccountUrl(nino, taxYear)

          server.stubFor(get(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

          val result: JsValue = Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

          result mustBe jsonResponse

          verifyOutgoingUpdateHeaders(getRequestedFor(urlEqualTo(url)))
        }

        "return a NOT_FOUND response code when NOT_FOUND response " in new ConnectorSetup {

          override def apiEnabled: Boolean = false
          override def desIsEnabled: Boolean = false

          val url: String =
            taxAccountUrl(nino, taxYear)

          server.stubFor(
            get(urlEqualTo(url)).willReturn(
              aResponse()
                .withStatus(NOT_FOUND)
                .withBody("not found")
            )
          )

          val result: NotFoundException =
            the[NotFoundException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

          result.responseCode mustBe NOT_FOUND
        }

        "return a BAD_REQUEST response code when BAD_REQUEST response " in new ConnectorSetup {

          override def apiEnabled: Boolean = false
          override def desIsEnabled: Boolean = false

          val url: String =
            taxAccountUrl(nino, taxYear)

          server.stubFor(
            get(urlEqualTo(url)).willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
            )
          )

          val result: BadRequestException =
            the[BadRequestException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

          result.responseCode mustBe BAD_REQUEST
        }

        "return a 418 response code when 418 response " in new ConnectorSetup {

          override def apiEnabled: Boolean = false
          override def desIsEnabled: Boolean = false

          val url: String =
            taxAccountUrl(nino, taxYear)

          server.stubFor(
            get(urlEqualTo(url)).willReturn(
              aResponse()
                .withStatus(IM_A_TEAPOT)
            )
          )

          val result: HttpException = the[HttpException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

          result.responseCode mustBe IM_A_TEAPOT
        }

        "return a INTERNAL_SERVER_ERROR response code when INTERNAL_SERVER_ERROR response " in new ConnectorSetup {

          override def apiEnabled: Boolean = false
          override def desIsEnabled: Boolean = false

          val url: String =
            taxAccountUrl(nino, taxYear)

          server.stubFor(
            get(urlEqualTo(url)).willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
            )
          )

          val result: InternalServerException =
            the[InternalServerException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

          result.responseCode mustBe INTERNAL_SERVER_ERROR
        }

        "return a SERVICE_UNAVAILABLE response code when SERVICE_UNAVAILABLE response " in new ConnectorSetup {

          override def apiEnabled: Boolean = false
          override def desIsEnabled: Boolean = false

          val url: String =
            taxAccountUrl(nino, taxYear)

          server.stubFor(
            get(urlEqualTo(url)).willReturn(
              aResponse()
                .withStatus(SERVICE_UNAVAILABLE)
            )
          )

          val result: HttpException = the[HttpException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

          result.responseCode mustBe SERVICE_UNAVAILABLE
        }

        "return a not found response code when empty payload response " in new ConnectorSetup {

          override def apiEnabled: Boolean = false
          override def desIsEnabled: Boolean = false

          val url: String =
            taxAccountUrl(nino, taxYear)

          server.stubFor(get(urlEqualTo(url)).willReturn(ok(Json.obj().toString)))

          val result: NotFoundException =
            the[NotFoundException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

          result.responseCode mustBe NOT_FOUND
        }

        "return a LOCKED response code when LOCKED response " in new ConnectorSetup {

          override def apiEnabled: Boolean = false
          override def desIsEnabled: Boolean = false

          val url: String =
            taxAccountUrl(nino, taxYear)

          server.stubFor(
            get(urlEqualTo(url)).willReturn(
              aResponse()
                .withStatus(LOCKED)
            )
          )

          val result: LockedException =
            the[LockedException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

          result.responseCode mustBe LOCKED
        }

        "toggled to use non confirmedAPI" must {

          "return Tax Account as Json in the response" in new ConnectorSetup {

            override def desIsEnabled: Boolean = false

            val url: String =
              taxAccountUrl(nino, taxYear)

            server.stubFor(get(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

            val result: JsValue = Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

            result mustBe jsonResponse

            verifyOutgoingUpdateHeaders(getRequestedFor(urlEqualTo(url)))
          }

          "return a NOT_FOUND response code when NOT_FOUND response" in new ConnectorSetup {

            override def desIsEnabled: Boolean = false

            val url: String =
              taxAccountUrl(nino, taxYear)

            server.stubFor(
              get(urlEqualTo(url)).willReturn(
                aResponse()
                  .withStatus(NOT_FOUND)
              )
            )

            val result: NotFoundException =
              the[NotFoundException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

            result.responseCode mustBe NOT_FOUND

          }

          "return a BAD_REQUEST response code when BAD_REQUEST response" in new ConnectorSetup {

            override def desIsEnabled: Boolean = false

            val url: String =
              taxAccountUrl(nino, taxYear)

            server.stubFor(
              get(urlEqualTo(url)).willReturn(
                aResponse()
                  .withStatus(BAD_REQUEST)
              )
            )

            val result: BadRequestException =
              the[BadRequestException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

            result.responseCode mustBe BAD_REQUEST

          }

          "return a IM_A_TEAPOT response code when IM_A_TEAPOT response" in new ConnectorSetup {

            override def desIsEnabled: Boolean = false

            val url: String =
              taxAccountUrl(nino, taxYear)

            server.stubFor(
              get(urlEqualTo(url)).willReturn(
                aResponse()
                  .withStatus(IM_A_TEAPOT)
              )
            )

            val result: HttpException =
              the[HttpException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

            result.responseCode mustBe IM_A_TEAPOT

          }

          "return a INTERNAL_SERVER_ERROR response code when INTERNAL_SERVER_ERROR response " in new ConnectorSetup {

            override def desIsEnabled: Boolean = false

            val url: String =
              taxAccountUrl(nino, taxYear)

            server.stubFor(
              get(urlEqualTo(url)).willReturn(
                aResponse()
                  .withStatus(INTERNAL_SERVER_ERROR)
              )
            )

            val result: InternalServerException =
              the[InternalServerException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

            result.responseCode mustBe INTERNAL_SERVER_ERROR
          }

          "return a SERVICE_UNAVAILABLE response code when SERVICE_UNAVAILABLE response " in new ConnectorSetup {

            override def desIsEnabled: Boolean = false

            val url: String =
              taxAccountUrl(nino, taxYear)

            server.stubFor(
              get(urlEqualTo(url)).willReturn(
                aResponse()
                  .withStatus(SERVICE_UNAVAILABLE)
              )
            )

            val result: HttpException =
              the[HttpException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

            result.responseCode mustBe SERVICE_UNAVAILABLE
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

          verifyOutgoingDesUpdateHeaders(getRequestedFor(urlEqualTo(url)))
        }

        "return a HttpException" when {

          "connector receives BAD_REQUEST" in new ConnectorSetup {

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

          "connector receives NOT_FOUND" in new ConnectorSetup {
            val taxCodeId = 1

            val url: String = new URL(taxAccountUrls.taxAccountHistoricSnapshotUrl(nino, taxCodeId)).getPath

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

            val url: String = new URL(taxAccountUrls.taxAccountHistoricSnapshotUrl(nino, taxCodeId)).getPath

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

            val url: String = new URL(taxAccountUrls.taxAccountHistoricSnapshotUrl(nino, taxCodeId)).getPath

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

            val url: String = new URL(taxAccountUrls.taxAccountHistoricSnapshotUrl(nino, taxCodeId)).getPath

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

              val url: String =
                taxAccountUrl(nino, taxYear)

              server
                .stubFor(get(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

              val result: JsValue = Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result mustBe jsonResponse

              verifyOutgoingUpdateHeaders(getRequestedFor(urlEqualTo(url)))
            }

            "return a NOT_FOUND response code when NOT_FOUND response" in new ConnectorSetup {

              val url: String =
                taxAccountUrl(nino, taxYear)

              server.stubFor(
                get(urlEqualTo(url)).willReturn(
                  aResponse()
                    .withStatus(NOT_FOUND)
                )
              )

              val result: NotFoundException =
                the[NotFoundException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe NOT_FOUND

            }

            "return a BAD_REQUEST response code when BAD_REQUEST response" in new ConnectorSetup {

              val url: String =
                taxAccountUrl(nino, taxYear)

              server.stubFor(
                get(urlEqualTo(url)).willReturn(
                  aResponse()
                    .withStatus(BAD_REQUEST)
                )
              )

              val result: BadRequestException =
                the[BadRequestException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe BAD_REQUEST

            }

            "return a IM_A_TEAPOT response code when IM_A_TEAPOT response" in new ConnectorSetup {

              val url: String =
                taxAccountUrl(nino, taxYear)

              server.stubFor(
                get(urlEqualTo(url)).willReturn(
                  aResponse()
                    .withStatus(IM_A_TEAPOT)
                )
              )

              val result: HttpException =
                the[HttpException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe IM_A_TEAPOT

            }

            "return a INTERNAL_SERVER_ERROR response code when INTERNAL_SERVER_ERROR response " in new ConnectorSetup {

              val url: String =
                taxAccountUrl(nino, taxYear)

              server.stubFor(
                get(urlEqualTo(url)).willReturn(
                  aResponse()
                    .withStatus(INTERNAL_SERVER_ERROR)
                )
              )

              val result: InternalServerException =
                the[InternalServerException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe INTERNAL_SERVER_ERROR
            }

            "return a SERVICE_UNAVAILABLE response code when SERVICE_UNAVAILABLE response " in new ConnectorSetup {

              val url: String =
                taxAccountUrl(nino, taxYear)

              server.stubFor(
                get(urlEqualTo(url)).willReturn(
                  aResponse()
                    .withStatus(SERVICE_UNAVAILABLE)
                )
              )

              val result: HttpException =
                the[HttpException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe SERVICE_UNAVAILABLE
            }

          }

          "toggled to use non confirmedAPI" must {

            "return Tax Account as Json in the response" in new ConnectorSetup {

              override def apiEnabled: Boolean = false

              val url: String =
                taxAccountUrl(nino, taxYear)

              server.stubFor(get(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

              Await.result(sut.taxAccount(nino, taxYear), 5 seconds) mustBe jsonResponse

              verifyOutgoingUpdateHeaders(getRequestedFor(urlEqualTo(url)))
            }

            "return a NOT_FOUND response code when NOT_FOUND response" in new ConnectorSetup {

              override def apiEnabled: Boolean = false

              val url: String =
                taxAccountUrl(nino, taxYear)

              server.stubFor(
                get(urlEqualTo(url)).willReturn(
                  aResponse()
                    .withStatus(NOT_FOUND)
                )
              )

              val result: NotFoundException =
                the[NotFoundException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe NOT_FOUND

            }

            "return a BAD_REQUEST response code when BAD_REQUEST response" in new ConnectorSetup {

              override def apiEnabled: Boolean = false

              val url: String =
                taxAccountUrl(nino, taxYear)

              server.stubFor(
                get(urlEqualTo(url)).willReturn(
                  aResponse()
                    .withStatus(BAD_REQUEST)
                )
              )

              val result: BadRequestException =
                the[BadRequestException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe BAD_REQUEST

            }

            "return a IM_A_TEAPOT response code when IM_A_TEAPOT response" in new ConnectorSetup {

              override def apiEnabled: Boolean = false

              val url: String =
                taxAccountUrl(nino, taxYear)

              server.stubFor(
                get(urlEqualTo(url)).willReturn(
                  aResponse()
                    .withStatus(IM_A_TEAPOT)
                )
              )

              val result: HttpException =
                the[HttpException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe IM_A_TEAPOT

            }

            "return a INTERNAL_SERVER_ERROR response code when INTERNAL_SERVER_ERROR response " in new ConnectorSetup {

              override def apiEnabled: Boolean = false

              val url: String =
                taxAccountUrl(nino, taxYear)

              server.stubFor(
                get(urlEqualTo(url)).willReturn(
                  aResponse()
                    .withStatus(INTERNAL_SERVER_ERROR)
                )
              )

              val result: InternalServerException =
                the[InternalServerException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe INTERNAL_SERVER_ERROR
            }

            "return a SERVICE_UNAVAILABLE response code when SERVICE_UNAVAILABLE response " in new ConnectorSetup {

              override def apiEnabled: Boolean = false

              val url: String =
                taxAccountUrl(nino, taxYear)

              server.stubFor(
                get(urlEqualTo(url)).willReturn(
                  aResponse()
                    .withStatus(SERVICE_UNAVAILABLE)
                )
              )

              val result: HttpException =
                the[HttpException] thrownBy Await.result(sut.taxAccount(nino, taxYear), 5 seconds)

              result.responseCode mustBe SERVICE_UNAVAILABLE
            }

          }

        }
      }
    }
  }
}

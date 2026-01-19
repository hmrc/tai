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
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.when
import play.api.http.Status.*
import play.api.libs.json.*
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.http.{BadRequestException, HeaderNames, HttpException, NotFoundException}
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.tai.config.{DesConfig, HipConfig}
import uk.gov.hmrc.tai.model.admin.HipIabdsUpdateExpensesToggle
import uk.gov.hmrc.tai.model.domain.IabdDetails
import uk.gov.hmrc.tai.model.domain.response.{HodUpdateFailure, HodUpdateSuccess}
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.nps2.IabdType.{NewEstimatedPay, hipMapping}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.{IabdUpdateAmount, UpdateIabdEmployeeExpense}
import uk.gov.hmrc.tai.util.TaiConstants

import java.time.LocalDate
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class IabdConnectorSpec extends ConnectorBaseSpec {

  implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  lazy val iabdUrls: IabdUrls = inject[IabdUrls]
  val iabdType: Int = 27
  val iabdHipType: String = "New-Estimated-Pay-(027)"
  private def hipConfig = inject[HipConfig]

  def sut(): IabdConnector = new DefaultIabdConnector(
    inject[HttpHandler],
    inject[DesConfig],
    hipConfig,
    mockFeatureFlagService
  )
  private val iabdTypeArgument: String = hipMapping(iabdType)
  val taxYear: TaxYear = TaxYear()

  lazy val hipIabdsUrl: String =
    s"/v1/api/iabd/taxpayer/$nino/tax-year/${taxYear.year}?type=New-Estimated-Pay-(027)"

  val desBaseUrl: String = s"/pay-as-you-earn/individuals/${nino.nino}"
  val updateExpensesUrl: String = s"$desBaseUrl/iabds/${taxYear.year}/$iabdType"
  val updateExpenseshipIabdsUrl: String = s"/v1/api/iabd/taxpayer/$nino/tax-year/${taxYear.year}/type/$iabdHipType"

  val iabdDetails: IabdDetails =
    IabdDetails(None, Some(15), Some(10), None, Some(LocalDate.of(2017, 4, 10)))

  private val json = Json.arr(
    Json.obj(
      "taxYear"         -> 2017,
      "type"            -> 10,
      "source"          -> 15,
      "grossAmount"     -> JsNull,
      "receiptDate"     -> JsNull,
      "captureDate"     -> "10/04/2017",
      "typeDescription" -> "Total gift aid Payments",
      "netAmount"       -> 100
    )
  )

  private val hipJson = Json.arr(
    Json.obj(
      "taxYear"     -> 2017,
      "type"        -> "Balancing Charge (010)",
      "source"      -> "TELEPHONE CALL",
      "receiptDate" -> "2017-04-09",
      "captureDate" -> "2017-04-10"
    )
  )

  val jsonResponse: JsObject = Json.obj(
    "taxYear"        -> taxYear.year,
    "totalLiability" -> Json.obj("untaxedInterest" -> Json.obj("totalTaxableIncome" -> 123)),
    "incomeSources" -> Json.arr(
      Json.obj("employmentId" -> 1, "taxCode" -> "1150L", "name" -> "Employer1", "basisOperation" -> 1),
      Json.obj("employmentId" -> 2, "taxCode" -> "1100L", "name" -> "Employer2", "basisOperation" -> 2)
    )
  )

  val updateAmount: List[IabdUpdateAmount] = List(IabdUpdateAmount(Some(1), 20000))

  implicit lazy val iabdFormats: Format[IabdUpdateAmount] = IabdUpdateAmount.formats

  "iabds" when {
    "toggle to use HIP" must {
      "return IABD json" in {
        server.stubFor(
          get(urlEqualTo(hipIabdsUrl)).willReturn(aResponse().withStatus(OK).withBody(hipJson.toString()))
        )

        val actualJson = sut().getIabdsForType(nino, taxYear.year, iabdHipType).futureValue
        actualJson mustBe hipJson

        server.verify(
          getRequestedFor(urlEqualTo(hipIabdsUrl))
            .withHeader("Gov-Uk-Originator-Id", equalTo(hipOriginatorId))
            .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
            .withHeader(HeaderNames.xRequestId, equalTo(requestId))
            .withHeader(
              "CorrelationId",
              matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
            )
        )

      }

      "return empty json" when {
        "looking for next tax year" in {
          server.stubFor(
            get(urlEqualTo(hipIabdsUrl)).willReturn(aResponse().withStatus(OK).withBody(json.toString()))
          )

          sut().getIabdsForType(nino, taxYear.next.year, iabdHipType).futureValue mustBe JsArray.empty
        }

        "looking for cy+2 year" in {
          server.stubFor(
            get(urlEqualTo(hipIabdsUrl)).willReturn(aResponse().withStatus(OK).withBody(json.toString()))
          )

          sut().getIabdsForType(nino, taxYear.next.next.year, iabdHipType).futureValue mustBe JsArray.empty
        }
      }

      "return error json" when {
        "NOT_FOUND is returned by the Nps API" in {
          server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))

          assertConnectorException[NotFoundException](
            sut().getIabdsForType(nino, taxYear.year, iabdHipType),
            NOT_FOUND,
            ""
          )
        }
      }

      "return an error" when {
        "a 400 occurs" in {
          server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(aResponse().withStatus(BAD_REQUEST)))

          sut().getIabdsForType(nino, taxYear.year, iabdHipType).failed.futureValue mustBe a[BadRequestException]
        }

        List(
          IM_A_TEAPOT,
          INTERNAL_SERVER_ERROR,
          SERVICE_UNAVAILABLE
        ).foreach { httpResponse =>
          s"a $httpResponse occurs" in {
            server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(aResponse().withStatus(httpResponse)))

            sut().getIabdsForType(nino, taxYear.year, iabdHipType).failed.futureValue mustBe a[HttpException]
          }
        }
      }
    }
  }

  "updateTaxCodeIncome with HipToggleEmploymentIabds on" when {
    "update nps with the new tax code income" in {
      val url: String =
        s"${hipConfig.path}/iabd/taxpayer/$nino/tax-year/${taxYear.year}/employment/1/type/$iabdTypeArgument"

      server.stubFor(put(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

      Await.result(
        sut().updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345),
        5 seconds
      ) mustBe HodUpdateSuccess

      server.verify(
        putRequestedFor(urlEqualTo(url))
          .withHeader("gov-uk-originator-id", equalTo(hipOriginatorId))
          .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
          .withHeader(HeaderNames.xRequestId, equalTo(requestId))
          .withoutHeader("ETag")
          .withoutHeader("X-TXID")
          .withHeader(
            "correlationId",
            matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
          )
          .withRequestBody(equalTo("""{"grossAmount":12345,"source":"Cutover","currentOptimisticLock":1}"""))
      )
    }

    "return a failure status if the update fails" in {
      val url: String =
        s"${hipConfig.path}/iabd/taxpayer/$nino/tax-year/${taxYear.year}/employment/1/type/$iabdTypeArgument"

      server.stubFor(put(urlEqualTo(url)).willReturn(aResponse.withStatus(400)))

      Await.result(
        sut().updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345),
        5 seconds
      ) mustBe HodUpdateFailure

      server.verify(
        putRequestedFor(urlEqualTo(url))
          .withHeader("gov-uk-originator-id", equalTo(hipOriginatorId))
          .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
          .withHeader(HeaderNames.xRequestId, equalTo(requestId))
          .withHeader(
            "correlationId",
            matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
          )
      )
    }

    List(
      BAD_REQUEST,
      NOT_FOUND,
      IM_A_TEAPOT,
      INTERNAL_SERVER_ERROR,
      SERVICE_UNAVAILABLE
    ).foreach { httpStatus =>
      s" return a failure status for $httpStatus  response" in {

        val url: String =
          s"${hipConfig.path}/iabd/taxpayer/$nino/tax-year/${taxYear.year}/employment/1/type/$iabdTypeArgument"

        server.stubFor(put(urlEqualTo(url)).willReturn(aResponse.withStatus(httpStatus)))

        Await.result(
          sut().updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345),
          5 seconds
        ) mustBe HodUpdateFailure
        server.verify(
          putRequestedFor(urlEqualTo(url))
            .withHeader("gov-uk-originator-id", equalTo(hipOriginatorId))
            .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
            .withHeader(HeaderNames.xRequestId, equalTo(requestId))
            .withHeader(
              "correlationId",
              matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
            )
        )
      }
    }
  }

  "updateExpensesData" must {
    "using NPS" must {
      "return a status of 200 OK" when {

        "updating expenses data in DES using a valid update amount" in {

          val json = Json.toJson(updateAmount)

          server.stubFor(
            post(urlEqualTo(updateExpensesUrl)).willReturn(aResponse().withStatus(OK).withBody(json.toString()))
          )

          val response =
            sut()
              .updateExpensesData(
                nino = nino,
                year = taxYear.year,
                iabdType = iabdType,
                version = 1,
                expensesData = UpdateIabdEmployeeExpense(100, None),
                apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
              )
              .futureValue

          response.status mustBe OK
          response.json mustBe json

          server.verify(
            postRequestedFor(urlEqualTo(updateExpensesUrl))
              .withHeader("Environment", equalTo("local"))
              .withHeader("Authorization", equalTo("Bearer desAuthorization"))
              .withHeader("Content-Type", equalTo(TaiConstants.contentType))
              .withHeader("Etag", equalTo("1"))
              .withHeader("Originator-Id", equalTo(desPtaOriginatorId))
              .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
              .withHeader(HeaderNames.xRequestId, equalTo(requestId))
              .withHeader(
                "CorrelationId",
                matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
              )
          )
        }
      }

      "return a 2xx status" when {

        "the connector returns NO_CONTENT" in {

          server.stubFor(
            post(urlEqualTo(updateExpensesUrl)).willReturn(aResponse().withStatus(NO_CONTENT))
          )

          val response =
            sut()
              .updateExpensesData(
                nino = nino,
                year = taxYear.year,
                iabdType = iabdType,
                version = 1,
                expensesData = UpdateIabdEmployeeExpense(100, None),
                apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
              )
              .futureValue

          response.status mustBe NO_CONTENT
        }

        "the connector returns ACCEPTED" in {

          server.stubFor(
            post(urlEqualTo(updateExpensesUrl)).willReturn(aResponse().withStatus(ACCEPTED))
          )

          val response =
            sut()
              .updateExpensesData(
                nino = nino,
                year = taxYear.year,
                iabdType = iabdType,
                version = 1,
                expensesData = UpdateIabdEmployeeExpense(100, None),
                apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
              )
              .futureValue

          response.status mustBe ACCEPTED
        }
      }

      "throw a HttpException" when {

        "a 4xx response is returned" in {

          val exMessage = "Bad request"

          server.stubFor(
            post(urlEqualTo(updateExpensesUrl)).willReturn(aResponse().withStatus(BAD_REQUEST).withBody(exMessage))
          )

          assertConnectorException[HttpException](
            sut().updateExpensesData(
              nino = nino,
              year = taxYear.year,
              iabdType = iabdType,
              version = 1,
              expensesData = UpdateIabdEmployeeExpense(100, None),
              apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
            ),
            BAD_REQUEST,
            exMessage
          )
        }

        "a BAD_REQUEST response is returned for BAD_REQUEST response status" in {

          val exMessage = "Bad request"

          server.stubFor(
            post(urlEqualTo(updateExpensesUrl)).willReturn(aResponse().withStatus(BAD_REQUEST).withBody(exMessage))
          )

          assertConnectorException[HttpException](
            sut().updateExpensesData(
              nino = nino,
              year = taxYear.year,
              iabdType = iabdType,
              version = 1,
              expensesData = UpdateIabdEmployeeExpense(100, None),
              apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
            ),
            BAD_REQUEST,
            exMessage
          )
        }

        "a NOT_FOUND response is returned for NOT_FOUND response status" in {

          val exMessage = "Not Found"

          server.stubFor(
            post(urlEqualTo(updateExpensesUrl)).willReturn(aResponse().withStatus(NOT_FOUND).withBody(exMessage))
          )

          assertConnectorException[HttpException](
            sut().updateExpensesData(
              nino = nino,
              year = taxYear.year,
              iabdType = iabdType,
              version = 1,
              expensesData = UpdateIabdEmployeeExpense(100, None),
              apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
            ),
            NOT_FOUND,
            exMessage
          )
        }

        "a 418 response is returned for 418 response status" in {

          val exMessage = "An error occurred"

          server.stubFor(
            post(urlEqualTo(updateExpensesUrl)).willReturn(aResponse().withStatus(IM_A_TEAPOT).withBody(exMessage))
          )

          assertConnectorException[HttpException](
            sut().updateExpensesData(
              nino = nino,
              year = taxYear.year,
              iabdType = iabdType,
              version = 1,
              expensesData = UpdateIabdEmployeeExpense(100, None),
              apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
            ),
            IM_A_TEAPOT,
            exMessage
          )
        }

        "a INTERNAL_SERVER_ERROR response is returned for INTERNAL_SERVER_ERROR response status" in {

          val exMessage = "Internal Server Error"

          server.stubFor(
            post(urlEqualTo(updateExpensesUrl))
              .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR).withBody(exMessage))
          )

          assertConnectorException[HttpException](
            sut().updateExpensesData(
              nino = nino,
              year = taxYear.year,
              iabdType = iabdType,
              version = 1,
              expensesData = UpdateIabdEmployeeExpense(100, None),
              apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
            ),
            INTERNAL_SERVER_ERROR,
            exMessage
          )
        }

        "a SERVICE_UNAVAILABLE response is returned for SERVICE_UNAVAILABLE response status" in {

          val exMessage = "Service unavailable"

          server.stubFor(
            post(urlEqualTo(updateExpensesUrl))
              .willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE).withBody(exMessage))
          )

          assertConnectorException[HttpException](
            sut().updateExpensesData(
              nino = nino,
              year = taxYear.year,
              iabdType = iabdType,
              version = 1,
              expensesData = UpdateIabdEmployeeExpense(100, None),
              apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
            ),
            SERVICE_UNAVAILABLE,
            exMessage
          )
        }

        "a 5xx response is returned" in {

          val exMessage = "An error occurred"

          server.stubFor(
            post(urlEqualTo(updateExpensesUrl))
              .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR).withBody(exMessage))
          )

          assertConnectorException[HttpException](
            sut().updateExpensesData(
              nino = nino,
              year = taxYear.year,
              iabdType = iabdType,
              version = 1,
              expensesData = UpdateIabdEmployeeExpense(100, None),
              apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
            ),
            INTERNAL_SERVER_ERROR,
            exMessage
          )
        }
      }
    }
    "using HIP" must {
      "return a status of 200 OK" when {

        "updating expenses data in HIP using a valid update amount" in {
          when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipIabdsUpdateExpensesToggle))).thenReturn(
            Future.successful(FeatureFlag(HipIabdsUpdateExpensesToggle, isEnabled = true))
          )

          val json = Json.toJson(updateAmount)

          server.stubFor(
            put(urlEqualTo(updateExpenseshipIabdsUrl)).willReturn(aResponse().withStatus(OK).withBody(json.toString()))
          )

          val response =
            sut()
              .updateExpensesData(
                nino = nino,
                year = taxYear.year,
                iabdType = iabdType,
                version = 1,
                expensesData = UpdateIabdEmployeeExpense(100, None),
                apiType = APITypes.HipIabdUpdateEmployeeExpensesAPI
              )
              .futureValue

          response.status mustBe OK
          response.json mustBe json

          server.verify(
            putRequestedFor(urlEqualTo(updateExpenseshipIabdsUrl))
              .withHeader("Authorization", equalTo("Basic Y2xpZW50SWQ6Y2xpZW50U2VjcmV0"))
              .withHeader("Content-Type", equalTo(TaiConstants.contentType))
              .withHeader("Gov-Uk-Originator-Id", equalTo(hipOriginatorId))
              .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
              .withHeader(HeaderNames.xRequestId, equalTo(requestId))
              .withHeader(
                "CorrelationId",
                matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
              )
          )
        }
      }

      "return a 2xx status" when {

        "the connector returns NO_CONTENT" in {
          when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipIabdsUpdateExpensesToggle))).thenReturn(
            Future.successful(FeatureFlag(HipIabdsUpdateExpensesToggle, isEnabled = true))
          )

          server.stubFor(
            put(urlEqualTo(updateExpenseshipIabdsUrl)).willReturn(aResponse().withStatus(NO_CONTENT))
          )

          val response =
            sut()
              .updateExpensesData(
                nino = nino,
                year = taxYear.year,
                iabdType = iabdType,
                version = 1,
                expensesData = UpdateIabdEmployeeExpense(100, None),
                apiType = APITypes.HipIabdUpdateEmployeeExpensesAPI
              )
              .futureValue

          response.status mustBe NO_CONTENT
        }

        "the connector returns ACCEPTED" in {
          when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipIabdsUpdateExpensesToggle))).thenReturn(
            Future.successful(FeatureFlag(HipIabdsUpdateExpensesToggle, isEnabled = true))
          )

          server.stubFor(
            put(urlEqualTo(updateExpenseshipIabdsUrl)).willReturn(aResponse().withStatus(ACCEPTED))
          )

          val response =
            sut()
              .updateExpensesData(
                nino = nino,
                year = taxYear.year,
                iabdType = iabdType,
                version = 1,
                expensesData = UpdateIabdEmployeeExpense(100, None),
                apiType = APITypes.HipIabdUpdateEmployeeExpensesAPI
              )
              .futureValue

          response.status mustBe ACCEPTED
        }
      }

      "throw a HttpException" when {

        "a 4xx response is returned" in {
          when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipIabdsUpdateExpensesToggle))).thenReturn(
            Future.successful(FeatureFlag(HipIabdsUpdateExpensesToggle, isEnabled = true))
          )

          val exMessage = "Bad request"

          server.stubFor(
            put(urlEqualTo(updateExpenseshipIabdsUrl)).willReturn(
              aResponse().withStatus(BAD_REQUEST).withBody(exMessage)
            )
          )

          assertConnectorException[HttpException](
            sut().updateExpensesData(
              nino = nino,
              year = taxYear.year,
              iabdType = iabdType,
              version = 1,
              expensesData = UpdateIabdEmployeeExpense(100, None),
              apiType = APITypes.HipIabdUpdateEmployeeExpensesAPI
            ),
            BAD_REQUEST,
            exMessage
          )
        }

        "a BAD_REQUEST response is returned for BAD_REQUEST response status" in {
          when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipIabdsUpdateExpensesToggle))).thenReturn(
            Future.successful(FeatureFlag(HipIabdsUpdateExpensesToggle, isEnabled = true))
          )

          val exMessage = "Bad request"

          server.stubFor(
            put(urlEqualTo(updateExpenseshipIabdsUrl)).willReturn(
              aResponse().withStatus(BAD_REQUEST).withBody(exMessage)
            )
          )

          assertConnectorException[HttpException](
            sut().updateExpensesData(
              nino = nino,
              year = taxYear.year,
              iabdType = iabdType,
              version = 1,
              expensesData = UpdateIabdEmployeeExpense(100, None),
              apiType = APITypes.HipIabdUpdateEmployeeExpensesAPI
            ),
            BAD_REQUEST,
            exMessage
          )
        }

        "a NOT_FOUND response is returned for NOT_FOUND response status" in {
          when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipIabdsUpdateExpensesToggle))).thenReturn(
            Future.successful(FeatureFlag(HipIabdsUpdateExpensesToggle, isEnabled = true))
          )

          val exMessage = "Not Found"

          server.stubFor(
            put(urlEqualTo(updateExpenseshipIabdsUrl)).willReturn(
              aResponse().withStatus(NOT_FOUND).withBody(exMessage)
            )
          )

          assertConnectorException[HttpException](
            sut().updateExpensesData(
              nino = nino,
              year = taxYear.year,
              iabdType = iabdType,
              version = 1,
              expensesData = UpdateIabdEmployeeExpense(100, None),
              apiType = APITypes.HipIabdUpdateEmployeeExpensesAPI
            ),
            NOT_FOUND,
            exMessage
          )
        }

        "a 418 response is returned for 418 response status" in {
          when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipIabdsUpdateExpensesToggle))).thenReturn(
            Future.successful(FeatureFlag(HipIabdsUpdateExpensesToggle, isEnabled = true))
          )

          val exMessage = "An error occurred"

          server.stubFor(
            put(urlEqualTo(updateExpenseshipIabdsUrl)).willReturn(
              aResponse().withStatus(IM_A_TEAPOT).withBody(exMessage)
            )
          )

          assertConnectorException[HttpException](
            sut().updateExpensesData(
              nino = nino,
              year = taxYear.year,
              iabdType = iabdType,
              version = 1,
              expensesData = UpdateIabdEmployeeExpense(100, None),
              apiType = APITypes.HipIabdUpdateEmployeeExpensesAPI
            ),
            IM_A_TEAPOT,
            exMessage
          )
        }

        "a INTERNAL_SERVER_ERROR response is returned for INTERNAL_SERVER_ERROR response status" in {
          when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipIabdsUpdateExpensesToggle))).thenReturn(
            Future.successful(FeatureFlag(HipIabdsUpdateExpensesToggle, isEnabled = true))
          )

          val exMessage = "Internal Server Error"

          server.stubFor(
            put(urlEqualTo(updateExpenseshipIabdsUrl))
              .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR).withBody(exMessage))
          )

          assertConnectorException[HttpException](
            sut().updateExpensesData(
              nino = nino,
              year = taxYear.year,
              iabdType = iabdType,
              version = 1,
              expensesData = UpdateIabdEmployeeExpense(100, None),
              apiType = APITypes.HipIabdUpdateEmployeeExpensesAPI
            ),
            INTERNAL_SERVER_ERROR,
            exMessage
          )
        }

        "a SERVICE_UNAVAILABLE response is returned for SERVICE_UNAVAILABLE response status" in {
          when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipIabdsUpdateExpensesToggle))).thenReturn(
            Future.successful(FeatureFlag(HipIabdsUpdateExpensesToggle, isEnabled = true))
          )

          val exMessage = "Service unavailable"

          server.stubFor(
            put(urlEqualTo(updateExpenseshipIabdsUrl))
              .willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE).withBody(exMessage))
          )

          assertConnectorException[HttpException](
            sut().updateExpensesData(
              nino = nino,
              year = taxYear.year,
              iabdType = iabdType,
              version = 1,
              expensesData = UpdateIabdEmployeeExpense(100, None),
              apiType = APITypes.HipIabdUpdateEmployeeExpensesAPI
            ),
            SERVICE_UNAVAILABLE,
            exMessage
          )
        }

        "a 5xx response is returned" in {
          when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipIabdsUpdateExpensesToggle))).thenReturn(
            Future.successful(FeatureFlag(HipIabdsUpdateExpensesToggle, isEnabled = true))
          )

          val exMessage = "An error occurred"

          server.stubFor(
            put(urlEqualTo(updateExpenseshipIabdsUrl))
              .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR).withBody(exMessage))
          )

          assertConnectorException[HttpException](
            sut().updateExpensesData(
              nino = nino,
              year = taxYear.year,
              iabdType = iabdType,
              version = 1,
              expensesData = UpdateIabdEmployeeExpense(100, None),
              apiType = APITypes.HipIabdUpdateEmployeeExpensesAPI
            ),
            INTERNAL_SERVER_ERROR,
            exMessage
          )
        }
      }
    }
  }
}

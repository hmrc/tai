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
import play.api.http.Status._
import play.api.libs.json.{JsNull, JsObject, Json, Writes}
import play.api.test.FakeRequest
import uk.gov.hmrc.http.{BadRequestException, HeaderNames, HttpException, InternalServerException, NotFoundException}
import uk.gov.hmrc.tai.config.{DesConfig, NpsConfig}
import uk.gov.hmrc.tai.controllers.predicates.AuthenticatedRequest
import uk.gov.hmrc.tai.model.{IabdUpdateAmount, IabdUpdateAmountFormats, UpdateIabdEmployeeExpense}
import uk.gov.hmrc.tai.model.domain.formatters.IabdDetails
import uk.gov.hmrc.tai.model.domain.response.{HodUpdateFailure, HodUpdateSuccess}
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.nps.NpsIabdRoot
import uk.gov.hmrc.tai.model.nps2.IabdType.NewEstimatedPay
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.TaiConstants

import java.net.URL
import java.time.LocalDate
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class IabdConnectorSpec extends ConnectorBaseSpec {

  implicit val authenticatedRequest = AuthenticatedRequest(FakeRequest(), nino)
  lazy val iabdUrls: IabdUrls = inject[IabdUrls]
  val iabdType: Int = 27

  def sut(): IabdConnector = new DefaultIabdConnector(inject[HttpHandler], inject[NpsConfig],
    inject[DesConfig],
    iabdUrls, inject[IabdUpdateAmountFormats])

  val taxYear: TaxYear = TaxYear()

  val npsUrl: String = s"/nps-hod-service/services/nps/person/${nino.nino}/iabds/${taxYear.year}"

  val desBaseUrl: String = s"/pay-as-you-earn/individuals/${nino.nino}"
  val iabdsUrl: String = s"$desBaseUrl/iabds/tax-year/${taxYear.year}"
  val iabdsForTypeUrl: String = s"$iabdsUrl?type=$iabdType"
  val updateExpensesUrl: String = s"$desBaseUrl/iabds/${taxYear.year}/$iabdType"

  val iabdDetails: IabdDetails =
    IabdDetails(Some(nino.withoutSuffix), None, Some(15), Some(10), None, Some(LocalDate.of(2017, 4, 10)))

  private val json = Json.arr(
    Json.obj(
      "nino"            -> nino.withoutSuffix,
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

  val jsonResponse: JsObject = Json.obj(
    "taxYear" -> taxYear.year,
    "totalLiability" -> Json.obj("untaxedInterest" -> Json.obj("totalTaxableIncome" -> 123)),
    "incomeSources" -> Json.arr(
      Json.obj("employmentId" -> 1, "taxCode" -> "1150L", "name" -> "Employer1", "basisOperation" -> 1),
      Json.obj("employmentId" -> 2, "taxCode" -> "1100L", "name" -> "Employer2", "basisOperation" -> 2)
    )
  )

  val updateAmount = List(IabdUpdateAmount(1, 20000))

  implicit lazy val iabdWrites: Writes[IabdUpdateAmount] =
    inject[IabdUpdateAmountFormats].iabdUpdateAmountWrites
  
  "iabds" when {
    "toggled to use NPS" must {
      "return IABD json" in {

        server.stubFor(
          get(urlEqualTo(npsUrl)).willReturn(aResponse().withStatus(OK).withBody(json.toString()))
        )

        sut().iabds(nino, taxYear).futureValue mustBe List(iabdDetails)

        server.verify(
          getRequestedFor(urlEqualTo(npsUrl))
            .withHeader("Gov-Uk-Originator-Id", equalTo(npsOriginatorId))
            .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
            .withHeader(HeaderNames.xRequestId, equalTo(requestId))
            .withHeader(
              "CorrelationId",
              matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")))

      }

      "return empty json" when {
        "looking for next tax year" in {

          server.stubFor(
            get(urlEqualTo(npsUrl)).willReturn(aResponse().withStatus(OK).withBody(json.toString()))
          )

          sut().iabds(nino, taxYear.next).futureValue mustBe List()
        }

        "looking for cy+2 year" in {

          server.stubFor(
            get(urlEqualTo(npsUrl)).willReturn(aResponse().withStatus(OK).withBody(json.toString()))
          )

          sut().iabds(nino, taxYear.next.next).futureValue mustBe List()
        }
      }

      "return an error" when {
        "a 400 occurs" in {

          server.stubFor(get(urlEqualTo(npsUrl)).willReturn(aResponse().withStatus(BAD_REQUEST)))

          sut().iabds(nino, taxYear).failed.futureValue mustBe a[BadRequestException]
        }

        "a 404 occurs" in {

          server.stubFor(get(urlEqualTo(npsUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))

          sut().iabds(nino, taxYear).failed.futureValue mustBe a[NotFoundException]
        }

        List(
          IM_A_TEAPOT,
          INTERNAL_SERVER_ERROR,
          SERVICE_UNAVAILABLE
        ).foreach { httpResponse =>
          s"a $httpResponse occurs" in {

            server.stubFor(get(urlEqualTo(npsUrl)).willReturn(aResponse().withStatus(httpResponse)))

            sut().iabds(nino, taxYear).failed.futureValue mustBe a[HttpException]
          }
        }
      }

    }
  }

  "updateTaxCodeIncome" when {
    "update nps with the new tax code income" in {

      val url: String = {
        val path = new URL(iabdUrls.npsIabdEmploymentUrl(nino, taxYear, NewEstimatedPay.code))
        s"${path.getPath}"
      }

      server.stubFor(post(urlEqualTo(url)).willReturn(ok(jsonResponse.toString)))

      Await.result(
        sut().updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345),
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

    "return a failure status if the update fails" in {

      val url: String = {
        val path = new URL(iabdUrls.npsIabdEmploymentUrl(nino, taxYear, NewEstimatedPay.code))
        s"${path.getPath}"
      }

      server.stubFor(post(urlEqualTo(url)).willReturn(aResponse.withStatus(400)))

      Await.result(
        sut().updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345),
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
      s" return a failure status for $httpStatus  response" in {

        val url: String = {
          val path = new URL(iabdUrls.npsIabdEmploymentUrl(nino, taxYear, NewEstimatedPay.code))
          s"${path.getPath}"
        }

        server.stubFor(post(urlEqualTo(url)).willReturn(aResponse.withStatus(httpStatus)))

        Await.result(
          sut().updateTaxCodeAmount(nino, taxYear, 1, 1, NewEstimatedPay.code, 12345),
          5 seconds
        ) mustBe HodUpdateFailure
      }
    }
  }

  "getIabdsForType" must {
    "get IABD's from DES api" when {
      "supplied with a valid nino, year and IABD type" in {

        val iabdList = List(NpsIabdRoot(nino = nino.nino, `type` = iabdType))
        val jsonData = Json.toJson(iabdList).toString()

        server.stubFor(
          get(urlEqualTo(iabdsForTypeUrl)).willReturn(aResponse().withStatus(OK).withBody(jsonData))
        )

        sut().getIabdsForType(nino, taxYear.year, iabdType).futureValue mustBe iabdList

        server.verify(
          getRequestedFor(urlEqualTo(iabdsForTypeUrl))
            .withHeader("Environment", equalTo("local"))
            .withHeader("Authorization", equalTo("Bearer desAuthorization"))
            .withHeader("Content-Type", equalTo(TaiConstants.contentType))
            .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
            .withHeader(HeaderNames.xRequestId, equalTo(requestId))
            .withHeader(
              "CorrelationId",
              matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"))
        )
      }
    }

    "throw an exception from DES API" when {
      "supplied with a valid nino, year and IABD type but an IABD cannot be found" in {

        val exMessage = "Could not find IABD"

        server.stubFor(
          get(urlEqualTo(iabdsForTypeUrl)).willReturn(aResponse().withStatus(NOT_FOUND).withBody(exMessage))
        )

        assertConnectorException[NotFoundException](
          sut().getIabdsForType(nino, taxYear.year, iabdType),
          NOT_FOUND,
          exMessage
        )
      }

      "DES API returns 400" in {

        val exMessage = "Invalid query"

        server.stubFor(
          get(urlEqualTo(iabdsForTypeUrl)).willReturn(aResponse().withStatus(BAD_REQUEST).withBody(exMessage))
        )

        assertConnectorException[BadRequestException](
          sut().getIabdsForType(nino, taxYear.year, iabdType),
          BAD_REQUEST,
          exMessage
        )
      }

      "DES API returns 4xx" in {

        val exMessage = "Record locked"

        server.stubFor(
          get(urlEqualTo(iabdsForTypeUrl)).willReturn(aResponse().withStatus(LOCKED).withBody(exMessage))
        )

        assertConnectorException[HttpException](
          sut().getIabdsForType(nino, taxYear.year, iabdType),
          LOCKED,
          exMessage
        )
      }

      "DES API returns 500" in {

        val exMessage = "An error occurred"

        server.stubFor(
          get(urlEqualTo(iabdsForTypeUrl)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR).withBody(exMessage))
        )

        assertConnectorException[InternalServerException](
          sut().getIabdsForType(nino, taxYear.year, iabdType),
          INTERNAL_SERVER_ERROR,
          exMessage
        )
      }

      "DES API returns 5xx" in {

        val exMessage = "Service unavailable"

        server.stubFor(
          get(urlEqualTo(iabdsForTypeUrl)).willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE).withBody(exMessage))
        )

        assertConnectorException[HttpException](
          sut().getIabdsForType(nino, taxYear.year, iabdType),
          SERVICE_UNAVAILABLE,
          exMessage
        )
      }
    }
  }

  "updateExpensesData" must {

    "return a status of 200 OK" when {

      "updating expenses data in DES using a valid update amount" in {

        val json = Json.toJson(updateAmount)

        server.stubFor(
          post(urlEqualTo(updateExpensesUrl)).willReturn(aResponse().withStatus(OK).withBody(json.toString()))
        )

        val response =
          sut().updateExpensesData(
            nino = nino,
            year = taxYear.year,
            iabdType = iabdType,
            version = 1,
            expensesData = List(UpdateIabdEmployeeExpense(100, None)),
            apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
          ).futureValue

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
              matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"))
        )
      }
    }

    "return a 2xx status" when {

      "the connector returns NO_CONTENT" in {

        server.stubFor(
          post(urlEqualTo(updateExpensesUrl)).willReturn(aResponse().withStatus(NO_CONTENT))
        )

        val response =
          sut().updateExpensesData(
            nino = nino,
            year = taxYear.year,
            iabdType = iabdType,
            version = 1,
            expensesData = List(UpdateIabdEmployeeExpense(100, None)),
            apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
          ).futureValue

        response.status mustBe NO_CONTENT
      }

      "the connector returns ACCEPTED" in {

        server.stubFor(
          post(urlEqualTo(updateExpensesUrl)).willReturn(aResponse().withStatus(ACCEPTED))
        )

        val response =
          sut().updateExpensesData(
            nino = nino,
            year = taxYear.year,
            iabdType = iabdType,
            version = 1,
            expensesData = List(UpdateIabdEmployeeExpense(100, None)),
            apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
          ).futureValue

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
            expensesData = List(UpdateIabdEmployeeExpense(100, None)),
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
            expensesData = List(UpdateIabdEmployeeExpense(100, None)),
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
            expensesData = List(UpdateIabdEmployeeExpense(100, None)),
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
            expensesData = List(UpdateIabdEmployeeExpense(100, None)),
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
            expensesData = List(UpdateIabdEmployeeExpense(100, None)),
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
            expensesData = List(UpdateIabdEmployeeExpense(100, None)),
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
            expensesData = List(UpdateIabdEmployeeExpense(100, None)),
            apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
          ),
          INTERNAL_SERVER_ERROR,
          exMessage
        )
      }
    }
  }

}

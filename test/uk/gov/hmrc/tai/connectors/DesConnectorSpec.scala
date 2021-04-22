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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, post, urlEqualTo}
import org.joda.time.DateTime
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json, Writes}
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.nps.{NpsIabdRoot, NpsTaxAccount}
import uk.gov.hmrc.tai.model.{IabdUpdateAmount, IabdUpdateAmountFormats, UpdateIabdEmployeeExpense}
import uk.gov.hmrc.tai.util.TaiConstants

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class DesConnectorSpec extends ConnectorBaseSpec with ScalaFutures {

  lazy val sut: DesConnector = inject[DesConnector]
  implicit lazy val iabdWrites: Writes[IabdUpdateAmount] =
    inject[IabdUpdateAmountFormats].iabdUpdateAmountWrites

  val taxYear: Int = DateTime.now().getYear
  val iabdType: Int = 27

  val baseUrl: String = s"/pay-as-you-earn/individuals/${nino.nino}"
  val iabdsUrl: String = s"$baseUrl/iabds/tax-year/$taxYear"
  val iabdsForTypeUrl: String = s"$iabdsUrl?type=$iabdType"
  val calcTaxAccUrl: String = s"$baseUrl/tax-account/tax-year/$taxYear?calculation=true"
  val updateEmploymentUrl: String = s"$baseUrl/iabds/$taxYear/employment/$iabdType"
  val updateExpensesUrl: String = s"$baseUrl/iabds/$taxYear/$iabdType"

  "DesConnector" should {

    "create a valid des url with an additional path" when {
      "a valid nino and additional path are supplied" in {

        val result = sut.desPathUrl(nino, "test")

        result mustBe s"${server.baseUrl()}/pay-as-you-earn/individuals/$nino/test"
      }
    }

    "create a header carrier with extra headers populated correctly" when {
      "the required extra header information is provided" in {

        val etag: String = "2"
        val originatorId: String = "testOriginatorId"

        val result = sut.headerForUpdate(etag.toInt, originatorId)

        result.extraHeaders mustBe Seq(
          "Environment"   -> "local",
          "Authorization" -> "Bearer Local",
          "Content-Type"  -> TaiConstants.contentType,
          "Originator-Id" -> originatorId,
          "ETag"          -> etag
        )
      }
    }

    "get IABD's from DES api" when {
      "supplied with a valid nino, year and IABD type" in {

        val iabdList = List(NpsIabdRoot(nino = nino.nino, `type` = iabdType))
        val jsonData = Json.toJson(iabdList).toString()

        server.stubFor(
          get(urlEqualTo(iabdsForTypeUrl)).willReturn(aResponse().withStatus(OK).withBody(jsonData))
        )

        Await.result(sut.getIabdsForTypeFromDes(nino, taxYear, iabdType), 5 seconds) mustBe iabdList
      }
    }

    "throw an exception from DES API" when {
      "supplied with a valid nino, year and IABD type but an IABD cannot be found" in {

        val exMessage = "Could not find IABD"

        server.stubFor(
          get(urlEqualTo(iabdsForTypeUrl)).willReturn(aResponse().withStatus(NOT_FOUND).withBody(exMessage))
        )

        assertConnectorException[NotFoundException](
          sut.getIabdsForTypeFromDes(nino, taxYear, iabdType),
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
          sut.getIabdsForTypeFromDes(nino, taxYear, iabdType),
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
          sut.getIabdsForTypeFromDes(nino, taxYear, iabdType),
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
          sut.getIabdsForTypeFromDes(nino, taxYear, iabdType),
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
          sut.getIabdsForTypeFromDes(nino, taxYear, iabdType),
          SERVICE_UNAVAILABLE,
          exMessage
        )
      }
    }

    "get IABDs from DES api" when {
      "supplied with a valid nino and year" in {
        val iabdList =
          List(NpsIabdRoot(nino = nino.nino, `type` = iabdType), NpsIabdRoot(nino = nino.nino, `type` = iabdType))
        val jsonData = Json.toJson(iabdList).toString()

        server.stubFor(
          get(urlEqualTo(iabdsUrl)).willReturn(aResponse().withStatus(OK).withBody(jsonData))
        )

        Await.result(sut.getIabdsFromDes(nino, taxYear), 5 seconds) mustBe iabdList
      }
    }

    "throw an exception" when {
      "a 404 is returned" in {

        val exMessage = "Not found"

        server.stubFor(
          get(urlEqualTo(iabdsUrl)).willReturn(aResponse().withStatus(NOT_FOUND).withBody(exMessage))
        )

        assertConnectorException[NotFoundException](
          sut.getIabdsFromDes(nino, taxYear),
          NOT_FOUND,
          exMessage
        )
      }

      "a 400 is returned" in {

        val exMessage = "Invalid query"

        server.stubFor(
          get(urlEqualTo(iabdsUrl)).willReturn(aResponse().withStatus(BAD_REQUEST).withBody(exMessage))
        )

        assertConnectorException[BadRequestException](
          sut.getIabdsFromDes(nino, taxYear),
          BAD_REQUEST,
          exMessage
        )
      }

      "a 4xx is returned" in {

        val exMessage = "UNACCEPTABLE"

        server.stubFor(
          get(urlEqualTo(iabdsUrl)).willReturn(aResponse().withStatus(NOT_ACCEPTABLE).withBody(exMessage))
        )

        assertConnectorException[HttpException](
          sut.getIabdsFromDes(nino, taxYear),
          NOT_ACCEPTABLE,
          exMessage
        )
      }

      "a 500 is returned" in {

        val exMessage = "An error occurred"

        server.stubFor(
          get(urlEqualTo(iabdsUrl)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR).withBody(exMessage))
        )

        assertConnectorException[InternalServerException](
          sut.getIabdsFromDes(nino, taxYear),
          INTERNAL_SERVER_ERROR,
          exMessage
        )
      }

      "a 5xx is returned" in {

        val exMessage = "Service unavailable"

        server.stubFor(
          get(urlEqualTo(iabdsUrl)).willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE).withBody(exMessage))
        )

        assertConnectorException[HttpException](
          sut.getIabdsFromDes(nino, taxYear),
          SERVICE_UNAVAILABLE,
          exMessage
        )
      }
    }

    "get TaxAccount from DES api" when {
      "supplied with a valid nino and year" in {

        val taxAccount = NpsTaxAccount(Some(nino.nino), Some(taxYear))
        val jsonData = Json.toJson(taxAccount)
        val body = jsonData.toString()
        val expectedResult: (NpsTaxAccount, Int, JsValue) = (taxAccount, -1, jsonData)

        server.stubFor(
          get(urlEqualTo(calcTaxAccUrl)).willReturn(aResponse().withStatus(OK).withBody(body))
        )

        Await.result(sut.getCalculatedTaxAccountFromDes(nino, taxYear), 5 seconds) mustBe expectedResult
      }
    }

    "thrown an exception" when {
      "supplied with a valid nino and year but the taxAccount cannot be found" in {

        val exMessage = "Not found"

        server.stubFor(
          get(urlEqualTo(calcTaxAccUrl)).willReturn(aResponse().withStatus(NOT_FOUND).withBody(exMessage))
        )

        assertConnectorException[NotFoundException](
          sut.getCalculatedTaxAccountFromDes(nino, taxYear),
          NOT_FOUND,
          exMessage
        )
      }

      "the API returns 400" in {

        val exMessage = "Invalid query"

        server.stubFor(
          get(urlEqualTo(calcTaxAccUrl)).willReturn(aResponse().withStatus(BAD_REQUEST).withBody(exMessage))
        )

        assertConnectorException[BadRequestException](
          sut.getCalculatedTaxAccountFromDes(nino, taxYear),
          BAD_REQUEST,
          exMessage
        )
      }

      "the API returns 4xx" in {

        val exMessage = "There was a conflict"

        server.stubFor(
          get(urlEqualTo(calcTaxAccUrl)).willReturn(aResponse().withStatus(CONFLICT).withBody(exMessage))
        )

        assertConnectorException[HttpException](
          sut.getCalculatedTaxAccountFromDes(nino, taxYear),
          CONFLICT,
          exMessage
        )
      }

      "the API returns 500" in {

        val exMessage = "An error occurred"

        server.stubFor(
          get(urlEqualTo(calcTaxAccUrl)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR).withBody(exMessage))
        )

        assertConnectorException[InternalServerException](
          sut.getCalculatedTaxAccountFromDes(nino, taxYear),
          INTERNAL_SERVER_ERROR,
          exMessage
        )
      }

      "the API returns 5xx" in {

        val exMessage = "There was a problem"

        server.stubFor(
          get(urlEqualTo(calcTaxAccUrl)).willReturn(aResponse().withStatus(GATEWAY_TIMEOUT).withBody(exMessage))
        )

        assertConnectorException[HttpException](
          sut.getCalculatedTaxAccountFromDes(nino, taxYear),
          GATEWAY_TIMEOUT,
          exMessage
        )
      }
    }

    "get TaxAccount Json from DES api" when {
      "supplied with a valid nino and year" in {
        val jsonData = Json.toJson(NpsTaxAccount(Some(nino.nino), Some(taxYear)))

        server.stubFor(
          get(urlEqualTo(calcTaxAccUrl)).willReturn(aResponse().withStatus(OK).withBody(jsonData.toString()))
        )

        val response = Await.result(sut.getCalculatedTaxAccountRawResponseFromDes(nino, taxYear), 5 seconds)

        response.status mustBe OK
        response.json mustBe jsonData
      }
    }

    "return the correct HttpResponse" when {

      "the API returns 400" in {

        val body = "Invalid query"

        server.stubFor(
          get(urlEqualTo(calcTaxAccUrl)).willReturn(aResponse().withStatus(BAD_REQUEST).withBody(body))
        )

        val response = Await.result(sut.getCalculatedTaxAccountRawResponseFromDes(nino, taxYear), 5 seconds)

        response.status mustBe BAD_REQUEST
        response.body mustBe body
      }

      "the API returns 404" in {

        val body = "Account not found"

        server.stubFor(
          get(urlEqualTo(calcTaxAccUrl)).willReturn(aResponse().withStatus(NOT_FOUND).withBody(body))
        )

        val response = Await.result(sut.getCalculatedTaxAccountRawResponseFromDes(nino, taxYear), 5 seconds)

        response.status mustBe NOT_FOUND
        response.body mustBe body
      }

      "the API returns 4xx" in {

        val body = "Access denied"

        server.stubFor(
          get(urlEqualTo(calcTaxAccUrl)).willReturn(aResponse().withStatus(FORBIDDEN).withBody(body))
        )

        val response = Await.result(sut.getCalculatedTaxAccountRawResponseFromDes(nino, taxYear), 5 seconds)

        response.status mustBe FORBIDDEN
        response.body mustBe body
      }

      "the API returns 500" in {

        val body = "An error occurred"

        server.stubFor(
          get(urlEqualTo(calcTaxAccUrl)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR).withBody(body))
        )

        val response = Await.result(sut.getCalculatedTaxAccountRawResponseFromDes(nino, taxYear), 5 seconds)

        response.status mustBe INTERNAL_SERVER_ERROR
        response.body mustBe body
      }

      "the API returns 5xx" in {

        val body = "The service is unavailable"

        server.stubFor(
          get(urlEqualTo(calcTaxAccUrl)).willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE).withBody(body))
        )

        val response = Await.result(sut.getCalculatedTaxAccountRawResponseFromDes(nino, taxYear), 5 seconds)

        response.status mustBe SERVICE_UNAVAILABLE
        response.body mustBe body
      }
    }

    val updateAmount = List(IabdUpdateAmount(1, 20000))

    "updateEmploymentDataToDes" must {

      val updateAmount = List(IabdUpdateAmount(1, 20000))

      "return a status of 200 OK" when {

        "updating employment data in DES using an empty update amount." in {

          val response = Await.result(sut.updateEmploymentDataToDes(nino, taxYear, iabdType, 1, Nil), 5 seconds)

          response.status mustBe OK
        }

        "updating employment data in DES using a valid update amount" in {

          val json = Json.toJson(updateAmount)

          server.stubFor(
            post(urlEqualTo(updateEmploymentUrl)).willReturn(aResponse().withStatus(OK).withBody(json.toString()))
          )

          val response =
            Await.result(sut.updateEmploymentDataToDes(nino, taxYear, iabdType, 1, updateAmount), 5 seconds)

          response.status mustBe OK
          response.json mustBe json
        }
      }

      "return a 2xx status" when {

        "the connector returns NO_CONTENT" in {

          server.stubFor(
            post(urlEqualTo(updateEmploymentUrl)).willReturn(aResponse().withStatus(NO_CONTENT))
          )

          val response =
            Await.result(sut.updateEmploymentDataToDes(nino, taxYear, iabdType, 1, updateAmount), 5 seconds)

          response.status mustBe NO_CONTENT
        }

        "the connector returns ACCEPTED" in {

          server.stubFor(
            post(urlEqualTo(updateEmploymentUrl)).willReturn(aResponse().withStatus(ACCEPTED))
          )

          val response =
            Await.result(sut.updateEmploymentDataToDes(nino, taxYear, iabdType, 1, updateAmount), 5 seconds)

          response.status mustBe ACCEPTED
        }
      }

      "throw a HttpException" when {

        "a 4xx response is returned" in {

          val exMessage = "Bad request"

          server.stubFor(
            post(urlEqualTo(updateEmploymentUrl)).willReturn(aResponse().withStatus(BAD_REQUEST).withBody(exMessage))
          )

          assertConnectorException[HttpException](
            sut.updateEmploymentDataToDes(nino, taxYear, iabdType, 1, updateAmount),
            BAD_REQUEST,
            exMessage
          )
        }

        "a 5xx response is returned" in {

          val exMessage = "An error occurred"

          server.stubFor(
            post(urlEqualTo(updateEmploymentUrl))
              .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR).withBody(exMessage))
          )

          assertConnectorException[HttpException](
            sut.updateEmploymentDataToDes(nino, taxYear, iabdType, 1, updateAmount),
            INTERNAL_SERVER_ERROR,
            exMessage
          )
        }
      }
    }

    "returns the sessionId from a HeaderCarrier" when {
      "HeaderCarrier has an assigned sessionId" in {

        val testSessionId = "testSessionId"
        val testHeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(testSessionId)))

        val result = sut.sessionOrUUID(testHeaderCarrier)

        result mustBe testSessionId
      }
    }

    "returns a randomly generated sessionId" when {
      "HeaderCarrier has None assigned as a sessionId" in {
        sut.sessionOrUUID(HeaderCarrier(sessionId = None)).length must be > 0
      }
    }

    "updateExpensesDataToDes" should {

      "return a status of 200 OK" when {

        "updating expenses data in DES using a valid update amount" in {

          val json = Json.toJson(updateAmount)

          server.stubFor(
            post(urlEqualTo(updateExpensesUrl)).willReturn(aResponse().withStatus(OK).withBody(json.toString()))
          )

          val response = Await.result(
            sut.updateExpensesDataToDes(
              nino = nino,
              year = taxYear,
              iabdType = iabdType,
              version = 1,
              expensesData = List(UpdateIabdEmployeeExpense(100, None)),
              apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
            ),
            5 seconds
          )

          response.status mustBe OK
          response.json mustBe json
        }
      }

      "return a 2xx status" when {

        "the connector returns NO_CONTENT" in {

          server.stubFor(
            post(urlEqualTo(updateExpensesUrl)).willReturn(aResponse().withStatus(NO_CONTENT))
          )

          val response = Await.result(
            sut.updateExpensesDataToDes(
              nino = nino,
              year = taxYear,
              iabdType = iabdType,
              version = 1,
              expensesData = List(UpdateIabdEmployeeExpense(100, None)),
              apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
            ),
            5 seconds
          )

          response.status mustBe NO_CONTENT
        }

        "the connector returns ACCEPTED" in {

          server.stubFor(
            post(urlEqualTo(updateExpensesUrl)).willReturn(aResponse().withStatus(ACCEPTED))
          )

          val response = Await.result(
            sut.updateExpensesDataToDes(
              nino = nino,
              year = taxYear,
              iabdType = iabdType,
              version = 1,
              expensesData = List(UpdateIabdEmployeeExpense(100, None)),
              apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
            ),
            5 seconds
          )

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
            sut.updateExpensesDataToDes(
              nino = nino,
              year = taxYear,
              iabdType = iabdType,
              version = 1,
              expensesData = List(UpdateIabdEmployeeExpense(100, None)),
              apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
            ),
            BAD_REQUEST,
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
            sut.updateExpensesDataToDes(
              nino = nino,
              year = taxYear,
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
}

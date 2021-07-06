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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, equalTo, get, getRequestedFor, matching, post, postRequestedFor, putRequestedFor, urlEqualTo}
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.joda.time.LocalDate
import org.mockito.Mockito.when
import play.api.http.Status._
import play.api.libs.json.{JsArray, JsValue, Json, Writes}
import uk.gov.hmrc.http._
import uk.gov.hmrc.tai.model
import uk.gov.hmrc.tai.model.nps.{NpsDate, NpsEmployment, NpsIabdRoot, NpsTaxAccount}
import uk.gov.hmrc.tai.model.nps2.NpsFormatter
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.{GateKeeperRule, IabdUpdateAmount, IabdUpdateAmountFormats, nps2}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class NpsConnectorSpec extends ConnectorBaseSpec with NpsFormatter {

  def intGen: Int = Random.nextInt(50)

  val year: Int = TaxYear().year
  val etag: Int = intGen
  val iabdType: Int = intGen
  val empSeqNum: Int = intGen
  val npsBaseUrl: String = s"/nps-hod-service/services/nps/person/${nino.nino}"
  val employmentsUrl: String = s"$npsBaseUrl/employment/$year"
  val iabdsUrl: String = s"$npsBaseUrl/iabds/$year"
  val iabdsForTypeUrl: String = s"$iabdsUrl/$iabdType"
  val taxAccountUrl: String = s"$npsBaseUrl/tax-account/$year/calculation"
  val updateEmploymentUrl: String = s"$iabdsUrl/employment/$iabdType"
  lazy val sut: NpsConnector = inject[NpsConnector]

  def verifyOutgoingUpdateHeaders(requestPattern: RequestPatternBuilder): Unit =
    server.verify(
      requestPattern
        .withHeader("Gov-Uk-Originator-Id", equalTo(npsOriginatorId))
        .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
        .withHeader(HeaderNames.xRequestId, equalTo(requestId))
        .withHeader(
          "CorrelationId",
          matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")))

  val employment: NpsEmployment = NpsEmployment(
    intGen,
    NpsDate(LocalDate.now().minusYears(1)),
    None,
    intGen.toString,
    intGen.toString,
    Some("Big corp"),
    1,
    None,
    Some(intGen.toString),
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    Some(BigDecimal(intGen))
  )

  val employmentAsJson: JsValue = Json.toJson(employment)

  "NpsConnector" when {
    "npsPathUrl is called" must {
      "fetch the path url" when {
        "given a nino and path" in {
          val arg = "path"
          sut.npsPathUrl(nino, arg) contains s"$npsBaseUrl/$arg"
        }
      }
    }

    "getEmployments is called" must {
      "return employments with success" when {
        "given a nino and a year" in {

          val expectedResult: (List[NpsEmployment], List[nps2.NpsEmployment], Int, List[GateKeeperRule]) = (
            List(employment),
            List(employmentAsJson.as[model.nps2.NpsEmployment]),
            etag,
            Nil
          )

          server.stubFor(
            get(urlEqualTo(employmentsUrl)).willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(s"[$employmentAsJson]")
                .withHeader("ETag", s"$etag")
            )
          )

          Await.result(sut.getEmployments(nino, year), 5.seconds) mustBe expectedResult

          verifyOutgoingUpdateHeaders(getRequestedFor(urlEqualTo(employmentsUrl)))
        }
      }

      "throw an exception" when {
        "connector returns 400" in {

          val exMessage = "Invalid query"

          server.stubFor(
            get(urlEqualTo(employmentsUrl)).willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[BadRequestException](
            sut.getEmployments(nino, year),
            BAD_REQUEST,
            exMessage
          )
        }

        "connector returns 404" in {

          val exMessage = "Could not find employment"

          server.stubFor(
            get(urlEqualTo(employmentsUrl)).willReturn(
              aResponse()
                .withStatus(NOT_FOUND)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[NotFoundException](
            sut.getEmployments(nino, year),
            NOT_FOUND,
            exMessage
          )
        }

        "connector returns 4xx" in {

          val exMessage = "Locked record"

          server.stubFor(
            get(urlEqualTo(employmentsUrl)).willReturn(
              aResponse()
                .withStatus(LOCKED)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[HttpException](
            sut.getEmployments(nino, year),
            LOCKED,
            exMessage
          )
        }

        "connector returns 500" in {

          val exMessage = "An error occurred"

          server.stubFor(
            get(urlEqualTo(employmentsUrl)).willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[InternalServerException](
            sut.getEmployments(nino, year),
            INTERNAL_SERVER_ERROR,
            exMessage
          )
        }

        "connector returns 5xx" in {

          val exMessage = "Could not reach gateway"

          server.stubFor(
            get(urlEqualTo(employmentsUrl)).willReturn(
              aResponse()
                .withStatus(BAD_GATEWAY)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[HttpException](
            sut.getEmployments(nino, year),
            BAD_GATEWAY,
            exMessage
          )
        }
      }
    }

    "getEmploymentDetails is called" must {
      "return employments json with success" when {
        "given a nino and a year" in {

          val employmentListJson = JsArray(Seq(employmentAsJson))

          server.stubFor(
            get(urlEqualTo(employmentsUrl)).willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(employmentListJson.toString())
                .withHeader("ETag", s"$etag"))
          )

          Await.result(sut.getEmploymentDetails(nino, year), 5.seconds) mustBe employmentListJson

          verifyOutgoingUpdateHeaders(getRequestedFor(urlEqualTo(employmentsUrl)))
        }
      }

      "throw an exception" when {
        "connector returns 400" in {

          val exMessage = "Invalid query"

          server.stubFor(
            get(urlEqualTo(employmentsUrl)).willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[BadRequestException](
            sut.getEmploymentDetails(nino, year),
            BAD_REQUEST,
            exMessage
          )
        }

        "connector returns 404" in {

          val exMessage = "Could not find employment"

          server.stubFor(
            get(urlEqualTo(employmentsUrl)).willReturn(
              aResponse()
                .withStatus(NOT_FOUND)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[NotFoundException](
            sut.getEmploymentDetails(nino, year),
            NOT_FOUND,
            exMessage
          )
        }

        "connector returns 4xx" in {

          val exMessage = "Locked record"

          server.stubFor(
            get(urlEqualTo(employmentsUrl)).willReturn(
              aResponse()
                .withStatus(LOCKED)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[HttpException](
            sut.getEmploymentDetails(nino, year),
            LOCKED,
            exMessage
          )
        }

        "connector returns 500" in {

          val exMessage = "An error occurred"

          server.stubFor(
            get(urlEqualTo(employmentsUrl)).willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[InternalServerException](
            sut.getEmploymentDetails(nino, year),
            INTERNAL_SERVER_ERROR,
            exMessage
          )
        }

        "connector returns 5xx" in {

          val exMessage = "Could not reach gateway"

          server.stubFor(
            get(urlEqualTo(employmentsUrl)).willReturn(
              aResponse()
                .withStatus(BAD_GATEWAY)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[HttpException](
            sut.getEmploymentDetails(nino, year),
            BAD_GATEWAY,
            exMessage
          )
        }
      }
    }

    "getIabds is called" must {
      "return iabds" when {
        "given a valid nino, a year and a type" in {

          val expectedResponse = List(NpsIabdRoot(nino = nino.nino, `type` = iabdType))

          val body = Json.toJson(expectedResponse).toString()

          server.stubFor(
            get(urlEqualTo(iabdsUrl)).willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(body)
                .withHeader("ETag", s"$etag"))
          )

          Await.result(sut.getIabds(nino, year), 5.seconds) mustBe
            expectedResponse

          verifyOutgoingUpdateHeaders(getRequestedFor(urlEqualTo(iabdsUrl)))
        }
      }

      "throw an exception" when {
        "connector returns 400" in {

          val exMessage = "Invalid query"

          server.stubFor(
            get(urlEqualTo(iabdsUrl)).willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[BadRequestException](
            sut.getIabds(nino, year),
            BAD_REQUEST,
            exMessage
          )
        }

        "connector returns 404" in {

          val exMessage = "Could not find employment"

          server.stubFor(
            get(urlEqualTo(iabdsUrl)).willReturn(
              aResponse()
                .withStatus(NOT_FOUND)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[NotFoundException](
            sut.getIabds(nino, year),
            NOT_FOUND,
            exMessage
          )
        }

        "connector returns 4xx" in {

          val exMessage = "Locked record"

          server.stubFor(
            get(urlEqualTo(iabdsUrl)).willReturn(
              aResponse()
                .withStatus(LOCKED)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[HttpException](
            sut.getIabds(nino, year),
            LOCKED,
            exMessage
          )
        }

        "connector returns 500" in {

          val exMessage = "An error occurred"

          server.stubFor(
            get(urlEqualTo(iabdsUrl)).willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[InternalServerException](
            sut.getIabds(nino, year),
            INTERNAL_SERVER_ERROR,
            exMessage
          )
        }

        "connector returns 5xx" in {

          val exMessage = "Could not reach gateway"

          server.stubFor(
            get(urlEqualTo(iabdsUrl)).willReturn(
              aResponse()
                .withStatus(BAD_GATEWAY)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[HttpException](
            sut.getIabds(nino, year),
            BAD_GATEWAY,
            exMessage
          )
        }
      }
    }

    "getIabdsForType is called" must {
      "return iabds" when {
        "given a valid nino, a year and a type" in {

          val expectedResponse = List(NpsIabdRoot(nino = nino.nino, `type` = iabdType))

          val body = Json.toJson(expectedResponse).toString()

          server.stubFor(
            get(urlEqualTo(iabdsForTypeUrl)).willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(body)
                .withHeader("ETag", s"$etag"))
          )

          Await.result(sut.getIabdsForType(nino, year, iabdType), 5.seconds) mustBe
            expectedResponse

        }
      }

      "throw an exception" when {
        "connector returns 400" in {

          val exMessage = "Invalid query"

          server.stubFor(
            get(urlEqualTo(iabdsForTypeUrl)).willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[BadRequestException](
            sut.getIabdsForType(nino, year, iabdType),
            BAD_REQUEST,
            exMessage
          )
        }

        "connector returns 404" in {

          val exMessage = "Could not find employment"

          server.stubFor(
            get(urlEqualTo(iabdsForTypeUrl)).willReturn(
              aResponse()
                .withStatus(NOT_FOUND)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[NotFoundException](
            sut.getIabdsForType(nino, year, iabdType),
            NOT_FOUND,
            exMessage
          )
        }

        "connector returns 4xx" in {

          val exMessage = "Locked record"

          server.stubFor(
            get(urlEqualTo(iabdsForTypeUrl)).willReturn(
              aResponse()
                .withStatus(LOCKED)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[HttpException](
            sut.getIabdsForType(nino, year, iabdType),
            LOCKED,
            exMessage
          )
        }

        "connector returns 500" in {

          val exMessage = "An error occurred"

          server.stubFor(
            get(urlEqualTo(iabdsForTypeUrl)).willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[InternalServerException](
            sut.getIabdsForType(nino, year, iabdType),
            INTERNAL_SERVER_ERROR,
            exMessage
          )
        }

        "connector returns 5xx" in {

          val exMessage = "Could not reach gateway"

          server.stubFor(
            get(urlEqualTo(iabdsForTypeUrl)).willReturn(
              aResponse()
                .withStatus(BAD_GATEWAY)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[HttpException](
            sut.getIabdsForType(nino, year, iabdType),
            BAD_GATEWAY,
            exMessage
          )
        }
      }
    }

    "getCalculatedTaxAccount is called" must {
      "return tax account" when {
        "connector returns OK with correct body" in {

          val taxAccount = NpsTaxAccount(Some(nino.nino), Some(year))
          val taxAccountAsJson = Json.toJson(taxAccount)
          val expectedResult: (NpsTaxAccount, Int, JsValue) = (taxAccount, etag, taxAccountAsJson)

          server.stubFor(
            get(urlEqualTo(taxAccountUrl)).willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(taxAccountAsJson.toString())
                .withHeader("ETag", etag.toString))
          )

          Await.result(sut.getCalculatedTaxAccount(nino, year), 5.seconds) mustBe expectedResult

          verifyOutgoingUpdateHeaders(getRequestedFor(urlEqualTo(taxAccountUrl)))
        }
      }

      "throw an exception" when {
        "connector returns 400" in {

          val exMessage = "Invalid query"

          server.stubFor(
            get(urlEqualTo(taxAccountUrl)).willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[BadRequestException](
            sut.getCalculatedTaxAccount(nino, year),
            BAD_REQUEST,
            exMessage
          )
        }

        "connector returns 404" in {

          val exMessage = "Could not find employment"

          server.stubFor(
            get(urlEqualTo(taxAccountUrl)).willReturn(
              aResponse()
                .withStatus(NOT_FOUND)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[NotFoundException](
            sut.getCalculatedTaxAccount(nino, year),
            NOT_FOUND,
            exMessage
          )
        }

        "connector returns 4xx" in {

          val exMessage = "Locked record"

          server.stubFor(
            get(urlEqualTo(taxAccountUrl)).willReturn(
              aResponse()
                .withStatus(LOCKED)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[HttpException](
            sut.getCalculatedTaxAccount(nino, year),
            LOCKED,
            exMessage
          )
        }

        "connector returns 500" in {

          val exMessage = "An error occurred"

          server.stubFor(
            get(urlEqualTo(taxAccountUrl)).willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[InternalServerException](
            sut.getCalculatedTaxAccount(nino, year),
            INTERNAL_SERVER_ERROR,
            exMessage
          )
        }

        "connector returns 5xx" in {

          val exMessage = "Could not reach gateway"

          server.stubFor(
            get(urlEqualTo(taxAccountUrl)).willReturn(
              aResponse()
                .withStatus(BAD_GATEWAY)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          assertConnectorException[HttpException](
            sut.getCalculatedTaxAccount(nino, year),
            BAD_GATEWAY,
            exMessage
          )
        }
      }
    }

    "getCalculatedTaxAccountRawResponse is called" must {
      "return tax account" when {
        "connector returns OK with correct body" in {

          val taxAccountAsJson = Json.toJson(NpsTaxAccount(Some(nino.nino), Some(year)))

          server.stubFor(
            get(urlEqualTo(taxAccountUrl)).willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(taxAccountAsJson.toString())
                .withHeader("ETag", etag.toString))
          )

          val result = Await.result(sut.getCalculatedTaxAccountRawResponse(nino, year), 5.seconds)

          result.status mustBe OK
          result.json mustBe taxAccountAsJson

          verifyOutgoingUpdateHeaders(getRequestedFor(urlEqualTo(taxAccountUrl)))
        }
      }

      "handle a non-200 status code" when {
        "connector returns 4xx" in {

          val exMessage = "Invalid query"

          server.stubFor(
            get(urlEqualTo(taxAccountUrl)).willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          val res = Await.result(sut.getCalculatedTaxAccountRawResponse(nino, year), 5.seconds)

          res.status mustBe BAD_REQUEST
          res.body mustBe exMessage
        }

        "connector returns 400" in {

          val exMessage = "Bad Request"

          server.stubFor(
            get(urlEqualTo(taxAccountUrl)).willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          val res = Await.result(sut.getCalculatedTaxAccountRawResponse(nino, year), 5.seconds)

          res.status mustBe BAD_REQUEST
          res.body mustBe exMessage
        }

        "connector returns 404" in {

          val exMessage = "Not Found"

          server.stubFor(
            get(urlEqualTo(taxAccountUrl)).willReturn(
              aResponse()
                .withStatus(NOT_FOUND)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          val res = Await.result(sut.getCalculatedTaxAccountRawResponse(nino, year), 5.seconds)

          res.status mustBe NOT_FOUND
          res.body mustBe exMessage
        }

        "connector returns 418" in {

          val exMessage = "An error occurred"

          server.stubFor(
            get(urlEqualTo(taxAccountUrl)).willReturn(
              aResponse()
                .withStatus(IM_A_TEAPOT)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          val res = Await.result(sut.getCalculatedTaxAccountRawResponse(nino, year), 5.seconds)

          res.status mustBe IM_A_TEAPOT
          res.body mustBe exMessage
        }

        "connector returns 5xx" in {

          val exMessage = "An error occurred"

          server.stubFor(
            get(urlEqualTo(taxAccountUrl)).willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody(exMessage)
                .withHeader("ETag", s"$etag"))
          )

          val res = Await.result(sut.getCalculatedTaxAccountRawResponse(nino, year), 5.seconds)

          res.status mustBe INTERNAL_SERVER_ERROR
          res.body mustBe exMessage
        }

        List(
          GATEWAY_TIMEOUT,
          INTERNAL_SERVER_ERROR
        ).foreach { httpStatus =>
          s"connector returns $httpStatus" in {

            val exMessage = "An error occurred"

            server.stubFor(
              get(urlEqualTo(taxAccountUrl)).willReturn(
                aResponse()
                  .withStatus(httpStatus)
                  .withBody(exMessage)
                  .withHeader("ETag", s"$etag"))
            )

            val res = Await.result(sut.getCalculatedTaxAccountRawResponse(nino, year), 5.seconds)

            res.status mustBe httpStatus
            res.body mustBe exMessage
          }
        }
      }

      "updateEmploymentData is called" must {

        val update = List(IabdUpdateAmount(empSeqNum, intGen))

        "update employment data" when {
          "given a populated update amount" in {

            implicit lazy val writes: Writes[IabdUpdateAmount] =
              inject[IabdUpdateAmountFormats].iabdUpdateAmountWrites

            val json = Json.toJson(update)

            server.stubFor(
              post(urlEqualTo(updateEmploymentUrl)).willReturn(
                aResponse()
                  .withStatus(OK)
                  .withBody(json.toString())
              )
            )

            val result: HttpResponse =
              Await.result(sut.updateEmploymentData(nino, year, iabdType, etag, update), 5 seconds)

            result.status mustBe OK
            result.json mustBe json

            server.verify(
              postRequestedFor(urlEqualTo(updateEmploymentUrl))
                .withHeader("Gov-Uk-Originator-Id", equalTo(npsOriginatorId))
                .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
                .withHeader(HeaderNames.xRequestId, equalTo(requestId))
                .withHeader("ETag", equalTo(etag.toString))
                .withHeader(
                  "CorrelationId",
                  matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")))
          }

          "given an empty updates amount" in {

            server.stubFor(
              post(urlEqualTo(updateEmploymentUrl)).willReturn(
                aResponse()
                  .withStatus(OK)
              )
            )

            val result: HttpResponse =
              Await.result(sut.updateEmploymentData(nino, year, iabdType, etag, Nil), 5 seconds)

            result.status mustBe OK
          }

          "connector returns ACCEPTED (202)" in {

            server.stubFor(
              post(urlEqualTo(updateEmploymentUrl)).willReturn(
                aResponse()
                  .withStatus(ACCEPTED)
              )
            )

            val result: HttpResponse =
              Await.result(sut.updateEmploymentData(nino, year, iabdType, etag, update), 5 seconds)

            result.status mustBe ACCEPTED
          }

          "connector returns NO_CONTENT (204)" in {

            server.stubFor(
              post(urlEqualTo(updateEmploymentUrl)).willReturn(
                aResponse()
                  .withStatus(NO_CONTENT)
              )
            )

            val result: HttpResponse =
              Await.result(sut.updateEmploymentData(nino, year, iabdType, etag, update), 5 seconds)

            result.status mustBe NO_CONTENT
          }
        }

        "throw an exception" when {
          "the connector returns a 4xx code" in {

            val exMessage = "Invalid payload"

            server.stubFor(
              post(urlEqualTo(updateEmploymentUrl)).willReturn(
                aResponse()
                  .withStatus(BAD_REQUEST)
                  .withBody(exMessage)
              )
            )

            assertConnectorException[HttpException](
              sut.updateEmploymentData(nino, year, iabdType, etag, update),
              BAD_REQUEST,
              exMessage
            )
          }

          "the connector returns a 400 code" in {

            val exMessage = "Invalid payload"

            server.stubFor(
              post(urlEqualTo(updateEmploymentUrl)).willReturn(
                aResponse()
                  .withStatus(BAD_REQUEST)
                  .withBody(exMessage)
              )
            )

            assertConnectorException[HttpException](
              sut.updateEmploymentData(nino, year, iabdType, etag, update),
              BAD_REQUEST,
              exMessage
            )
          }

          "the connector returns a 404 code" in {

            val exMessage = "Not Found"

            server.stubFor(
              post(urlEqualTo(updateEmploymentUrl)).willReturn(
                aResponse()
                  .withStatus(NOT_FOUND)
                  .withBody(exMessage)
              )
            )

            assertConnectorException[HttpException](
              sut.updateEmploymentData(nino, year, iabdType, etag, update),
              NOT_FOUND,
              exMessage
            )
          }

          "the connector returns a 418 code" in {

            val exMessage = "An error occurred"

            server.stubFor(
              post(urlEqualTo(updateEmploymentUrl)).willReturn(
                aResponse()
                  .withStatus(IM_A_TEAPOT)
                  .withBody(exMessage)
              )
            )

            assertConnectorException[HttpException](
              sut.updateEmploymentData(nino, year, iabdType, etag, update),
              IM_A_TEAPOT,
              exMessage
            )
          }

          "the connector returns a 5xx code" in {

            val exMessage = "An error occurred"

            server.stubFor(
              post(urlEqualTo(updateEmploymentUrl)).willReturn(
                aResponse()
                  .withStatus(INTERNAL_SERVER_ERROR)
                  .withBody(exMessage)
              )
            )

            assertConnectorException[HttpException](
              sut.updateEmploymentData(nino, year, iabdType, etag, update),
              INTERNAL_SERVER_ERROR,
              exMessage
            )
          }

          List(
            GATEWAY_TIMEOUT,
            INTERNAL_SERVER_ERROR
          ).foreach { httpStatus =>
            s"connector returns $httpStatus" in {

              val exMessage = "An error occurred"

              server.stubFor(
                post(urlEqualTo(updateEmploymentUrl)).willReturn(
                  aResponse()
                    .withStatus(httpStatus)
                    .withBody(exMessage)
                )
              )

              assertConnectorException[HttpException](
                sut.updateEmploymentData(nino, year, iabdType, etag, update),
                httpStatus,
                exMessage
              )
            }
          }
        }
      }
    }
  }
}

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
import play.api.http.Status._
import play.api.libs.json.{JsArray, JsValue, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.tai.connectors.deprecated.NpsConnector
import uk.gov.hmrc.tai.model.nps.{NpsDate, NpsEmployment}
import uk.gov.hmrc.tai.model.nps2.NpsFormatter
import uk.gov.hmrc.tai.model.tai.TaxYear

import java.time.LocalDate
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

          sut.getEmploymentDetails(nino, year).futureValue mustBe employmentListJson

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
  }
}

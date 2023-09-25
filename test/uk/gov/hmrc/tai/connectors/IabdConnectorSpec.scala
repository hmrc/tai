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
import play.api.libs.json.{JsNull, JsObject, Json}
import uk.gov.hmrc.http.{BadRequestException, HeaderNames, HttpException, NotFoundException}
import uk.gov.hmrc.tai.config.NpsConfig
import uk.gov.hmrc.tai.connectors.cache.{DefaultIabdConnector, IabdConnector}
import uk.gov.hmrc.tai.model.IabdUpdateAmountFormats
import uk.gov.hmrc.tai.model.domain.formatters.IabdDetails
import uk.gov.hmrc.tai.model.domain.response.{HodUpdateFailure, HodUpdateSuccess}
import uk.gov.hmrc.tai.model.nps2.IabdType.NewEstimatedPay
import uk.gov.hmrc.tai.model.tai.TaxYear

import java.net.URL
import java.time.LocalDate
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class IabdConnectorSpec extends ConnectorBaseSpec {


  lazy val iabdUrls: IabdUrls = inject[IabdUrls]

  def sut(): IabdConnector = new DefaultIabdConnector(inject[HttpHandler], inject[NpsConfig],
    iabdUrls, inject[IabdUpdateAmountFormats])

  val taxYear: TaxYear = TaxYear()

  val npsUrl: String = s"/nps-hod-service/services/nps/person/${nino.nino}/iabds/${taxYear.year}"

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
}

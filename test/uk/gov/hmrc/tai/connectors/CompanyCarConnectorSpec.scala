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

import com.fasterxml.jackson.core.JsonParseException
import com.github.tomakehurst.wiremock.client.WireMock._
import org.joda.time.LocalDate
import play.api.http.Status._
import play.api.libs.json.{JsResultException, Json}
import uk.gov.hmrc.http.{BadRequestException, HttpException, InternalServerException, NotFoundException}
import uk.gov.hmrc.tai.model.domain.benefits.{CompanyCar, CompanyCarBenefit, WithdrawCarAndFuel}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class CompanyCarConnectorSpec extends ConnectorBaseSpec {

  val empSeqNumber: Int = 1
  val carSeqNumber: Int = 24

  private val taxYear = TaxYear(2020)

  lazy val baseUrl = s"/paye/${nino.nino}"
  lazy val carBenefitUrl = s"$baseUrl/car-benefits/${taxYear.year}"
  lazy val ninoVersionUrl = s"$baseUrl/version"
  lazy val removeBenefitUrl = s"$baseUrl/benefits/${taxYear.year}/$empSeqNumber/car/$carSeqNumber/remove"

  lazy val sut: CompanyCarConnector = inject[CompanyCarConnector]

  "carBenefits" must {
    "return company car benefit details from the company car benefit service with no fuel benefit" in {

      val expectedResponse = Seq(
        CompanyCarBenefit(
          empSeqNumber,
          3333,
          Seq(
            CompanyCar(
              carSeqNumber,
              "company car",
              hasActiveFuelBenefit = false,
              Some(LocalDate.parse("2014-06-10")),
              None,
              None))))

      val body: String = Json
        .arr(Json.obj(
          "employmentSequenceNumber" -> empSeqNumber,
          "grossAmount"              -> 3333,
          "carDetails" -> Json.arr(Json.obj(
            "carSequenceNumber" -> carSeqNumber,
            "makeModel"         -> "company car",
            "dateMadeAvailable" -> "2014-06-10"))
        ))
        .toString()

      server.stubFor(
        get(urlEqualTo(carBenefitUrl)).willReturn(aResponse().withStatus(OK).withBody(body))
      )

      Await.result(sut.carBenefits(nino, taxYear), 5 seconds) mustBe expectedResponse
    }

    "return company car benefit details from the company car benefit service with a fuel benefit" in {
      val expectedResponse = Seq(
        CompanyCarBenefit(
          empSeqNumber,
          3333,
          Seq(
            CompanyCar(
              carSeqNumber,
              "company car",
              hasActiveFuelBenefit = true,
              Some(LocalDate.parse("2014-06-10")),
              Some(LocalDate.parse("2017-05-02")),
              None))
        ))

      val rawResponse: String =
        Json
          .arr(
            Json.obj(
              "employmentSequenceNumber" -> empSeqNumber,
              "grossAmount"              -> 3333,
              "carDetails" -> Json.arr(
                Json.obj(
                  "carSequenceNumber" -> carSeqNumber,
                  "makeModel"         -> "company car",
                  "dateMadeAvailable" -> "2014-06-10",
                  "fuelBenefit" ->
                    Json.obj(
                      "dateMadeAvailable" -> "2017-05-02",
                      "benefitAmount"     -> 500,
                      "actions"           -> Json.obj("foo" -> "bar")
                    )
                )
              )
            )
          )
          .toString()

      server.stubFor(
        get(urlEqualTo(carBenefitUrl)).willReturn(aResponse().withStatus(OK).withBody(rawResponse))
      )

      Await.result(sut.carBenefits(nino, taxYear), 5 seconds) mustBe expectedResponse
    }

    "return an empty sequence of car benefits" in {

      server.stubFor(
        get(urlEqualTo(carBenefitUrl)).willReturn(aResponse().withStatus(OK).withBody("[]"))
      )

      Await.result(sut.carBenefits(nino, taxYear), 5 seconds) mustBe Seq.empty[CompanyCarBenefit]
    }

    "throw" when {
      "json is invalid" in {

        val invalidResponse: String =
          Json
            .arr(
              Json.obj(
                "empSeqNum"   -> empSeqNumber,
                "grossAmount" -> 3333,
                "carDetails" -> Json.arr(
                  Json.obj(
                    "carSeqNum"         -> carSeqNumber,
                    "makeModel"         -> "company car",
                    "dateMadeAvailable" -> "2014-06-10",
                    "fuelBenefit" ->
                      Json.obj(
                        "dateMadeAvailable" -> "2017-05-02",
                        "benefitAmount"     -> 500,
                        "actions"           -> Json.obj("foo" -> "bar")
                      )
                  )
                )
              )
            )
            .toString()

        server.stubFor(
          get(urlEqualTo(carBenefitUrl)).willReturn(aResponse().withStatus(OK).withBody(invalidResponse))
        )

        a[JsResultException] mustBe thrownBy {
          Await.result(sut.carBenefits(nino, taxYear), 5 seconds)
        }
      }

      "400 is returned" in {

        val exMessage = "Invalid argument"

        server.stubFor(
          get(urlEqualTo(carBenefitUrl)).willReturn(aResponse().withStatus(BAD_REQUEST).withBody(exMessage))
        )

        assertConnectorException[BadRequestException](
          sut.carBenefits(nino, taxYear),
          BAD_REQUEST,
          exMessage
        )
      }

      "404 is returned" in {

        val exMessage = "Could not find car benefits"

        server.stubFor(
          get(urlEqualTo(carBenefitUrl)).willReturn(aResponse().withStatus(NOT_FOUND).withBody(exMessage))
        )

        assertConnectorException[NotFoundException](
          sut.carBenefits(nino, taxYear),
          NOT_FOUND,
          exMessage
        )
      }

      "4xx is returned" in {

        val exMessage = "Request has been locked"

        server.stubFor(
          get(urlEqualTo(carBenefitUrl)).willReturn(aResponse().withStatus(LOCKED).withBody(exMessage))
        )

        assertConnectorException[HttpException](
          sut.carBenefits(nino, taxYear),
          LOCKED,
          exMessage
        )
      }

      "500 is returned" in {

        val exMessage = "An error occurred"

        server.stubFor(
          get(urlEqualTo(carBenefitUrl)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR).withBody(exMessage))
        )

        assertConnectorException[InternalServerException](
          sut.carBenefits(nino, taxYear),
          INTERNAL_SERVER_ERROR,
          exMessage
        )
      }

      "5xx is returned" in {

        val exMessage = "An error occurred"

        server.stubFor(
          get(urlEqualTo(carBenefitUrl)).willReturn(aResponse().withStatus(BAD_GATEWAY).withBody(exMessage))
        )

        assertConnectorException[HttpException](
          sut.carBenefits(nino, taxYear),
          BAD_GATEWAY,
          exMessage
        )
      }
    }
  }

  "removeCarBenefit" must {

    val removeCarAndFuelModel = WithdrawCarAndFuel(1, new LocalDate(), None)

    "call remove Api and return id with success" in {

      val sampleResponse = Json
        .obj(
          "transaction" -> Json.obj("oid" -> "4958621783d14007b71d55934d5ccca9"),
          "taxCode"     -> "220T",
          "allowance"   -> 1674)
        .toString()

      server.stubFor(
        post(urlEqualTo(removeBenefitUrl)).willReturn(aResponse().withStatus(OK).withBody(sampleResponse))
      )

      val result = Await
        .result(sut.withdrawCarBenefit(nino, taxYear, empSeqNumber, carSeqNumber, removeCarAndFuelModel), 5 seconds)

      result mustBe "4958621783d14007b71d55934d5ccca9"
    }

    "throw" when {
      "invalid json is returned" in {

        val invalidResponse = Json
          .obj("transact" -> Json.obj("uid" -> "45ccca9"), "taxCode" -> "220T", "allowance" -> 1674)
          .toString()

        server.stubFor(
          post(urlEqualTo(removeBenefitUrl)).willReturn(aResponse().withStatus(OK).withBody(invalidResponse))
        )

        a[JsResultException] mustBe thrownBy {
          Await
            .result(sut.withdrawCarBenefit(nino, taxYear, empSeqNumber, carSeqNumber, removeCarAndFuelModel), 5 seconds)
        }
      }

      "400 is returned" in {

        val exMessage = "Invalid argument"

        server.stubFor(
          post(urlEqualTo(removeBenefitUrl)).willReturn(aResponse().withStatus(BAD_REQUEST).withBody(exMessage))
        )

        assertConnectorException[HttpException](
          sut.withdrawCarBenefit(nino, taxYear, empSeqNumber, carSeqNumber, removeCarAndFuelModel),
          BAD_REQUEST,
          exMessage
        )
      }

      "404 is returned" in {

        val exMessage = "Could not find car benefits"

        server.stubFor(
          post(urlEqualTo(removeBenefitUrl)).willReturn(aResponse().withStatus(NOT_FOUND).withBody(exMessage))
        )

        assertConnectorException[HttpException](
          sut.withdrawCarBenefit(nino, taxYear, empSeqNumber, carSeqNumber, removeCarAndFuelModel),
          NOT_FOUND,
          exMessage
        )
      }

      "4xx is returned" in {

        val exMessage = "Request has been locked"

        server.stubFor(
          post(urlEqualTo(removeBenefitUrl)).willReturn(aResponse().withStatus(FORBIDDEN).withBody(exMessage))
        )

        assertConnectorException[HttpException](
          sut.withdrawCarBenefit(nino, taxYear, empSeqNumber, carSeqNumber, removeCarAndFuelModel),
          FORBIDDEN,
          exMessage
        )
      }

      "500 is returned" in {

        val exMessage = "An error occurred"

        server.stubFor(
          post(urlEqualTo(removeBenefitUrl))
            .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR).withBody(exMessage))
        )

        assertConnectorException[HttpException](
          sut.withdrawCarBenefit(nino, taxYear, empSeqNumber, carSeqNumber, removeCarAndFuelModel),
          INTERNAL_SERVER_ERROR,
          exMessage
        )
      }

      "5xx is returned" in {

        val exMessage = "An error occurred"

        server.stubFor(
          post(urlEqualTo(removeBenefitUrl)).willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE).withBody(exMessage))
        )

        assertConnectorException[HttpException](
          sut.withdrawCarBenefit(nino, taxYear, empSeqNumber, carSeqNumber, removeCarAndFuelModel),
          SERVICE_UNAVAILABLE,
          exMessage
        )
      }
    }
  }

  "ninoVersion" must {
    "call paye to fetch the version" in {
      val expectedResponse = 4

      server.stubFor(
        get(urlEqualTo(ninoVersionUrl)).willReturn(aResponse().withStatus(OK).withBody(expectedResponse.toString))
      )

      val result = Await.result(sut.ninoVersion(nino), 5 seconds)

      result mustBe expectedResponse
    }

    "throw" when {
      "json is invalid" in {

        server.stubFor(
          get(urlEqualTo(ninoVersionUrl)).willReturn(aResponse().withStatus(OK).withBody("X"))
        )

        a[JsonParseException] mustBe thrownBy {
          Await.result(sut.ninoVersion(nino), 5 seconds)
        }
      }

      "400 is returned" in {

        val exMessage = "Invalid argument"

        server.stubFor(
          get(urlEqualTo(ninoVersionUrl)).willReturn(aResponse().withStatus(BAD_REQUEST).withBody(exMessage))
        )

        assertConnectorException[BadRequestException](
          sut.ninoVersion(nino),
          BAD_REQUEST,
          exMessage
        )
      }

      "404 is returned" in {

        val exMessage = "Could not find car benefits"

        server.stubFor(
          get(urlEqualTo(ninoVersionUrl)).willReturn(aResponse().withStatus(NOT_FOUND).withBody(exMessage))
        )

        assertConnectorException[NotFoundException](
          sut.ninoVersion(nino),
          NOT_FOUND,
          exMessage
        )
      }

      "4xx is returned" in {

        val exMessage = "Request has been locked"

        server.stubFor(
          get(urlEqualTo(ninoVersionUrl)).willReturn(aResponse().withStatus(LOCKED).withBody(exMessage))
        )

        assertConnectorException[HttpException](
          sut.ninoVersion(nino),
          LOCKED,
          exMessage
        )
      }

      "500 is returned" in {

        val exMessage = "An error occurred"

        server.stubFor(
          get(urlEqualTo(ninoVersionUrl)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR).withBody(exMessage))
        )

        assertConnectorException[InternalServerException](
          sut.ninoVersion(nino),
          INTERNAL_SERVER_ERROR,
          exMessage
        )
      }

      "5xx is returned" in {

        val exMessage = "An error occurred"

        server.stubFor(
          get(urlEqualTo(ninoVersionUrl)).willReturn(aResponse().withStatus(BAD_GATEWAY).withBody(exMessage))
        )

        assertConnectorException[HttpException](
          sut.ninoVersion(nino),
          BAD_GATEWAY,
          exMessage
        )
      }
    }
  }
}

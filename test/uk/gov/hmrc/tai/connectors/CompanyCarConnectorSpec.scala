/*
 * Copyright 2020 HM Revenue & Customs
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

import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.http.Status.OK
import play.api.libs.json.{JsNumber, JsValue, Json}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.tai.model.domain.benefits.{CompanyCar, CompanyCarBenefit, WithdrawCarAndFuel}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class CompanyCarConnectorSpec extends BaseSpec {

  "carBenefits" must {
    "return company car benefit details from the company car benefit service with no fuel benefit" in {
      val expectedResponse = Seq(
        CompanyCarBenefit(
          1,
          3333,
          Seq(CompanyCar(24, "company car", false, Some(LocalDate.parse("2014-06-10")), None, None))))

      val fakeResponse: JsValue = Json.arr(
        Json.obj(
          "employmentSequenceNumber" -> 1,
          "grossAmount"              -> 3333,
          "carDetails" -> Json.arr(
            Json.obj("carSequenceNumber" -> 24, "makeModel" -> "company car", "dateMadeAvailable" -> "2014-06-10"))
        ))

      val mockHttpHandler = mock[HttpHandler]
      when(mockHttpHandler.getFromApi(any(), any())(any()))
        .thenReturn(Future.successful(fakeResponse))

      val sut = createSUT(mockHttpHandler, mock[PayeUrls])

      Await.result(sut.carBenefits(nino, taxYear), 5 seconds) mustBe expectedResponse
    }

    "return company car benefit details from the company car benefit service with a fuel benefit" in {
      val expectedResponse = Seq(
        CompanyCarBenefit(
          1,
          3333,
          Seq(
            CompanyCar(
              24,
              "company car",
              true,
              Some(LocalDate.parse("2014-06-10")),
              Some(LocalDate.parse("2017-05-02")),
              None))))

      val rawResponse: JsValue =
        Json.arr(
          Json.obj(
            "employmentSequenceNumber" -> 1,
            "grossAmount"              -> 3333,
            "carDetails" -> Json.arr(
              Json.obj(
                "carSequenceNumber" -> 24,
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

      val mockHttpHandler = mock[HttpHandler]
      when(mockHttpHandler.getFromApi(any(), any())(any()))
        .thenReturn(Future.successful(rawResponse))

      val sut = createSUT(mockHttpHandler, mock[PayeUrls])
      Await.result(sut.carBenefits(nino, taxYear), 5 seconds) mustBe expectedResponse
    }
  }

  "removeCarBenefit" must {
    "call remove Api and return id with success" in {
      val removeCarAndFuelModel = WithdrawCarAndFuel(1, new LocalDate(), None)

      val sampleResponse = Json.obj(
        "transaction" -> Json.obj("oid" -> "4958621783d14007b71d55934d5ccca9"),
        "taxCode"     -> "220T",
        "allowance"   -> 1674)

      val mockHttpHandler = mock[HttpHandler]
      when(mockHttpHandler.postToApi(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(sampleResponse))))

      val sut = createSUT(mockHttpHandler, mock[PayeUrls])
      val result = Await.result(sut.withdrawCarBenefit(nino, taxYear, 1, 2, removeCarAndFuelModel), 5 seconds)

      result mustBe "4958621783d14007b71d55934d5ccca9"
      verify(mockHttpHandler, times(1))
        .postToApi(any(), any(), any())(any(), any())
    }
  }

  "ninoVersion" must {
    "call paye to fetch the version" in {
      val expectedResponse = 4
      val response: JsValue = JsNumber(4)

      val mockHttpHandler = mock[HttpHandler]
      when(mockHttpHandler.getFromApi(any(), any())(any()))
        .thenReturn(Future.successful(response))

      val sut = createSUT(mockHttpHandler, mock[PayeUrls])
      val result = Await.result(sut.ninoVersion(nino), 5 seconds)

      result mustBe expectedResponse
    }
  }

  private val taxYear = TaxYear(2017)

  private def createSUT(httpHandler: HttpHandler, urls: PayeUrls) =
    new CompanyCarConnector(httpHandler, urls)
}

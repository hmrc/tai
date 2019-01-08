/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.tai.controllers.taxCodeChange

import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Mockito.when
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsBoolean, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{status, _}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.tai.config.FeatureTogglesConfig
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model.{TaxCodeMismatch, api}
import uk.gov.hmrc.tai.model.api.TaxCodeChange
import uk.gov.hmrc.tai.service.TaxCodeChangeService
import uk.gov.hmrc.tai.util.TaxCodeHistoryConstants

import scala.concurrent.Future
import scala.util.Random

class TaxCodeChangeControllerSpec extends PlaySpec with MockitoSugar with MockAuthenticationPredicate with TaxCodeHistoryConstants{

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "hasTaxCodeChanged" should {

    "return true" when {
      "there has been a tax code change" in {

        val testNino = ninoGenerator

        when(mockConfig.taxCodeChangeEnabled).thenReturn(true)
        when(taxCodeService.hasTaxCodeChanged(Matchers.any())(Matchers.any())).thenReturn(Future.successful(true))

        val response: Future[Result] = controller.hasTaxCodeChanged(testNino)(FakeRequest())

        status(response) mustBe OK

        contentAsJson(response) mustEqual Json.toJson(true)
      }
    }

    "return false" when {
      "there has not been a tax code change" in {

        val testNino = ninoGenerator

        when(mockConfig.taxCodeChangeEnabled).thenReturn(true)
        when(taxCodeService.hasTaxCodeChanged(Matchers.any())(Matchers.any())).thenReturn(Future.successful(false))

        val response: Future[Result] = controller.hasTaxCodeChanged(testNino)(FakeRequest())

        status(response) mustBe OK

        contentAsJson(response) mustEqual Json.toJson(false)
      }
    }

    "return false" when {
      "taxCodeChanged endpoint is toggled off" in {

        val testNino = ninoGenerator

        when(mockConfig.taxCodeChangeEnabled).thenReturn(false)

        val response: Future[Result] = controller.hasTaxCodeChanged(testNino)(FakeRequest())

        status(response) mustBe OK

        contentAsJson(response) mustEqual Json.toJson(false)
      }
    }
  }

  "taxCodeChange" should {
    "return given nino's tax code history" in {

      val date = LocalDate.now()
      val testNino = ninoGenerator
      val currentRecord = api.TaxCodeRecordWithEndDate("b", Cumulative, date, date.minusDays(1), "Employer 1",
        Some("12345"), pensionIndicator = false, primary = true)
      val previousRecord = api.TaxCodeRecordWithEndDate("a", Cumulative, date, date.minusDays(1), "Employer 2",
        Some("67890"), pensionIndicator = false, primary = true)
      when(taxCodeService.taxCodeChange(Matchers.eq(testNino))(Matchers.any())).thenReturn(
        Future.successful(TaxCodeChange(Seq(currentRecord), Seq(previousRecord))))

      val expectedResponse = Json.obj(
        "data" -> Json.obj(
          "current" -> Json.arr(Json.obj("taxCode" -> "b",
                                  "basisOfOperation" -> Cumulative,
                                  "startDate" -> date.toString,
                                  "endDate" -> date.minusDays(1).toString,
                                  "employerName" -> "Employer 1",
                                  "payrollNumber" -> "12345",
                                  "pensionIndicator" -> false,
                                  "primary" -> true)),
          "previous" -> Json.arr(Json.obj("taxCode" -> "a",
                                  "basisOfOperation" -> Cumulative,
                                  "startDate" -> date.toString,
                                  "endDate" -> date.minusDays(1).toString,
                                  "employerName" -> "Employer 2",
                                  "payrollNumber" -> "67890",
                                  "pensionIndicator" -> false,
                                  "primary" -> true))),
        "links" -> Json.arr())


      val response = controller.taxCodeChange(testNino)(FakeRequest())

      contentAsJson(response) mustEqual expectedResponse
    }
  }

  "taxCodeMismatch" should {

    val nino = ninoGenerator

    "return true and list of tax code changes" when {

      "there has been a tax code change but there is a mismatch between confirmed and unconfirmed codes" in {

        when(taxCodeService.taxCodeMismatch(Matchers.any())(Matchers.any())).thenReturn(Future.successful(TaxCodeMismatch(true, Seq("1185L","BR"), Seq("1185L"))))

        val expectedResponse = Json.obj(
          "data" -> Json.obj(
            "mismatch" -> true,
            "unconfirmedTaxCodes" -> Json.arr("1185L","BR"),
            "confirmedTaxCodes" -> Json.arr("1185L")
          ),
          "links" -> Json.arr())

        val result = controller.taxCodeMismatch(nino)(FakeRequest())

        contentAsJson(result) mustEqual expectedResponse
      }
    }

    "return false and list of tax code changes" when {

      "there has been a tax code change but there is a mismatch between confirmed and unconfirmed codes" in {

        when(taxCodeService.taxCodeMismatch(Matchers.any())(Matchers.any())).thenReturn(
          Future.successful(TaxCodeMismatch(true, Seq("1185L","BR"), Seq("1185L","BR"))))

        val expectedResponse = Json.obj(
          "data" -> Json.obj(
            "mismatch" -> true,
            "unconfirmedTaxCodes" -> Json.arr("1185L","BR"),
            "confirmedTaxCodes" -> Json.arr("1185L","BR")
          ),
          "links" -> Json.arr())

        val result = controller.taxCodeMismatch(nino)(FakeRequest())

        contentAsJson(result) mustEqual expectedResponse
      }
    }

    "return a BadRequest 400" when {
      "a bad request exception has occurred" in {
        when(taxCodeService.taxCodeMismatch(Matchers.any())(Matchers.any())).thenReturn(Future.failed(new BadRequestException("Error")))

        val result = controller.taxCodeMismatch(nino)(FakeRequest())

        status(result) mustEqual 400
        contentAsString(result) mustEqual """{"reason":"Error"}"""
      }
    }

  }

  val mockConfig: FeatureTogglesConfig = mock[FeatureTogglesConfig]
  val taxCodeService: TaxCodeChangeService = mock[TaxCodeChangeService]

  private def controller = new TaxCodeChangeController(loggedInAuthenticationPredicate, taxCodeService, mockConfig)
  private def ninoGenerator = new Generator(new Random).nextNino
}

/*
 * Copyright 2018 HM Revenue & Customs
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
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{status, _}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.config.FeatureTogglesConfig
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model.api
import uk.gov.hmrc.tai.model.api.TaxCodeChange
import uk.gov.hmrc.tai.service.TaxCodeChangeService

import scala.concurrent.Future
import scala.util.Random

class TaxCodeChangeControllerSpec extends PlaySpec with MockitoSugar with MockAuthenticationPredicate {

  "hasTaxCodeChanged" should {

    "return true" when {
      "there has been a tax code change" in {

        val testNino = ninoGenerator

        when(mockConfig.taxCodeChangeEnabled).thenReturn(true)
        when(mockTaxCodeService.hasTaxCodeChanged(testNino)).thenReturn(Future.successful(true))

        val response: Future[Result] = controller.hasTaxCodeChanged(testNino)(FakeRequest())

        status(response) mustBe OK

        contentAsJson(response) mustEqual Json.toJson(true)
      }
    }

    "return false" when {
      "there has not been a tax code change" in {

        val testNino = ninoGenerator

        when(mockConfig.taxCodeChangeEnabled).thenReturn(true)
        when(mockTaxCodeService.hasTaxCodeChanged(testNino)).thenReturn(Future.successful(false))

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
      val currentRecord = api.TaxCodeChangeRecord("b", date, date.minusDays(1), "Employer 1")
      val previousRecord = api.TaxCodeChangeRecord("a", date, date.minusDays(1), "Employer 2")
      when(mockTaxCodeService.taxCodeChange(testNino)).thenReturn(Future.successful(TaxCodeChange(currentRecord, previousRecord)))

      val expectedResponse = Json.obj(
        "data" -> Json.obj(
          "current" -> Json.obj("taxCode" -> "b",
                                "startDate" -> date.toString,
                                "endDate" -> date.minusDays(1).toString,
                                "employerName" -> "Employer 1"),
          "previous" -> Json.obj("taxCode" -> "a",
                                 "startDate" -> date.toString,
                                 "endDate" -> date.minusDays(1).toString,
                                 "employerName" -> "Employer 2")),
        "links" -> Json.arr())


      val response = controller.taxCodeChange(testNino)(FakeRequest())

      contentAsJson(response) mustEqual expectedResponse
    }
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val mockConfig: FeatureTogglesConfig = mock[FeatureTogglesConfig]
  val mockTaxCodeService: TaxCodeChangeService = mock[TaxCodeChangeService]

  private def controller = new TaxCodeChangeController(loggedInAuthenticationPredicate, mockTaxCodeService, mockConfig)
  private def ninoGenerator = new Generator(new Random).nextNino
}

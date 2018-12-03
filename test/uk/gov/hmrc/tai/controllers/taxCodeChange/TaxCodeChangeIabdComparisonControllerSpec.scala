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

import org.mockito.Matchers
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{status, _}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model.TaxFreeAmountComparison
import uk.gov.hmrc.tai.model.domain.{CarBenefit, TaxComponentType}
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.service.TaxFreeAmountComparisonService
import org.mockito.Mockito.when

import scala.concurrent.Future
import scala.util.Random

class TaxCodeChangeIabdComparisonControllerSpec extends PlaySpec with MockAuthenticationPredicate with MockitoSugar {

  "taxCodeChangeIabdComparison" should {
    "respond with OK" when {
      "when given a valid Nino" in {
        val nino = ninoGenerator

        val codingComponent = CodingComponent(CarBenefit, Some(1), 1, "test", Some(1))
        val model = TaxFreeAmountComparison(Seq(codingComponent), Seq(codingComponent))

        when(taxFreeAmountComparisonService.taxFreeAmountComparison(Matchers.eq(nino))(Matchers.any()))
          .thenReturn(Future.successful(model))

        val expectedJson = Json.obj(
          "previous" -> Json.arr(
            Json.obj(
              "componentType" -> "CarBenefit",
              "employmentId" -> 1,
              "amount" -> 1,
              "description" -> "test",
              "iabdCategory" -> "Benefit",
              "inputAmount" -> 1
            )
          ),
          "current" ->  Json.arr(
            Json.obj(
              "componentType" -> "CarBenefit",
              "employmentId" -> 1,
              "amount" -> 1,
              "description" -> "test",
              "iabdCategory" -> "Benefit",
              "inputAmount" -> 1
            )
          )
        )

        val result: Future[Result] = testController.taxCodeChangeIabdComparison(nino)(FakeRequest())

        status(result) mustEqual OK
        contentAsJson(result) mustEqual expectedJson

      }
    }

    "respond with a BadRequest" when {
      "fetching tax free amount comparison fails :(" in {
        val nino = ninoGenerator

        when(taxFreeAmountComparisonService.taxFreeAmountComparison(Matchers.eq(nino))(Matchers.any()))
          .thenReturn(Future.failed(new RuntimeException("Its all gone wrong")))

        val result: Future[Result] = testController.taxCodeChangeIabdComparison(nino)(FakeRequest())

        status(result) mustEqual BAD_REQUEST
      }
    }
  }

  private def ninoGenerator = new Generator(new Random).nextNino

  private val taxFreeAmountComparisonService = mock[TaxFreeAmountComparisonService]

  val testController = new TaxCodeChangeIabdComparisonController(taxFreeAmountComparisonService, loggedInAuthenticationPredicate)

}

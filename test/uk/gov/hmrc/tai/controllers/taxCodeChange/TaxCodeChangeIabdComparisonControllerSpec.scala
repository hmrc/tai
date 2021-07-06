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

package uk.gov.hmrc.tai.controllers.taxCodeChange

import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.when
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{status, _}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.tai.factory.TaxFreeAmountComparisonFactory
import uk.gov.hmrc.tai.service.TaxFreeAmountComparisonService
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future
import scala.util.Random

class TaxCodeChangeIabdComparisonControllerSpec extends BaseSpec {

  "taxCodeChangeIabdComparison" must {
    "respond with OK" when {
      "when given a valid Nino" in {
        val nino = ninoGenerator

        val model = TaxFreeAmountComparisonFactory.create
        val expectedJson = Json.obj("data" -> TaxFreeAmountComparisonFactory.createJson, "links" -> Json.arr())

        when(taxFreeAmountComparisonService.taxFreeAmountComparison(meq(nino))(any()))
          .thenReturn(Future.successful(model))

        val result: Future[Result] = testController.taxCodeChangeIabdComparison(nino)(FakeRequest())

        status(result) mustEqual OK
        contentAsJson(result) mustEqual expectedJson

      }
    }

    "respond with a BadRequest" when {
      "fetching tax free amount comparison fails :(" in {
        val nino = ninoGenerator

        when(taxFreeAmountComparisonService.taxFreeAmountComparison(meq(nino))(any()))
          .thenReturn(Future.failed(new RuntimeException("Its all gone wrong")))

        val result: Future[Result] = testController.taxCodeChangeIabdComparison(nino)(FakeRequest())

        status(result) mustEqual BAD_REQUEST
      }
    }
  }

  private def ninoGenerator = new Generator(new Random).nextNino

  private val taxFreeAmountComparisonService = mock[TaxFreeAmountComparisonService]

  val testController =
    new TaxCodeChangeIabdComparisonController(taxFreeAmountComparisonService, loggedInAuthenticationPredicate, cc)

}

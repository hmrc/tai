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
import uk.gov.hmrc.tai.model.api.ApiResponse
import uk.gov.hmrc.tai.model.{TaxCodeHistory, TaxCodeRecord}
import uk.gov.hmrc.tai.service.TaxCodeChangeService

import scala.concurrent.Future
import scala.util.Random

class TaxCodeChangeRecordControllerSpec extends PlaySpec with MockitoSugar with MockAuthenticationPredicate {

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

//  "taxCodeHistory" should {
//    "return given nino's tax code history" in {
//
//      val testNino = ninoGenerator
//
//      val taxCodeHistory =
//        TaxCodeHistory(
//          testNino.nino,
//          Some(Seq(
//            TaxCodeRecord(taxCode="1185L", employerName="employer2", operatedTaxCode=true, p2Date=new LocalDate(2018, 7, 11)),
//            TaxCodeRecord(taxCode="1080L", employerName="employer1", operatedTaxCode=true, p2Date=new LocalDate(2018, 4, 11))
//          ))
//        )
//
//      when(mockTaxCodeService.taxCodeHistory(testNino)).thenReturn(Future.successful(taxCodeHistory))
//
//      val response = controller.taxCodeHistory(testNino)(FakeRequest())
//
//      contentAsJson(response) mustEqual Json.toJson(ApiResponse(taxCodeHistory, Nil))
//
//    }
//  }

  implicit val hc = HeaderCarrier()
  val mockConfig = mock[FeatureTogglesConfig]
  val mockTaxCodeService = mock[TaxCodeChangeService]

  private def controller = new TaxCodeChangeController(loggedInAuthenticationPredicate, mockTaxCodeService, mockConfig)
  private def ninoGenerator = new Generator(new Random).nextNino
}

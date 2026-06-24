/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.tai.controllers.taxCodeHistory

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{status, *}
import uk.gov.hmrc.domain.{Nino, NinoGenerator}
import uk.gov.hmrc.tai.factory.TaxCodeRecordFactory
import uk.gov.hmrc.tai.model.TaxCodeHistory
import uk.gov.hmrc.tai.model.api.ApiResponse
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.TaxCodeChangeService
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class TaxCodeHistoryControllerSpec extends BaseSpec {

  val testNino: Nino = NinoGenerator().nextNino
  val taxCodeService: TaxCodeChangeService = mock[TaxCodeChangeService]
  private def controller = new TaxCodeHistoryController(loggedInAuthenticationAuthJourney, taxCodeService, cc)

  "taxCodeHistory" must {
    "respond with OK and return tax code history" in {
      val taxCodeHistory = TaxCodeHistory(
        nino = testNino.withoutSuffix,
        taxCodeRecord = Seq(TaxCodeRecordFactory.createPrimaryEmployment())
      )

      val expectedResponse = Json.toJson(ApiResponse(taxCodeHistory, Seq.empty))

      when(taxCodeService.taxCodeHistory(any(), any())(any()))
        .thenReturn(Future.successful(taxCodeHistory))

      val result = controller.taxCodeHistory(testNino, TaxYear())(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustEqual expectedResponse
    }

    "respond with BAD_GATEWAY when a bad gateway exception occurs" in {

      when(taxCodeService.taxCodeHistory(any(), any())(any()))
        .thenReturn(Future.failed(badGatewayException))

      checkControllerResponse(
        badGatewayException,
        controller.taxCodeHistory(testNino, TaxYear())(FakeRequest()),
        BAD_GATEWAY
      )
    }

    "respond with NOT_FOUND when a NotFoundException occurs" in {

      when(taxCodeService.taxCodeHistory(any(), any())(any()))
        .thenReturn(Future.failed(notFoundException))

      checkControllerResponse(
        notFoundException,
        controller.taxCodeHistory(testNino, TaxYear())(FakeRequest()),
        NOT_FOUND
      )
    }

    "respond with BAD_GATEWAY when an InternalServerException occurs" in {

      when(taxCodeService.taxCodeHistory(any(), any())(any()))
        .thenReturn(Future.failed(internalServerException))

      checkControllerResponse(
        internalServerException,
        controller.taxCodeHistory(testNino, TaxYear())(FakeRequest()),
        BAD_GATEWAY
      )
    }
  }
}

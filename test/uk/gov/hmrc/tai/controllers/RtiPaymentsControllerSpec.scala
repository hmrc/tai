/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.tai.controllers

import cats.data.EitherT
import cats.instances.future.*
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.tai.config.CustomErrorHandler
import uk.gov.hmrc.tai.model.api.ApiResponse
import uk.gov.hmrc.tai.model.domain.{AnnualAccount, Available}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.RtiPaymentsService
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class RtiPaymentsControllerSpec extends BaseSpec {

  private val mockRtiPaymentsService = mock[RtiPaymentsService]
  private val sut =
    new RtiPaymentsController(mockRtiPaymentsService, loggedInAuthenticationAuthJourney, cc, inject[CustomErrorHandler])

  override protected def beforeEach(): Unit = {
    reset(mockRtiPaymentsService)
    super.beforeEach()
  }

  "rtiPayments" must {
    "return OK with RTI payments when service succeeds" in {
      val taxYear = TaxYear("2023")
      val payments = Seq(AnnualAccount(1, taxYear, Available, Nil, Nil))

      when(mockRtiPaymentsService.getRtiPayments(any(), any())(any(), any()))
        .thenReturn(EitherT.rightT(payments))

      val result = sut.rtiPayments(Nino(nino.nino), taxYear)(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(ApiResponse(payments, Nil))
      verify(mockRtiPaymentsService, times(1)).getRtiPayments(any(), any())(any(), any())
    }

    "return NOT_FOUND when service returns NOT_FOUND error" in {
      when(mockRtiPaymentsService.getRtiPayments(any(), any())(any(), any()))
        .thenReturn(EitherT.leftT(UpstreamErrorResponse("Not found", NOT_FOUND)))

      val result = sut.rtiPayments(Nino(nino.nino), TaxYear("2023"))(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "return BAD_REQUEST when service returns BAD_REQUEST error" in {
      when(mockRtiPaymentsService.getRtiPayments(any(), any())(any(), any()))
        .thenReturn(EitherT.leftT(UpstreamErrorResponse("Bad request", BAD_REQUEST)))

      val result = sut.rtiPayments(Nino(nino.nino), TaxYear("2023"))(FakeRequest())

      status(result) mustBe BAD_REQUEST
      contentAsString(result) mustBe "bad request, cause: REDACTED"
    }

    "return INTERNAL_SERVER_ERROR when service returns an unexpected error" in {
      when(mockRtiPaymentsService.getRtiPayments(any(), any())(any(), any()))
        .thenReturn(EitherT.leftT(UpstreamErrorResponse("Server error", INTERNAL_SERVER_ERROR)))

      val result = sut.rtiPayments(Nino(nino.nino), TaxYear("2023"))(FakeRequest())

      status(result) mustBe BAD_GATEWAY
    }
  }
}

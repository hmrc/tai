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

package uk.gov.hmrc.tai.service

import cats.data.EitherT
import cats.instances.future.*
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.http.Status.*
import play.api.mvc.Request
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.tai.connectors.RtiConnector
import uk.gov.hmrc.tai.model.domain.{AnnualAccount, Available, TemporarilyUnavailable}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class RtiPaymentsServiceSpec extends BaseSpec {

  private val mockRtiConnector = mock[RtiConnector]
  private val sut = new RtiPaymentsService(mockRtiConnector)

  implicit val request: Request[_] = FakeRequest()

  override protected def beforeEach(): Unit = {
    reset(mockRtiConnector)
    super.beforeEach()
  }

  "getRtiPayments" must {

    "return RTI payments when the connector returns data" in {
      val taxYear = TaxYear("2023")
      val payments = Seq(AnnualAccount(1, taxYear, Available, Nil, Nil))

      when(mockRtiConnector.getPaymentsForYear(any(), any())(any(), any()))
        .thenReturn(EitherT.rightT(payments))

      val result = sut.getRtiPayments(Nino(nino.nino), taxYear).value.futureValue

      result mustBe Right(payments)
      verify(mockRtiConnector, times(1)).getPaymentsForYear(any(), any())(any(), any())
    }

    "return stub account with service unavailable when the connector call fails" in {
      when(mockRtiConnector.getPaymentsForYear(any(), any())(any(), any()))
        .thenReturn(EitherT.leftT(UpstreamErrorResponse("RTI unavailable", INTERNAL_SERVER_ERROR)))

      val result = sut.getRtiPayments(Nino(nino.nino), TaxYear("2023")).value.futureValue

      result mustBe Right(
        Seq(
          AnnualAccount(
            sequenceNumber = 0,
            taxYear = TaxYear("2023"),
            rtiStatus = TemporarilyUnavailable
          )
        )
      )
    }

    "return stub account with service unavailable when RTI responds with BAD_REQUEST" in {
      when(mockRtiConnector.getPaymentsForYear(any(), any())(any(), any()))
        .thenReturn(EitherT.leftT(UpstreamErrorResponse("Bad request", BAD_REQUEST)))

      val result = sut.getRtiPayments(Nino(nino.nino), TaxYear("2023")).value.futureValue

      result mustBe Right(
        Seq(
          AnnualAccount(
            sequenceNumber = 0,
            taxYear = TaxYear("2023"),
            rtiStatus = TemporarilyUnavailable
          )
        )
      )

    }

    "return stub account with service unavailable when RTI responds with an unexpected error" in {
      when(mockRtiConnector.getPaymentsForYear(any(), any())(any(), any()))
        .thenReturn(EitherT.leftT(UpstreamErrorResponse("Unexpected error", INTERNAL_SERVER_ERROR)))

      val result = sut.getRtiPayments(Nino(nino.nino), TaxYear("2023")).value.futureValue

      result mustBe Right(
        Seq(
          AnnualAccount(
            sequenceNumber = 0,
            taxYear = TaxYear("2023"),
            rtiStatus = TemporarilyUnavailable
          )
        )
      )
    }
  }
}

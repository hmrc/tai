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

package uk.gov.hmrc.tai.service

import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.tai.TaxYear
import org.mockito.Matchers.any
import org.mockito.Matchers
import org.mockito.Mockito.when
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.model.api.{TaxCodeChange, TaxCodeRecordWithEndDate}

import scala.concurrent.duration._
import uk.gov.hmrc.tai.model.domain.{CarFuelBenefit, PersonalAllowancePA}
import uk.gov.hmrc.tai.util.TaxCodeHistoryConstants
import uk.gov.hmrc.time.TaxYearResolver

import scala.concurrent.{Await, Future}
import scala.util.Random

class TaxFreeAmountComparisonServiceSpec  extends PlaySpec with MockitoSugar with TaxCodeHistoryConstants {

  "taxFreeAmountComparison" should {
    "return a sequence of current coding components" when {
      "called with a valid nino" in {
        val taxCodeChangeService = mock[TaxCodeChangeService]
        val codingComponentService = mock[CodingComponentService]

        val currentCodingComponents = Seq[CodingComponent](
          CodingComponent(PersonalAllowancePA, Some(123), 12345, "some description"),
          CodingComponent(CarFuelBenefit, Some(124), 66666, "some other description")
        )

        when(codingComponentService.codingComponents(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
          .thenReturn(Future.successful(currentCodingComponents))

        val expected = TaxFreeAmountComparison(Seq.empty, currentCodingComponents)

        val service = createTestService(taxCodeChangeService, codingComponentService)

        val result: TaxFreeAmountComparison = Await.result(service.taxFreeAmountComparison(nino), 5.seconds)

        result mustBe expected
      }
    }

    "return a sequence of previous coding components" when {
      "called with a valid nino" in {
        val taxCodeChangeService = mock[TaxCodeChangeService]
        val codingComponentService = mock[CodingComponentService]

        val taxCodeChange = stubTaxCodeChange

        when(codingComponentService.codingComponents(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
          .thenReturn(Future.successful(Seq.empty))

        when(taxCodeChangeService.taxCodeChange(Matchers.eq(nino))(any()))
          .thenReturn(Future.successful(taxCodeChange))

        val expected = TaxFreeAmountComparison(Seq.empty, Seq.empty)

        val service = createTestService(taxCodeChangeService, codingComponentService)

        val result: TaxFreeAmountComparison = Await.result(service.taxFreeAmountComparison(nino), 5.seconds)

        result mustBe expected
      }
    }
  }

  // TODO: Move to Factory
  private def stubTaxCodeChange: TaxCodeChange = {
    val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
    val currentEndDate = TaxYearResolver.endOfCurrentTaxYear
    val previousStartDate = TaxYearResolver.startOfCurrentTaxYear
    val previousEndDate = currentStartDate.minusDays(1)
    val payrollNumberPrev = "123"
    val payrollNumberCurr = "456"

    val currentTaxCodeRecords: Seq[TaxCodeRecordWithEndDate] = Seq(
      TaxCodeRecordWithEndDate("1000L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true),
      TaxCodeRecordWithEndDate("185L", Cumulative, currentStartDate, currentEndDate, "Employer 2", Some(payrollNumberCurr), pensionIndicator = false, primary = false)
    )

    val previousTaxCodeRecords: Seq[TaxCodeRecordWithEndDate] = Seq(
      TaxCodeRecordWithEndDate("1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true),
      TaxCodeRecordWithEndDate("BR", Cumulative, previousStartDate, previousEndDate, "Employer 2", Some(payrollNumberPrev), pensionIndicator = false, primary = false)
    )

    TaxCodeChange(currentTaxCodeRecords, previousTaxCodeRecords)
  }

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val nino: Nino = new Generator(new Random).nextNino

  private def createTestService(taxCodeChangeService: TaxCodeChangeService,
                                codingComponentService: CodingComponentService): TaxFreeAmountComparisonService = {
    new TaxFreeAmountComparisonService(taxCodeChangeService, codingComponentService)
  }

}

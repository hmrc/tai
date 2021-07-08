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

package uk.gov.hmrc.tai.service

import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.tai.model.TaxFreeAmountComparison
import uk.gov.hmrc.tai.model.api.{TaxCodeChange, TaxCodeSummary}
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.domain.{CarFuelBenefit, PersonalAllowancePA}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.{BaseSpec, TaxCodeHistoryConstants}

import scala.concurrent.Future

class TaxFreeAmountComparisonServiceSpec extends BaseSpec with TaxCodeHistoryConstants {

  "taxFreeAmountComparison" should {
    "return a sequence of current coding components" when {
      "called with a valid nino" in {
        val taxCodeChangeService = mock[TaxCodeChangeServiceImpl]
        val codingComponentService = mock[CodingComponentService]

        val currentCodingComponents = Seq[CodingComponent](
          CodingComponent(PersonalAllowancePA, Some(123), 12345, "some description"),
          CodingComponent(CarFuelBenefit, Some(124), 66666, "some other description")
        )

        val taxCodeChange = TaxCodeChange(Seq.empty, Seq.empty)

        when(taxCodeChangeService.taxCodeChange(meq(nino))(any()))
          .thenReturn(Future.successful(taxCodeChange))

        when(codingComponentService.codingComponents(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(currentCodingComponents))

        val expected = TaxFreeAmountComparison(Seq.empty, currentCodingComponents)

        val service = createTestService(taxCodeChangeService, codingComponentService)

        val result: TaxFreeAmountComparison = service.taxFreeAmountComparison(nino).futureValue

        result mustBe expected
      }
    }

    "return a Future Failed" when {
      "the service call for taxCodeChange fails" in {

        val taxCodeChangeService = mock[TaxCodeChangeServiceImpl]
        val codingComponentService = mock[CodingComponentService]

        val currentCodingComponents = Seq[CodingComponent](
          CodingComponent(PersonalAllowancePA, Some(123), 12345, "some description"),
          CodingComponent(CarFuelBenefit, Some(124), 66666, "some other description")
        )

        when(taxCodeChangeService.taxCodeChange(meq(nino))(any()))
          .thenReturn(Future.failed(new RuntimeException("Error")))

        when(codingComponentService.codingComponents(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(currentCodingComponents))

        val service = createTestService(taxCodeChangeService, codingComponentService)

        val result = service.taxFreeAmountComparison(nino).failed.futureValue

        result mustBe a[RuntimeException]
        result.getMessage mustBe "Error"
      }

      "service call for the previous coding components fails" in {
        val taxCodeChangeService = mock[TaxCodeChangeServiceImpl]
        val codingComponentService = mock[CodingComponentService]

        val taxCodeChange = stubTaxCodeChange

        when(taxCodeChangeService.taxCodeChange(meq(nino))(any()))
          .thenReturn(Future.successful(taxCodeChange))

        when(codingComponentService.codingComponents(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(Seq.empty))

        when(
          codingComponentService
            .codingComponentsForTaxCodeId(meq(nino), meq(PRIMARY_PREVIOUS_TAX_CODE_ID))(any()))
          .thenReturn(Future.failed(new BadRequestException("Error")))

        val service = createTestService(taxCodeChangeService, codingComponentService)

        val result = service.taxFreeAmountComparison(nino).failed.futureValue

        result mustBe a[BadRequestException]
        result.getMessage mustBe "Error"
      }
    }

    "return a sequence of previous coding components" when {
      "called with a valid nino" in {
        val taxCodeChangeService = mock[TaxCodeChangeServiceImpl]
        val codingComponentService = mock[CodingComponentService]

        val codingComponent1 = CodingComponent(PersonalAllowancePA, Some(123), 12345, "some description")
        val codingComponent2 = CodingComponent(CarFuelBenefit, Some(124), 66666, "some other description")

        val previousCodingComponents = Seq[CodingComponent](codingComponent1, codingComponent2)

        val taxCodeChange = stubTaxCodeChange

        when(taxCodeChangeService.taxCodeChange(meq(nino))(any()))
          .thenReturn(Future.successful(taxCodeChange))

        when(codingComponentService.codingComponents(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(Seq.empty))

        when(
          codingComponentService
            .codingComponentsForTaxCodeId(meq(nino), meq(PRIMARY_PREVIOUS_TAX_CODE_ID))(any()))
          .thenReturn(Future.successful(previousCodingComponents))

        val expected = TaxFreeAmountComparison(previousCodingComponents, Seq.empty)

        val service = createTestService(taxCodeChangeService, codingComponentService)

        val result: TaxFreeAmountComparison = service.taxFreeAmountComparison(nino).futureValue

        result mustBe expected
      }
    }
  }

  val PRIMARY_PREVIOUS_TAX_CODE_ID = 1

  private def stubTaxCodeChange: TaxCodeChange = {
    val currentStartDate = TaxYear().start.plusDays(2)
    val currentEndDate = TaxYear().end
    val previousStartDate = TaxYear().start
    val previousEndDate = currentStartDate.minusDays(1)
    val payrollNumberPrev = "123"
    val payrollNumberCurr = "456"

    val previousTaxCodeRecords: Seq[TaxCodeSummary] = Seq(
      TaxCodeSummary(
        PRIMARY_PREVIOUS_TAX_CODE_ID,
        "1185L",
        Cumulative,
        previousStartDate,
        previousEndDate,
        "Employer 1",
        Some(payrollNumberPrev),
        pensionIndicator = false,
        primary = true
      ),
      TaxCodeSummary(
        2,
        "BR",
        Cumulative,
        previousStartDate,
        previousEndDate,
        "Employer 2",
        Some(payrollNumberPrev),
        pensionIndicator = false,
        primary = false)
    )

    val currentTaxCodeRecords: Seq[TaxCodeSummary] = Seq(
      TaxCodeSummary(
        3,
        "1000L",
        Cumulative,
        currentStartDate,
        currentEndDate,
        "Employer 1",
        Some(payrollNumberCurr),
        pensionIndicator = false,
        primary = true),
      TaxCodeSummary(
        4,
        "185L",
        Cumulative,
        currentStartDate,
        currentEndDate,
        "Employer 2",
        Some(payrollNumberCurr),
        pensionIndicator = false,
        primary = false)
    )

    TaxCodeChange(currentTaxCodeRecords, previousTaxCodeRecords)
  }

  private def createTestService(
    taxCodeChangeService: TaxCodeChangeServiceImpl,
    codingComponentService: CodingComponentService): TaxFreeAmountComparisonService =
    new TaxFreeAmountComparisonService(taxCodeChangeService, codingComponentService)

}

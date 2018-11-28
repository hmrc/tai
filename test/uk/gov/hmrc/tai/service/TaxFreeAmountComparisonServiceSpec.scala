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
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.tai.connectors.TaxAccountHistoryConnector
import uk.gov.hmrc.tai.model.api.{TaxCodeChange, TaxCodeRecordWithEndDate}

import scala.concurrent.duration._
import uk.gov.hmrc.tai.model.domain.{CarFuelBenefit, PersonalAllowancePA}
import uk.gov.hmrc.tai.util.TaxCodeHistoryConstants
import uk.gov.hmrc.time.TaxYearResolver

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Random, Success}

class TaxFreeAmountComparisonServiceSpec  extends PlaySpec with MockitoSugar with TaxCodeHistoryConstants {

  "taxFreeAmountComparison" should {
    "return a sequence of current coding components" when {
      "called with a valid nino" in {
        val taxCodeChangeService = mock[TaxCodeChangeService]
        val codingComponentService = mock[CodingComponentService]
        val taxAccountHistoryConnector = mock[TaxAccountHistoryConnector]

        val currentCodingComponents = Seq[CodingComponent](
          CodingComponent(PersonalAllowancePA, Some(123), 12345, "some description"),
          CodingComponent(CarFuelBenefit, Some(124), 66666, "some other description")
        )

        when(codingComponentService.codingComponents(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
          .thenReturn(Future.successful(currentCodingComponents))

        val expected = TaxFreeAmountComparison(Seq.empty, currentCodingComponents)

        val service = createTestService(taxCodeChangeService, codingComponentService, taxAccountHistoryConnector)

        val result: TaxFreeAmountComparison = Await.result(service.taxFreeAmountComparison(nino), 5.seconds)

        result mustBe expected
      }
    }

    "return a sequence of previous coding components" when {
      "called with a valid nino" ignore {
        val taxCodeChangeService = mock[TaxCodeChangeService]
        val codingComponentService = mock[CodingComponentService]
        val taxAccountHistoryConnector = mock[TaxAccountHistoryConnector]

        val taxCodeChange = stubTaxCodeChange

        when(codingComponentService.codingComponents(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
          .thenReturn(Future.successful(Seq.empty))

        when(taxCodeChangeService.taxCodeChange(Matchers.eq(nino))(any()))
          .thenReturn(Future.successful(taxCodeChange))

        val expected = TaxFreeAmountComparison(Seq.empty, Seq.empty)

        val service = createTestService(taxCodeChangeService, codingComponentService, taxAccountHistoryConnector)

        val result: TaxFreeAmountComparison = Await.result(service.taxFreeAmountComparison(nino), 5.seconds)

        result mustBe expected
      }
    }
  }

  "previousTaxCodeChangeIds" should {
    "return a sequence of tax code ids relating to the previous tax codes" in {
      val taxCodeChangeService = mock[TaxCodeChangeService]
      val codingComponentService = mock[CodingComponentService]
      val taxAccountHistoryConnector = mock[TaxAccountHistoryConnector]

      val taxCodeChange = stubTaxCodeChange

      when(taxCodeChangeService.taxCodeChange(Matchers.eq(nino))(any()))
        .thenReturn(Future.successful(taxCodeChange))

      val service = createTestService(taxCodeChangeService, codingComponentService, taxAccountHistoryConnector)

      val result = Await.result(service.previousTaxCodeChangeIds(nino), 5.seconds)

      result mustBe Seq(1,2)
    }
  }

  "buildPreviousCodingComponentsFromIds" should {
    "return a seq of coding components given a set of taxCodeIds" when {
      "all request return successfully" in {
        val taxCodeChangeService = mock[TaxCodeChangeService]
        val codingComponentService = mock[CodingComponentService]
        val taxAccountHistoryConnector = mock[TaxAccountHistoryConnector]

        val codingComponent1 = CodingComponent(PersonalAllowancePA, Some(123), 12345, "some description")
        val codingComponent2 = CodingComponent(CarFuelBenefit, Some(124), 66666, "some other description")

        val currentCodingComponents = Seq[CodingComponent](codingComponent1, codingComponent2)

        when(taxAccountHistoryConnector.taxAccountHistory(Matchers.eq(nino), Matchers.eq(1)))
          .thenReturn(Future.successful(Success(Seq(codingComponent1))))

        when(taxAccountHistoryConnector.taxAccountHistory(Matchers.eq(nino), Matchers.eq(2)))
          .thenReturn(Future.successful(Success(Seq(codingComponent2))))

        val service = createTestService(taxCodeChangeService, codingComponentService, taxAccountHistoryConnector)

        val result = Await.result(service.buildPreviousCodingComponentsFromIds(nino, Seq(1, 2)), 5.seconds)

        result mustBe currentCodingComponents
      }
    }

    "return a Future.failed" when {
      "one of the requests fails" in {
        val taxCodeChangeService = mock[TaxCodeChangeService]
        val codingComponentService = mock[CodingComponentService]
        val taxAccountHistoryConnector = mock[TaxAccountHistoryConnector]

        val codingComponent1 = CodingComponent(PersonalAllowancePA, Some(123), 12345, "some description")

        when(taxAccountHistoryConnector.taxAccountHistory(Matchers.eq(nino), Matchers.eq(1)))
          .thenReturn(Future.successful(Success(Seq(codingComponent1))))

        when(taxAccountHistoryConnector.taxAccountHistory(Matchers.eq(nino), Matchers.eq(2)))
          .thenReturn(Future.successful(Failure(new BadRequestException("Error"))))

        val service = createTestService(taxCodeChangeService, codingComponentService, taxAccountHistoryConnector)

        val exception = the[RuntimeException] thrownBy Await.result(service.buildPreviousCodingComponentsFromIds(nino, Seq(1, 2)), 5.seconds)

        exception.getMessage mustBe "Could not retrieve all previous coding components - Error"
      }
    }
  }

  "codingComponentsForId" should {
    "return a Success seq of coding components for a taxCodeId" when {
      "a successful response is returned" in {
        val taxCodeChangeService = mock[TaxCodeChangeService]
        val codingComponentService = mock[CodingComponentService]
        val taxAccountHistoryConnector = mock[TaxAccountHistoryConnector]

        val expected = Seq(CodingComponent(PersonalAllowancePA, Some(123), 12345, "some description"))

        when(taxAccountHistoryConnector.taxAccountHistory(Matchers.eq(nino), Matchers.eq(1)))
          .thenReturn(Future.successful(Success(expected)))

        val service = createTestService(taxCodeChangeService, codingComponentService, taxAccountHistoryConnector)

        val result = Await.result(service.codingComponentsForId(nino, 1), 5.seconds)

        result mustBe expected
      }
    }

    "return a Failure for a taxCodeId" when {
      "a failure response is returned" in {
        val taxCodeChangeService = mock[TaxCodeChangeService]
        val codingComponentService = mock[CodingComponentService]
        val taxAccountHistoryConnector = mock[TaxAccountHistoryConnector]

        when(taxAccountHistoryConnector.taxAccountHistory(Matchers.eq(nino), Matchers.eq(1)))
          .thenReturn(Future.successful(Failure(new BadRequestException("Error"))))

        val service = createTestService(taxCodeChangeService, codingComponentService, taxAccountHistoryConnector)

        val exception = the[BadRequestException] thrownBy Await.result(service.codingComponentsForId(nino, 1), 5.seconds)

        exception.getMessage mustBe "Error"
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

    val previousTaxCodeRecords: Seq[TaxCodeRecordWithEndDate] = Seq(
      TaxCodeRecordWithEndDate(1, "1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true),
      TaxCodeRecordWithEndDate(2, "BR", Cumulative, previousStartDate, previousEndDate, "Employer 2", Some(payrollNumberPrev), pensionIndicator = false, primary = false)
    )

    val currentTaxCodeRecords: Seq[TaxCodeRecordWithEndDate] = Seq(
      TaxCodeRecordWithEndDate(3, "1000L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true),
      TaxCodeRecordWithEndDate(4, "185L", Cumulative, currentStartDate, currentEndDate, "Employer 2", Some(payrollNumberCurr), pensionIndicator = false, primary = false)
    )

    TaxCodeChange(currentTaxCodeRecords, previousTaxCodeRecords)
  }

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val nino: Nino = new Generator(new Random).nextNino

  private def createTestService(taxCodeChangeService: TaxCodeChangeService,
                                codingComponentService: CodingComponentService,
                                taxAccountHistoryConnector: TaxAccountHistoryConnector): TaxFreeAmountComparisonService = {
    new TaxFreeAmountComparisonService(taxCodeChangeService, codingComponentService, taxAccountHistoryConnector)
  }

}

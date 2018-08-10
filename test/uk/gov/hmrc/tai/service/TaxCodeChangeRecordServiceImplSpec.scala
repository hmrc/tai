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

import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.connectors.TaxCodeChangeConnector
import uk.gov.hmrc.tai.model.api.TaxCodeChange
import uk.gov.hmrc.tai.model.{TaxCodeHistory, TaxCodeRecord, api}
import uk.gov.hmrc.tai.util.TaiConstants
import uk.gov.hmrc.time.TaxYearResolver

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

class TaxCodeChangeRecordServiceImplSpec extends PlaySpec with MockitoSugar {

  "hasTaxCodeChanged" should {
    "return true" when {
      "there has been a tax code change the year" in {
        val thisYear = LocalDate.now()
        val lastYear = LocalDate.now().minusYears(1)
        val testNino = randomNino

        val taxCodeHistory = TaxCodeHistory(
          nino = testNino.withoutSuffix,
          taxCodeRecord = Seq(
            TaxCodeRecord(taxCode = "1185L", employerName = "employer2", operatedTaxCode = true, p2Date = thisYear),
            TaxCodeRecord(taxCode = "1080L", employerName = "employer1", operatedTaxCode = true, p2Date = lastYear)
          )
        )

        val mockConnector = mock[TaxCodeChangeConnector]
        when(mockConnector.taxCodeHistory(any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service = new TaxCodeChangeServiceImpl(mockConnector)
        Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual true
      }
    }

    "return false" when {
      "there are no tax code changes" in {

        val testNino = randomNino

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq()
        )

        val mockConnector = mock[TaxCodeChangeConnector]
        when(mockConnector.taxCodeHistory(any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service = new TaxCodeChangeServiceImpl(mockConnector)
        Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual false
      }

      "there has not been a tax code change in the year" in {
        val lastYear = LocalDate.now().minusYears(1)
        val testNino = randomNino

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(
            TaxCodeRecord(taxCode = "1185L", employerName = "employer2", operatedTaxCode = true, p2Date = lastYear)
          )
        )

        val mockConnector = mock[TaxCodeChangeConnector]
        when(mockConnector.taxCodeHistory(any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service = new TaxCodeChangeServiceImpl(mockConnector)
        Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual false
      }

    }
  }

  "taxCodeChange" should {

    "return a domain TaxCodeRecord" when {

      "there is only one tax code change in the current tax year" in {

        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
        val currentEndDate = TaxYearResolver.endOfCurrentTaxYear
        val previousStartDate = TaxYearResolver.startOfCurrentTaxYear
        val PreviousEndDate = currentStartDate.minusDays(1)

        val testNino = randomNino

        val expectedPreviousTaxCodeChange = api.TaxCodeChangeRecord("1185L", previousStartDate, PreviousEndDate, "Employer 1")
        val expectedCurrentTaxCodeChange = api.TaxCodeChangeRecord("1000L", currentStartDate, currentEndDate, "Employer 2")

        val previousTaxCodeRecord = TaxCodeRecord(taxCode = "1185L", employerName = "Employer 1", operatedTaxCode = true,
                                                  p2Date = TaxYearResolver.startOfCurrentTaxYear.minusMonths(1))
        val currentTaxCodeRecord = TaxCodeRecord(taxCode = "1000L", employerName = "Employer 2", operatedTaxCode = true,
                                                  p2Date = currentStartDate)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord, currentTaxCodeRecord)
        )

        val mockConnector = mock[TaxCodeChangeConnector]
        when(mockConnector.taxCodeHistory(any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service = new TaxCodeChangeServiceImpl(mockConnector)

        val expectedResult = TaxCodeChange(expectedCurrentTaxCodeChange, expectedPreviousTaxCodeChange)

        Await.result(service.taxCodeHistory(testNino), 5.seconds) mustEqual expectedResult
      }

      "there are two tax code change in the current tax year" in {

        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
        val currentEndDate = TaxYearResolver.endOfCurrentTaxYear
        val previousStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(1)
        val PreviousEndDate = currentStartDate.minusDays(1)

        val testNino = randomNino

        val expectedPreviousTaxCodeChange = api.TaxCodeChangeRecord("1185L", previousStartDate, PreviousEndDate, "Employer 1")
        val expectedCurrentTaxCodeChange = api.TaxCodeChangeRecord("1000L", currentStartDate, currentEndDate, "Employer 2")

        val previousTaxCodeRecord = TaxCodeRecord(taxCode = "1185L", employerName = "Employer 1", operatedTaxCode = true,
          p2Date = previousStartDate)
        val currentTaxCodeRecord = TaxCodeRecord(taxCode = "1000L", employerName = "Employer 2", operatedTaxCode = true,
          p2Date = currentStartDate)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord, currentTaxCodeRecord)
        )

        val mockConnector = mock[TaxCodeChangeConnector]
        when(mockConnector.taxCodeHistory(any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service = new TaxCodeChangeServiceImpl(mockConnector)

        val expectedResult = TaxCodeChange(expectedCurrentTaxCodeChange, expectedPreviousTaxCodeChange)

        Await.result(service.taxCodeHistory(testNino), 5.seconds) mustEqual expectedResult
      }
    }
  }

  val dateFormatter = DateTimeFormat.forPattern(TaiConstants.npsDateFormat)
  def randomNino: Nino = new Generator(new Random).nextNino

}
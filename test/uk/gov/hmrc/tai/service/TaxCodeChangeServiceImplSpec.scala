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
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.JsResultException
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.TaxCodeChangeConnector
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.model.api.{TaxCodeChange, TaxCodeChangeRecord}
import uk.gov.hmrc.tai.util.TaxCodeHistoryConstants
import uk.gov.hmrc.time.TaxYearResolver

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

class TaxCodeChangeServiceImplSpec extends PlaySpec with MockitoSugar with TaxCodeHistoryConstants {

  implicit val hc = HeaderCarrier()

  "hasTaxCodeChanged" should {

    "return true" when {

      "the tax code has been operated for single employments" when {

        "there has been a tax code change after Annual Coding" in {
          val newCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
          val previousCodeDate = TaxYearResolver.startOfCurrentTaxYear
          val testNino = randomNino

          val taxCodeHistory = TaxCodeHistory(
            nino = testNino.withoutSuffix,
            taxCodeRecord = Seq(
              taxCodeRecord(dateOfCalculation = newCodeDate),
              taxCodeRecord(dateOfCalculation = previousCodeDate)
            )
          )

          when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

          val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
          Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual true
        }

        "there has been a tax code change after Annual Coding where Annual coding was before start of tax year" in {
          val newCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
          val previousCodeDate = TaxYearResolver.startOfCurrentTaxYear.minusMonths(1)
          val testNino = randomNino

          val taxCodeHistory = TaxCodeHistory(
            nino = testNino.withoutSuffix,
            taxCodeRecord = Seq(
              taxCodeRecord(dateOfCalculation = newCodeDate),
              taxCodeRecord(taxCode = "1080L", dateOfCalculation = previousCodeDate)
            )
          )

          when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

          val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
          Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual true
        }

        "there has been a change in job with a different tax code after Annual Coding" in {
          val newCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
          val previousCodeDate = TaxYearResolver.startOfCurrentTaxYear
          val testNino = randomNino


          val taxCodeHistory = TaxCodeHistory(
            nino = testNino.withoutSuffix,
            taxCodeRecord = Seq(
              taxCodeRecord(taxCode ="1180L", dateOfCalculation = newCodeDate),
              taxCodeRecord(taxCode = "1080L", dateOfCalculation = previousCodeDate)
            )
          )

          when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

          val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
          Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual true
        }

        "there has been more than one daily tax code change in the year" in {
          val newCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
          val previousCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(1)
          val annualCodeDate = TaxYearResolver.startOfCurrentTaxYear.minusMonths(1)
          val testNino = randomNino


          val taxCodeHistory = TaxCodeHistory(
            nino = testNino.withoutSuffix,
            taxCodeRecord = Seq(
              taxCodeRecord(taxCode ="1180L", dateOfCalculation = newCodeDate),
              taxCodeRecord(taxCode ="1080L", dateOfCalculation = previousCodeDate),
              taxCodeRecord(taxCode ="1000L", dateOfCalculation = annualCodeDate)
            )
          )

          when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

          val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
          Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual true
        }
      }

      "the tax code has been operated for multiple employments" when {

        "there has been more than one daily tax code change in the year for 2 employments to 2 employments" in {
          val newCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
          val previousCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(1)
          val testNino = randomNino
          val payrollNumber1 = randomInt().toString
          val payrollNumber2 = randomInt().toString


          val taxCodeHistory = TaxCodeHistory(
            nino = testNino.withoutSuffix,
            taxCodeRecord = Seq(
              taxCodeRecord(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1)),
              taxCodeRecord(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber2), employmentType = Secondary),
              taxCodeRecord(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber1)),
              taxCodeRecord(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber2), employmentType = Secondary)
            )
          )

          when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

          val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
          Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual true
        }

        "there has been more than one daily tax code change in the year for 1 employment to 2 employments" in {
          val newCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
          val previousCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(1)
          val testNino = randomNino
          val payrollNumber1 = randomInt().toString
          val payrollNumber2 = randomInt().toString

          val taxCodeRecords = Seq(
            taxCodeRecord(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1)),
            taxCodeRecord(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber2), employmentType = Secondary),
            taxCodeRecord(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber1))
          )

          val taxCodeHistory = TaxCodeHistory(nino = testNino.withoutSuffix, taxCodeRecord = taxCodeRecords)

          when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

          val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
          Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual true
        }

        "there has been more than one daily tax code change in the year for 2 employments to 1 employment" in {
          val newCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
          val previousCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(1)
          val testNino = randomNino
          val payrollNumber1 = randomInt().toString
          val payrollNumber2 = randomInt().toString

          val taxCodeRecords = Seq(
            taxCodeRecord(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1)),
            taxCodeRecord(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber1)),
            taxCodeRecord(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber2), employmentType = Secondary)
          )

          val taxCodeHistory = TaxCodeHistory(nino = testNino.withoutSuffix, taxCodeRecord = taxCodeRecords)

          when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

          val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
          Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual true
        }

        "there has been a tax code change after Annual Coding for 2 employments to 2 employments" in {
          val newCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
          val previousCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(1)
          val testNino = randomNino
          val payrollNumber1 = randomInt().toString
          val payrollNumber2 = randomInt().toString

          val taxCodeRecords = Seq(
            taxCodeRecord(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1)),
            taxCodeRecord(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber2), employmentType = Secondary),
            taxCodeRecord(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber1)),
            taxCodeRecord(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber2), employmentType = Secondary)
          )

          val taxCodeHistory = TaxCodeHistory(nino = testNino.withoutSuffix, taxCodeRecord = taxCodeRecords)

          when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

          val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
          Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual true
        }
      }
    }

    "return false" when {

      "for single employments" when {

        "this is the first ever employment" in {
          val testNino = randomNino
          val newCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)

          val taxCodeHistory = TaxCodeHistory(
            testNino.withoutSuffix,
            Seq(taxCodeRecord(dateOfCalculation = newCodeDate))
          )

          when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

          val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
          Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual false
        }

        "there has been one tax code change in the year but it has not been operated" in {
          val newCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
          val previousCodeDate = TaxYearResolver.startOfCurrentTaxYear
          val testNino = randomNino

          val taxCodeHistory = TaxCodeHistory(
            nino = testNino.withoutSuffix,
            taxCodeRecord = Seq(
              taxCodeRecord(dateOfCalculation = newCodeDate, operatedTaxCode = false),
              taxCodeRecord(dateOfCalculation = previousCodeDate)
            )
          )

          when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

          val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
          Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual false
        }

        "there has not been a tax code change in the year" in {
          val annualCodeDate = TaxYearResolver.startOfCurrentTaxYear
          val testNino = randomNino

          val taxCodeHistory = TaxCodeHistory(
            testNino.withoutSuffix,
            Seq(taxCodeRecord(dateOfCalculation = annualCodeDate))
          )

          when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

          val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
          Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual false
        }

        "there has not been a tax code change in the year and annual coding was done before the start of the tax year" in {
          val annualCodeDate = TaxYearResolver.startOfCurrentTaxYear.minusMonths(1)
          val testNino = randomNino

          val taxCodeHistory = TaxCodeHistory(
            testNino.withoutSuffix,
            Seq(taxCodeRecord(dateOfCalculation = annualCodeDate))
          )

          when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

          val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
          Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual false
        }
      }

      "Multiple tax code changes have been made prior to the start of the current tax year" in {

        val annualCodingDate = TaxYearResolver.startOfCurrentTaxYear.minusMonths(2)
        val dailyCodingDate = TaxYearResolver.startOfCurrentTaxYear.minusMonths(1)
        val testNino = randomNino

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(
            taxCodeRecord(dateOfCalculation = dailyCodingDate),
            taxCodeRecord(dateOfCalculation = annualCodingDate)
          )
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
        Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual false
      }

      "for multiple employments" when {

        "this is the first ever employment but 2 employments start at the same time" in {
          val dailyCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
          val testNino = randomNino

          val taxCodeHistory = TaxCodeHistory(
            testNino.withoutSuffix,
            Seq(
              taxCodeRecord(dateOfCalculation = dailyCodeDate),
              taxCodeRecord(dateOfCalculation = dailyCodeDate, employmentType = Secondary)
            )
          )

          when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

          val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
          Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual false
        }

        "there has not been a tax code change in the year (2 employments at Annual Coding)" in {
          val annualCodeDate = TaxYearResolver.startOfCurrentTaxYear
          val testNino = randomNino

          val taxCodeHistory = TaxCodeHistory(
            testNino.withoutSuffix,
            Seq(
              taxCodeRecord(employerName = "Employer A", dateOfCalculation = annualCodeDate),
              taxCodeRecord(employerName = "Employer B", dateOfCalculation = annualCodeDate, employmentType = Secondary)
            )
          )

          when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

          val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
          Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual false
        }
      }

      "an empty sequence of TaxCodeRecords is returned in TaxCodeHistory" in {
        val testNino = randomNino

        val taxCodeHistory = TaxCodeHistory(testNino.withoutSuffix, Seq.empty[TaxCodeRecord])

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
        Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual false
      }

      "a JSExceptionResult is thrown by the connector" in {
        val testNino = randomNino

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.failed(JsResultException(Nil)))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
        Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual false
      }
    }
  }

  "taxCodeChange" should {

    "return a TaxCodeChangeRecord for single employments" when {

      "there has been a tax code change in the year after annual coding" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
        val currentEndDate = TaxYearResolver.endOfCurrentTaxYear
        val previousStartDate = TaxYearResolver.startOfCurrentTaxYear
        val previousEndDate = currentStartDate.minusDays(1)
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val payrollNumberPrev = randomInt().toString
        val payrollNumberCurr = randomInt().toString

        val testNino = randomNino

        val expectedPreviousTaxCodeChange = TaxCodeChangeRecord("1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange = TaxCodeChangeRecord("1000L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)

        val previousTaxCodeRecord = TaxCodeRecord("1185L", Cumulative, "Employer 1", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val currentTaxCodeRecord = TaxCodeRecord("1000L", Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord, currentTaxCodeRecord)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq(expectedCurrentTaxCodeChange), Seq(expectedPreviousTaxCodeChange))

        Await.result(service.taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }

      "there has been more than one daily tax code change in the year" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
        val currentEndDate = TaxYearResolver.endOfCurrentTaxYear
        val previousStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(1)
        val previousEndDate = currentStartDate.minusDays(1)
        val payrollNumberPrev = randomInt().toString
        val payrollNumberCurr = randomInt().toString

        val testNino = randomNino

        val expectedPreviousTaxCodeChange = TaxCodeChangeRecord("1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange = TaxCodeChangeRecord("1000L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)

        val previousTaxCodeRecord = TaxCodeRecord("1185L", Cumulative, "Employer 1", operatedTaxCode = true, previousStartDate, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val currentTaxCodeRecord = TaxCodeRecord("1000L", Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord, currentTaxCodeRecord)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq(expectedCurrentTaxCodeChange), Seq(expectedPreviousTaxCodeChange))

        Await.result(service.taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }

      "there has been three tax code changes in the year but the most recent tax code is not operated" in {
        val nonOperatedStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(5)
        val currentStartDate = nonOperatedStartDate.minusDays(3)
        val previousStartDate = nonOperatedStartDate.minusDays(4)

        val currentEndDate = TaxYearResolver.endOfCurrentTaxYear
        val previousEndDate = currentStartDate.minusDays(1)
        val payrollNumberPrev = randomInt().toString
        val payrollNumberCurr = randomInt().toString
        val payrollNumberNotOp = randomInt().toString

        val testNino = randomNino

        val expectedPreviousTaxCodeChange = TaxCodeChangeRecord("1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange = TaxCodeChangeRecord("1000L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)

        val previousTaxCodeRecord = TaxCodeRecord("1185L", Cumulative, "Employer 1", operatedTaxCode = true, previousStartDate, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val currentTaxCodeRecord = TaxCodeRecord("1000L", Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)
        val nonOperatedCode = TaxCodeRecord("1185L", Cumulative, "Employer 1", operatedTaxCode = false, nonOperatedStartDate, Some(payrollNumberNotOp), pensionIndicator = false, Primary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(nonOperatedCode, previousTaxCodeRecord, currentTaxCodeRecord)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq(expectedCurrentTaxCodeChange), Seq(expectedPreviousTaxCodeChange))

        Await.result(service.taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }
    }

    "return a TaxCodeChange for multiple employments" when {

      "there has been a tax code change after Annual Coding for 2 employments to 2 employments" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
        val currentEndDate = TaxYearResolver.endOfCurrentTaxYear
        val previousStartDate = TaxYearResolver.startOfCurrentTaxYear
        val previousEndDate = currentStartDate.minusDays(1)
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val payrollNumberPrev = randomInt().toString
        val payrollNumberCurr = randomInt().toString

        val testNino = randomNino

        val expectedPreviousTaxCodeChange1 = TaxCodeChangeRecord("1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedPreviousTaxCodeChange2 = TaxCodeChangeRecord("BR", Cumulative, previousStartDate, previousEndDate, "Employer 2", Some(payrollNumberPrev), pensionIndicator = false, primary = false)
        val expectedCurrentTaxCodeChange1 = TaxCodeChangeRecord("1000L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange2 = TaxCodeChangeRecord("185L", Cumulative, currentStartDate, currentEndDate, "Employer 2", Some(payrollNumberCurr), pensionIndicator = false, primary = false)

        val previousTaxCodeRecord1 = TaxCodeRecord("1185L", Cumulative, "Employer 1", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val previousTaxCodeRecord2 = TaxCodeRecord("BR", Cumulative, "Employer 2", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Secondary)
        val currentTaxCodeRecord1 = TaxCodeRecord("1000L", Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)
        val currentTaxCodeRecord2 = TaxCodeRecord("185L", Cumulative, "Employer 2", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Secondary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord1, previousTaxCodeRecord2, currentTaxCodeRecord1, currentTaxCodeRecord2)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq(expectedCurrentTaxCodeChange1, expectedCurrentTaxCodeChange2), Seq(expectedPreviousTaxCodeChange1, expectedPreviousTaxCodeChange2))

        Await.result(service.taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }

      "there has been a tax code change after Annual Coding for 1 employment to 2 employments" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
        val currentEndDate = TaxYearResolver.endOfCurrentTaxYear
        val previousStartDate = TaxYearResolver.startOfCurrentTaxYear
        val previousEndDate = currentStartDate.minusDays(1)
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val payrollNumberPrev = randomInt().toString
        val payrollNumberCurr = randomInt().toString

        val testNino = randomNino

        val expectedPreviousTaxCodeChange1 = TaxCodeChangeRecord("1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange1 = TaxCodeChangeRecord("1000L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange2 = TaxCodeChangeRecord("185L", Cumulative, currentStartDate, currentEndDate, "Employer 2", Some(payrollNumberCurr), pensionIndicator = false, primary = false)

        val previousTaxCodeRecord1 = TaxCodeRecord("1185L", Cumulative, "Employer 1", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val currentTaxCodeRecord1 = TaxCodeRecord("1000L", Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)
        val currentTaxCodeRecord2 = TaxCodeRecord("185L", Cumulative, "Employer 2", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Secondary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord1, currentTaxCodeRecord1, currentTaxCodeRecord2)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq(expectedCurrentTaxCodeChange1, expectedCurrentTaxCodeChange2), Seq(expectedPreviousTaxCodeChange1))

        Await.result(service.taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }

      "there has been a tax code change after Annual Coding for 2 employments to 1 employment" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
        val currentEndDate = TaxYearResolver.endOfCurrentTaxYear
        val previousStartDate = TaxYearResolver.startOfCurrentTaxYear
        val previousEndDate = currentStartDate.minusDays(1)
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val payrollNumberPrev = randomInt().toString
        val payrollNumberCurr = randomInt().toString

        val testNino = randomNino

        val expectedPreviousTaxCodeChange1 = TaxCodeChangeRecord("1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedPreviousTaxCodeChange2 = TaxCodeChangeRecord("BR", Cumulative, previousStartDate, previousEndDate, "Employer 2", Some(payrollNumberPrev), pensionIndicator = false, primary = false)
        val expectedCurrentTaxCodeChange1 = TaxCodeChangeRecord("1185L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)

        val previousTaxCodeRecord1 = TaxCodeRecord("1185L", Cumulative, "Employer 1", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val previousTaxCodeRecord2 = TaxCodeRecord("BR", Cumulative, "Employer 2", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Secondary)
        val currentTaxCodeRecord1 = TaxCodeRecord("1185L", Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord1, previousTaxCodeRecord2, currentTaxCodeRecord1)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq(expectedCurrentTaxCodeChange1), Seq(expectedPreviousTaxCodeChange1, expectedPreviousTaxCodeChange2))

        Await.result(service.taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }

      "there has been more than one daily tax code change in the year for 2 employments to 2 employments" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
        val currentEndDate = TaxYearResolver.endOfCurrentTaxYear
        val previousStartDate = TaxYearResolver.startOfCurrentTaxYear
        val previousEndDate = currentStartDate.minusDays(1)
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val payrollNumberPrev = randomInt().toString
        val payrollNumberCurr = randomInt().toString

        val testNino = randomNino

        val expectedPreviousTaxCodeChange1 = TaxCodeChangeRecord("1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedPreviousTaxCodeChange2 = TaxCodeChangeRecord("BR", Cumulative, previousStartDate, previousEndDate, "Employer 2", Some(payrollNumberPrev), pensionIndicator = false, primary = false)
        val expectedCurrentTaxCodeChange1 = TaxCodeChangeRecord("1000L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange2 = TaxCodeChangeRecord("185L", Cumulative, currentStartDate, currentEndDate, "Employer 2", Some(payrollNumberCurr), pensionIndicator = false, primary = false)

        val previousTaxCodeRecord1 = TaxCodeRecord("1185L", Cumulative, "Employer 1", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val previousTaxCodeRecord2 = TaxCodeRecord("BR", Cumulative, "Employer 2", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Secondary)
        val currentTaxCodeRecord1 = TaxCodeRecord("1000L", Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)
        val currentTaxCodeRecord2 = TaxCodeRecord("185L", Cumulative, "Employer 2", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Secondary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord1, previousTaxCodeRecord2, currentTaxCodeRecord1, currentTaxCodeRecord2)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq(expectedCurrentTaxCodeChange1, expectedCurrentTaxCodeChange2),
          Seq(expectedPreviousTaxCodeChange1, expectedPreviousTaxCodeChange2))

        Await.result(service.taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }

      "there has been more than one daily tax code change in the year for 1 employment to 2 employments" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
        val currentEndDate = TaxYearResolver.endOfCurrentTaxYear
        val previousStartDate = TaxYearResolver.startOfCurrentTaxYear
        val previousEndDate = currentStartDate.minusDays(1)
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val payrollNumberPrev = randomInt().toString
        val payrollNumberCurr = randomInt().toString

        val testNino = randomNino

        val expectedPreviousTaxCodeChange1 = TaxCodeChangeRecord("1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange1 = TaxCodeChangeRecord("1000L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange2 = TaxCodeChangeRecord("185L", Cumulative, currentStartDate, currentEndDate, "Employer 2", Some(payrollNumberCurr), pensionIndicator = false, primary = false)

        val previousTaxCodeRecord1 = TaxCodeRecord("1185L", Cumulative, "Employer 1", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val currentTaxCodeRecord1 = TaxCodeRecord("1000L", Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)
        val currentTaxCodeRecord2 = TaxCodeRecord("185L", Cumulative, "Employer 2", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Secondary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord1, currentTaxCodeRecord1, currentTaxCodeRecord2)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq(expectedCurrentTaxCodeChange1, expectedCurrentTaxCodeChange2), Seq(expectedPreviousTaxCodeChange1))

        Await.result(service.taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }

      "there has been more than one daily tax code change in the year for 2 employments to 1 employment" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
        val currentEndDate = TaxYearResolver.endOfCurrentTaxYear
        val previousStartDate = TaxYearResolver.startOfCurrentTaxYear
        val previousEndDate = currentStartDate.minusDays(1)
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val payrollNumberPrev = randomInt().toString
        val payrollNumberCurr = randomInt().toString

        val testNino = randomNino

        val expectedPreviousTaxCodeChange1 = TaxCodeChangeRecord("1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedPreviousTaxCodeChange2 = TaxCodeChangeRecord("BR", Cumulative, previousStartDate, previousEndDate, "Employer 2", Some(payrollNumberPrev), pensionIndicator = false, primary = false)
        val expectedCurrentTaxCodeChange1 = TaxCodeChangeRecord("1185L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)

        val previousTaxCodeRecord1 = TaxCodeRecord("1185L", Cumulative, "Employer 1", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val previousTaxCodeRecord2 = TaxCodeRecord("BR", Cumulative, "Employer 2", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Secondary)
        val currentTaxCodeRecord1 = TaxCodeRecord("1185L", Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord1, previousTaxCodeRecord2, currentTaxCodeRecord1)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq(expectedCurrentTaxCodeChange1), Seq(expectedPreviousTaxCodeChange1, expectedPreviousTaxCodeChange2))

        Await.result(service.taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }
    }

    "return an empty TaxCodeChange for single employments" when {

      "there has not been a tax code change in the year" in {
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val payrollNumberPrev = randomInt().toString

        val testNino = randomNino


        val previousTaxCodeRecord = TaxCodeRecord("1185L", Cumulative, "Employer 1", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Primary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq.empty[TaxCodeChangeRecord], Seq.empty[TaxCodeChangeRecord])

        Await.result(service.taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }

      "this is the first ever employment" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
        val payrollNumberCurr = randomInt().toString

        val testNino = randomNino

        val currentTaxCodeRecord = TaxCodeRecord("1185L", Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(currentTaxCodeRecord)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq.empty[TaxCodeChangeRecord], Seq.empty[TaxCodeChangeRecord])

        Await.result(service.taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }
    }

    "return an empty TaxCodeChange for multiple employments" when {

      "there has not been a tax code change in the year" in {
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val payrollNumberPrev = randomInt().toString

        val testNino = randomNino


        val previousTaxCodeRecord1 = TaxCodeRecord("1000L", Cumulative, "Employer 1", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val previousTaxCodeRecord2 = TaxCodeRecord("185L", Cumulative, "Employer 2", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Secondary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord1, previousTaxCodeRecord2)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq.empty[TaxCodeChangeRecord], Seq.empty[TaxCodeChangeRecord])

        Await.result(service.taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }

      "this is the first ever employment (2 employments starting together)" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
        val payrollNumberCurr1 = randomInt().toString
        val payrollNumberCurr2 = randomInt().toString

        val testNino = randomNino

        val currentTaxCodeRecord1 = TaxCodeRecord("1000L", Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr1), pensionIndicator = false, Primary)
        val currentTaxCodeRecord2 = TaxCodeRecord("185L", Cumulative, "Employer 2", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr2), pensionIndicator = false, Secondary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(currentTaxCodeRecord1, currentTaxCodeRecord2)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq.empty[TaxCodeChangeRecord], Seq.empty[TaxCodeChangeRecord])

        Await.result(service.taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }
    }

    "audit the TaxCodeChange" when {
      "there has been a valid tax code change" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val payrollNumberPrev = randomInt().toString
        val payrollNumberCurr = randomInt().toString
        val payrollNumberCurr2 = randomInt().toString

        val testNino = randomNino

        val previousTaxCodeRecord = TaxCodeRecord("1185L", Cumulative, "Employer 1", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val currentTaxCodeRecordPrimary = TaxCodeRecord("1000L", Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)
        val currentTaxCodeRecordSecondary = TaxCodeRecord("1001L", Cumulative, "Employer 2", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Secondary)
        val currentTaxCodeRecordSecondary2 = TaxCodeRecord("1002L", Cumulative, "Employer 2", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr2), pensionIndicator = false, Secondary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord, currentTaxCodeRecordPrimary, currentTaxCodeRecordSecondary, currentTaxCodeRecordSecondary2)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val mockAudit = mock[Auditor]

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector, mockAudit)

        Await.result(service.taxCodeChange(testNino), 5.seconds)

        val expectedDetailMap = Map(
          "nino" -> testNino.nino,
          "numberOfCurrentTaxCodes" -> "3",
          "numberOfPreviousTaxCodes" -> "1",
          "dataOfTaxCodeChange" -> currentStartDate.toString,
          "primaryCurrentTaxCode" -> "1000L",
          "secondaryCurrentTaxCodes" -> "1001L,1002L",
          "primaryPreviousTaxCode" -> "1185L",
          "secondaryPreviousTaxCodes" -> "",
          "primaryCurrentPayrollNumber" -> payrollNumberCurr,
          "secondaryCurrentPayrollNumbers" -> s"$payrollNumberCurr,$payrollNumberCurr2",
          "primaryPreviousPayrollNumber" -> payrollNumberPrev,
          "secondaryPreviousPayrollNumbers" -> ""
        )

        verify(mockAudit, times(1)).sendDataEvent(Matchers.eq("TaxCodeChange"), Matchers.eq(expectedDetailMap))(Matchers.any())
      }
    }
  }

  "taxCodeChangeAnalytics" should {

    "return true" when {
      "there has been a tax code change" in {

        val newCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
        val previousCodeDate = TaxYearResolver.startOfCurrentTaxYear
        val nino = randomNino

        val taxCodeHistory = TaxCodeHistory(
          nino = nino.withoutSuffix,
          taxCodeRecord = Seq(
            taxCodeRecord(dateOfCalculation = newCodeDate),
            taxCodeRecord(dateOfCalculation = previousCodeDate)
          )
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
        Await.result(service.hasTaxCodeChanged(nino), 5.seconds) mustEqual true

      }
    }

  }


  private def taxCodeRecord(taxCode: String = "1185L",
                            employerName: String = "Employer 1",
                            operatedTaxCode: Boolean = true,
                            dateOfCalculation: LocalDate,
                            payrollNumber: Option[String] = Some(randomInt().toString),
                            pensionIndicator: Boolean = false,
                            employmentType: String = Primary): TaxCodeRecord = {

    TaxCodeRecord(taxCode, Cumulative, employerName, operatedTaxCode, dateOfCalculation, payrollNumber, pensionIndicator, employmentType)
  }

  val taxCodeChangeConnector: TaxCodeChangeConnector = mock[TaxCodeChangeConnector]
  val defaultMockAudit = mock[Auditor]

  private def createService(mockConnector: TaxCodeChangeConnector = taxCodeChangeConnector, mockAuditor: Auditor = defaultMockAudit) = {
    new TaxCodeChangeServiceImpl(mockConnector, mockAuditor)
  }

  private def randomNino: Nino = new Generator(new Random).nextNino

  private def randomInt(maxDigits: Int = 5) = {
    import scala.math.pow
    val random = new Random
    random.nextInt(pow(10, maxDigits).toInt)
  }
}
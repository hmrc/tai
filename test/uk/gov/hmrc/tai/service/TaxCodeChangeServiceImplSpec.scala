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
import play.api.libs.json.{JsResultException, Json}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.TaxCodeChangeConnector
import uk.gov.hmrc.tai.model.api.{TaxCodeChange, TaxCodeRecordWithEndDate}
import uk.gov.hmrc.tai.model.des.{IncomeSource, TaxAccountDetails}
import uk.gov.hmrc.tai.model.domain.EmploymentIncome
import uk.gov.hmrc.tai.model.domain.income.{Live, TaxCodeIncome, Week1Month1BasisOperation}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.{TaxCodeHistory, TaxCodeRecord, des, _}
import uk.gov.hmrc.tai.util.factory.TaxCodeRecordFactory
import uk.gov.hmrc.tai.util.{RandomInt, TaxCodeHistoryConstants}
import uk.gov.hmrc.time.TaxYearResolver
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Random, Try}

class TaxCodeChangeServiceImplSpec extends PlaySpec with MockitoSugar with TaxCodeHistoryConstants {

  implicit val hc = HeaderCarrier()
  val baseTaxCodeIncome = TaxCodeIncome(EmploymentIncome, Some(1), BigDecimal(0),
    "EmploymentIncome", "1185L", "Employer1", Week1Month1BasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0))

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
              TaxCodeRecordFactory.create(dateOfCalculation = newCodeDate),
              TaxCodeRecordFactory.create(dateOfCalculation = previousCodeDate)
            )
          )
          val taxCodeIncomes = Seq(baseTaxCodeIncome.copy(taxCode = "1185L"))

          when(incomeService.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))
          when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

          val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector, auditor, incomeService)
          Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual true
        }

        "there has been a tax code change after Annual Coding where Annual coding was before start of tax year" in {
          val newCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
          val previousCodeDate = TaxYearResolver.startOfCurrentTaxYear.minusMonths(1)
          val testNino = randomNino

          val taxCodeHistory = TaxCodeHistory(
            nino = testNino.withoutSuffix,
            taxCodeRecord = Seq(
              TaxCodeRecordFactory.create(dateOfCalculation = newCodeDate),
              TaxCodeRecordFactory.create(taxCode = "1080L", dateOfCalculation = previousCodeDate)
            )
          )

          val taxCodeIncomes = Seq(baseTaxCodeIncome.copy(taxCode = "1185L"))

          when(incomeService.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))
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
              TaxCodeRecordFactory.create(taxCode ="1180L", dateOfCalculation = newCodeDate),
              TaxCodeRecordFactory.create(taxCode = "1080L", dateOfCalculation = previousCodeDate)
            )
          )
          val taxCodeIncomes = Seq(baseTaxCodeIncome.copy(taxCode = "1180L"))

          when(incomeService.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))
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
              TaxCodeRecordFactory.create(taxCode ="1180L", dateOfCalculation = newCodeDate),
              TaxCodeRecordFactory.create(taxCode ="1080L", dateOfCalculation = previousCodeDate),
              TaxCodeRecordFactory.create(taxCode ="1000L", dateOfCalculation = annualCodeDate)
            )
          )
          val taxCodeIncomes = Seq(baseTaxCodeIncome.copy(taxCode = "1180L"))

          when(incomeService.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))
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
          val payrollNumber1 = RandomInt().toString
          val payrollNumber2 = RandomInt().toString


          val taxCodeHistory = TaxCodeHistory(
            nino = testNino.withoutSuffix,
            taxCodeRecord = Seq(
              TaxCodeRecordFactory.create(dateOfCalculation = newCodeDate, payrollNumber = payrollNumber1),
              TaxCodeRecordFactory.create(dateOfCalculation = newCodeDate, payrollNumber = payrollNumber2, employmentType = Secondary),
              TaxCodeRecordFactory.create(dateOfCalculation = previousCodeDate, payrollNumber = payrollNumber1),
              TaxCodeRecordFactory.create(dateOfCalculation = previousCodeDate, payrollNumber = payrollNumber2, employmentType = Secondary)
            )
          )

          val taxCodeIncomes = Seq(baseTaxCodeIncome.copy(taxCode = "1185L"), baseTaxCodeIncome.copy(taxCode = "1185L"))

          when(incomeService.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))
          when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

          val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
          Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual true
        }

        "there has been more than one daily tax code change in the year for 1 employment to 2 employments" in {
          val newCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
          val previousCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(1)
          val testNino = randomNino
          val payrollNumber1 = RandomInt().toString
          val payrollNumber2 = RandomInt().toString

          val taxCodeRecords = Seq(
            TaxCodeRecordFactory.create(dateOfCalculation = newCodeDate, payrollNumber = payrollNumber1),
            TaxCodeRecordFactory.create(dateOfCalculation = newCodeDate, payrollNumber = payrollNumber2, employmentType = Secondary),
            TaxCodeRecordFactory.create(dateOfCalculation = previousCodeDate, payrollNumber = payrollNumber1)
          )

          val taxCodeHistory = TaxCodeHistory(nino = testNino.withoutSuffix, taxCodeRecord = taxCodeRecords)

          val taxCodeIncomes = Seq(baseTaxCodeIncome.copy(taxCode = "1185L"), baseTaxCodeIncome.copy(taxCode = "1185L"))

          when(incomeService.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))
          when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

          val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
          Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual true
        }

        "there has been more than one daily tax code change in the year for 2 employments to 1 employment" in {
          val newCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
          val previousCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(1)
          val testNino = randomNino
          val payrollNumber1 = RandomInt().toString
          val payrollNumber2 = RandomInt().toString

          val taxCodeRecords = Seq(
            TaxCodeRecordFactory.create(dateOfCalculation = newCodeDate, payrollNumber = payrollNumber1),
            TaxCodeRecordFactory.create(dateOfCalculation = previousCodeDate, payrollNumber = payrollNumber1),
            TaxCodeRecordFactory.create(dateOfCalculation = previousCodeDate, payrollNumber = payrollNumber2, employmentType = Secondary)
          )

          val taxCodeHistory = TaxCodeHistory(nino = testNino.withoutSuffix, taxCodeRecord = taxCodeRecords)
          val taxCodeIncomes = Seq(baseTaxCodeIncome.copy(taxCode = "1185L"))

          when(incomeService.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))
          when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

          val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
          Await.result(service.hasTaxCodeChanged(testNino), 5.seconds) mustEqual true
        }

        "there has been a tax code change after Annual Coding for 2 employments to 2 employments" in {
          val newCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
          val previousCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(1)
          val testNino = randomNino
          val payrollNumber1 = RandomInt().toString
          val payrollNumber2 = RandomInt().toString

          val taxCodeRecords = Seq(
            TaxCodeRecordFactory.create(dateOfCalculation = newCodeDate, payrollNumber = payrollNumber1),
            TaxCodeRecordFactory.create(dateOfCalculation = newCodeDate, payrollNumber = payrollNumber2, employmentType = Secondary),
            TaxCodeRecordFactory.create(dateOfCalculation = previousCodeDate, payrollNumber = payrollNumber1),
            TaxCodeRecordFactory.create(dateOfCalculation = previousCodeDate, payrollNumber = payrollNumber2, employmentType = Secondary)
          )

          val taxCodeHistory = TaxCodeHistory(nino = testNino.withoutSuffix, taxCodeRecord = taxCodeRecords)
          val taxCodeIncomes = Seq(baseTaxCodeIncome.copy(taxCode = "1185L"), baseTaxCodeIncome.copy(taxCode = "1185L"))

          when(incomeService.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))
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
            Seq(TaxCodeRecordFactory.create(dateOfCalculation = newCodeDate))
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
              TaxCodeRecordFactory.create(dateOfCalculation = newCodeDate, operatedTaxCode = false),
              TaxCodeRecordFactory.create(dateOfCalculation = previousCodeDate)
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
            Seq(TaxCodeRecordFactory.create(dateOfCalculation = annualCodeDate))
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
            Seq(TaxCodeRecordFactory.create(dateOfCalculation = annualCodeDate))
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
            TaxCodeRecordFactory.create(dateOfCalculation = dailyCodingDate),
            TaxCodeRecordFactory.create(dateOfCalculation = annualCodingDate)
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
              TaxCodeRecordFactory.create(dateOfCalculation = dailyCodeDate),
              TaxCodeRecordFactory.create(dateOfCalculation = dailyCodeDate, employmentType = Secondary)
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
              TaxCodeRecordFactory.create(employerName = "Employer A", dateOfCalculation = annualCodeDate),
              TaxCodeRecordFactory.create(employerName = "Employer B", dateOfCalculation = annualCodeDate, employmentType = Secondary)
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
        val payrollNumberPrev = RandomInt().toString
        val payrollNumberCurr = RandomInt().toString
        val taxCodeId1 = RandomInt(3)
        val taxCodeId2 = RandomInt(3)
        val testNino = randomNino

        val expectedPreviousTaxCodeChange = TaxCodeRecordWithEndDate("1185L", taxCodeId1, Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange = TaxCodeRecordWithEndDate("1000L", taxCodeId2, Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)

        val previousTaxCodeRecord = TaxCodeRecord("1185L", taxCodeId1, Cumulative, "Employer 1", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val currentTaxCodeRecord = TaxCodeRecord("1000L", taxCodeId2, Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord, currentTaxCodeRecord)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq(expectedCurrentTaxCodeChange), Seq(expectedPreviousTaxCodeChange))

        Await.result(createService().taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }

      "there has been more than one daily tax code change in the year" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
        val currentEndDate = TaxYearResolver.endOfCurrentTaxYear
        val previousStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(1)
        val previousEndDate = currentStartDate.minusDays(1)
        val payrollNumberPrev = RandomInt().toString
        val payrollNumberCurr = RandomInt().toString
        val taxCodeId1 = RandomInt(3)
        val taxCodeId2 = RandomInt(3)
        val testNino = randomNino

        val expectedPreviousTaxCodeChange = TaxCodeRecordWithEndDate("1185L", taxCodeId1, Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange = TaxCodeRecordWithEndDate("1000L", taxCodeId2, Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)

        val previousTaxCodeRecord = TaxCodeRecord("1185L", taxCodeId1, Cumulative, "Employer 1", operatedTaxCode = true, previousStartDate, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val currentTaxCodeRecord = TaxCodeRecord("1000L", taxCodeId2, Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)

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
        val payrollNumberPrev = RandomInt().toString
        val payrollNumberCurr = RandomInt().toString
        val payrollNumberNotOp = RandomInt().toString
        val taxCodeId1 = RandomInt(3)
        val taxCodeId2 = RandomInt(3)
        val testNino = randomNino

        val expectedPreviousTaxCodeChange = TaxCodeRecordWithEndDate("1185L", taxCodeId1, Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange = TaxCodeRecordWithEndDate("1000L", taxCodeId2, Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)

        val previousTaxCodeRecord = TaxCodeRecord("1185L", taxCodeId1, Cumulative, "Employer 1", operatedTaxCode = true, previousStartDate, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val currentTaxCodeRecord = TaxCodeRecord("1000L", taxCodeId2, Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)
        val nonOperatedCode = TaxCodeRecord("1185L", RandomInt(), Cumulative, "Employer 1", operatedTaxCode = false, nonOperatedStartDate, Some(payrollNumberNotOp), pensionIndicator = false, Primary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(nonOperatedCode, previousTaxCodeRecord, currentTaxCodeRecord)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq(expectedCurrentTaxCodeChange), Seq(expectedPreviousTaxCodeChange))

        Await.result(createService().taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }
    }

    "return a TaxCodeChange for multiple employments" when {

      "there has been a tax code change after Annual Coding for 2 employments to 2 employments" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
        val currentEndDate = TaxYearResolver.endOfCurrentTaxYear
        val previousStartDate = TaxYearResolver.startOfCurrentTaxYear
        val previousEndDate = currentStartDate.minusDays(1)
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val payrollNumberPrev = RandomInt().toString
        val payrollNumberCurr = RandomInt().toString
        val taxCodeId1 = RandomInt(3)
        val taxCodeId2 = RandomInt(3)
        val taxCodeId3 = RandomInt(3)
        val taxCodeId4 = RandomInt(3)
        val testNino = randomNino

        val expectedPreviousTaxCodeChange1 = TaxCodeRecordWithEndDate("1185L", taxCodeId1, Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedPreviousTaxCodeChange2 = TaxCodeRecordWithEndDate("BR", taxCodeId2, Cumulative, previousStartDate, previousEndDate, "Employer 2", Some(payrollNumberPrev), pensionIndicator = false, primary = false)
        val expectedCurrentTaxCodeChange1 = TaxCodeRecordWithEndDate("1000L", taxCodeId3, Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange2 = TaxCodeRecordWithEndDate("185L", taxCodeId4, Cumulative, currentStartDate, currentEndDate, "Employer 2", Some(payrollNumberCurr), pensionIndicator = false, primary = false)

        val previousTaxCodeRecord1 = TaxCodeRecord("1185L", taxCodeId1, Cumulative, "Employer 1", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val previousTaxCodeRecord2 = TaxCodeRecord("BR", taxCodeId2, Cumulative, "Employer 2", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Secondary)
        val currentTaxCodeRecord1 = TaxCodeRecord("1000L", taxCodeId3, Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)
        val currentTaxCodeRecord2 = TaxCodeRecord("185L", taxCodeId4, Cumulative, "Employer 2", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Secondary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord1, previousTaxCodeRecord2, currentTaxCodeRecord1, currentTaxCodeRecord2)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq(expectedCurrentTaxCodeChange1, expectedCurrentTaxCodeChange2), Seq(expectedPreviousTaxCodeChange1, expectedPreviousTaxCodeChange2))

        Await.result(createService().taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }

      "there has been a tax code change after Annual Coding for 1 employment to 2 employments" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
        val currentEndDate = TaxYearResolver.endOfCurrentTaxYear
        val previousStartDate = TaxYearResolver.startOfCurrentTaxYear
        val previousEndDate = currentStartDate.minusDays(1)
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val payrollNumberPrev = RandomInt().toString
        val payrollNumberCurr = RandomInt().toString
        val taxCodeId1 = RandomInt(3)
        val taxCodeId2 = RandomInt(3)
        val taxCodeId3 = RandomInt(3)
        val testNino = randomNino

        val expectedPreviousTaxCodeChange1 = TaxCodeRecordWithEndDate("1185L", taxCodeId1, Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange1 = TaxCodeRecordWithEndDate("1000L", taxCodeId2, Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange2 = TaxCodeRecordWithEndDate("185L", taxCodeId3, Cumulative, currentStartDate, currentEndDate, "Employer 2", Some(payrollNumberCurr), pensionIndicator = false, primary = false)

        val previousTaxCodeRecord1 = TaxCodeRecord("1185L", taxCodeId1, Cumulative, "Employer 1", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val currentTaxCodeRecord1 = TaxCodeRecord("1000L", taxCodeId2, Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)
        val currentTaxCodeRecord2 = TaxCodeRecord("185L", taxCodeId3, Cumulative, "Employer 2", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Secondary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord1, currentTaxCodeRecord1, currentTaxCodeRecord2)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq(expectedCurrentTaxCodeChange1, expectedCurrentTaxCodeChange2), Seq(expectedPreviousTaxCodeChange1))

        Await.result(createService().taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }

      "there has been a tax code change after Annual Coding for 2 employments to 1 employment" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
        val currentEndDate = TaxYearResolver.endOfCurrentTaxYear
        val previousStartDate = TaxYearResolver.startOfCurrentTaxYear
        val previousEndDate = currentStartDate.minusDays(1)
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val payrollNumberPrev = RandomInt().toString
        val payrollNumberCurr = RandomInt().toString
        val taxCodeId1 = RandomInt(3)
        val taxCodeId2 = RandomInt(3)
        val taxCodeId3 = RandomInt(3)
        val taxCodeId4 = RandomInt(3)
        val testNino = randomNino

        val expectedPreviousTaxCodeChange1 = TaxCodeRecordWithEndDate("1185L", taxCodeId1, Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedPreviousTaxCodeChange2 = TaxCodeRecordWithEndDate("BR", taxCodeId2, Cumulative, previousStartDate, previousEndDate, "Employer 2", Some(payrollNumberPrev), pensionIndicator = false, primary = false)
        val expectedCurrentTaxCodeChange1 = TaxCodeRecordWithEndDate("1185L", taxCodeId3, Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)

        val previousTaxCodeRecord1 = TaxCodeRecord("1185L", taxCodeId1, Cumulative, "Employer 1", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val previousTaxCodeRecord2 = TaxCodeRecord("BR", taxCodeId2, Cumulative, "Employer 2", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Secondary)
        val currentTaxCodeRecord1 = TaxCodeRecord("1185L", taxCodeId3, Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord1, previousTaxCodeRecord2, currentTaxCodeRecord1)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq(expectedCurrentTaxCodeChange1), Seq(expectedPreviousTaxCodeChange1, expectedPreviousTaxCodeChange2))

        Await.result(createService().taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }

      "there has been more than one daily tax code change in the year for 2 employments to 2 employments" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
        val currentEndDate = TaxYearResolver.endOfCurrentTaxYear
        val previousStartDate = TaxYearResolver.startOfCurrentTaxYear
        val previousEndDate = currentStartDate.minusDays(1)
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val payrollNumberPrev = RandomInt().toString
        val payrollNumberCurr = RandomInt().toString
        val taxCodeId1 = RandomInt(3)
        val taxCodeId2 = RandomInt(3)
        val taxCodeId3 = RandomInt(3)
        val taxCodeId4 = RandomInt(3)

        val testNino = randomNino

        val expectedPreviousTaxCodeChange1 = TaxCodeRecordWithEndDate("1185L", taxCodeId1, Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedPreviousTaxCodeChange2 = TaxCodeRecordWithEndDate("BR", taxCodeId2, Cumulative, previousStartDate, previousEndDate, "Employer 2", Some(payrollNumberPrev), pensionIndicator = false, primary = false)
        val expectedCurrentTaxCodeChange1 = TaxCodeRecordWithEndDate("1000L", taxCodeId3, Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange2 = TaxCodeRecordWithEndDate("185L", taxCodeId4, Cumulative, currentStartDate, currentEndDate, "Employer 2", Some(payrollNumberCurr), pensionIndicator = false, primary = false)

        val previousTaxCodeRecord1 = TaxCodeRecord("1185L", taxCodeId1, Cumulative, "Employer 1", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val previousTaxCodeRecord2 = TaxCodeRecord("BR", taxCodeId2, Cumulative, "Employer 2", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Secondary)
        val currentTaxCodeRecord1 = TaxCodeRecord("1000L", taxCodeId3, Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)
        val currentTaxCodeRecord2 = TaxCodeRecord("185L", taxCodeId4, Cumulative, "Employer 2", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Secondary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord1, previousTaxCodeRecord2, currentTaxCodeRecord1, currentTaxCodeRecord2)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq(expectedCurrentTaxCodeChange1, expectedCurrentTaxCodeChange2),
          Seq(expectedPreviousTaxCodeChange1, expectedPreviousTaxCodeChange2))

        Await.result(createService().taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }

      "there has been more than one daily tax code change in the year for 1 employment to 2 employments" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
        val currentEndDate = TaxYearResolver.endOfCurrentTaxYear
        val previousStartDate = TaxYearResolver.startOfCurrentTaxYear
        val previousEndDate = currentStartDate.minusDays(1)
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val payrollNumberPrev = RandomInt().toString
        val payrollNumberCurr = RandomInt().toString
        val taxCodeId1 = RandomInt(3)
        val taxCodeId2 = RandomInt(3)
        val taxCodeId3 = RandomInt(3)
        val testNino = randomNino

        val expectedPreviousTaxCodeChange1 = TaxCodeRecordWithEndDate("1185L", taxCodeId1, Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange1 = TaxCodeRecordWithEndDate("1000L", taxCodeId2, Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange2 = TaxCodeRecordWithEndDate("185L", taxCodeId3, Cumulative, currentStartDate, currentEndDate, "Employer 2", Some(payrollNumberCurr), pensionIndicator = false, primary = false)

        val previousTaxCodeRecord1 = TaxCodeRecord("1185L", taxCodeId1, Cumulative, "Employer 1", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val currentTaxCodeRecord1 = TaxCodeRecord("1000L", taxCodeId2, Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)
        val currentTaxCodeRecord2 = TaxCodeRecord("185L", taxCodeId3, Cumulative, "Employer 2", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Secondary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord1, currentTaxCodeRecord1, currentTaxCodeRecord2)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq(expectedCurrentTaxCodeChange1, expectedCurrentTaxCodeChange2), Seq(expectedPreviousTaxCodeChange1))

        Await.result(createService().taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }

      "there has been more than one daily tax code change in the year for 2 employments to 1 employment" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
        val currentEndDate = TaxYearResolver.endOfCurrentTaxYear
        val previousStartDate = TaxYearResolver.startOfCurrentTaxYear
        val previousEndDate = currentStartDate.minusDays(1)
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val payrollNumberPrev = RandomInt().toString
        val payrollNumberCurr = RandomInt().toString
        val taxCodeId1 = RandomInt(3)
        val taxCodeId2 = RandomInt(3)
        val taxCodeId3 = RandomInt(3)
        val testNino = randomNino

        val expectedPreviousTaxCodeChange1 = TaxCodeRecordWithEndDate("1185L", taxCodeId1, Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedPreviousTaxCodeChange2 = TaxCodeRecordWithEndDate("BR", taxCodeId2, Cumulative, previousStartDate, previousEndDate, "Employer 2", Some(payrollNumberPrev), pensionIndicator = false, primary = false)
        val expectedCurrentTaxCodeChange1 = TaxCodeRecordWithEndDate("1185L", taxCodeId3, Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)

        val previousTaxCodeRecord1 = TaxCodeRecord("1185L", taxCodeId1, Cumulative, "Employer 1", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val previousTaxCodeRecord2 = TaxCodeRecord("BR", taxCodeId2, Cumulative, "Employer 2", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Secondary)
        val currentTaxCodeRecord1 = TaxCodeRecord("1185L", taxCodeId3, Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord1, previousTaxCodeRecord2, currentTaxCodeRecord1)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq(expectedCurrentTaxCodeChange1), Seq(expectedPreviousTaxCodeChange1, expectedPreviousTaxCodeChange2))

        Await.result(createService().taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }
    }

    "return one TaxCodeChange(record) for single employments" when {

      "there has not been a tax code change in the year" in {
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val nino = randomNino
        val taxCodeRecord = TaxCodeRecord("1185L", 1, Cumulative, "Employer 1", true, previousStartDateInPrevYear, Some(RandomInt().toString), false, Primary)
        val taxCodeHistory = TaxCodeHistory(nino.withoutSuffix, Seq(taxCodeRecord))

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val taxCodeChangeRecord = TaxCodeRecordWithEndDate(
          taxCodeRecord.taxCode, taxCodeRecord.taxCodeId, taxCodeRecord.basisOfOperation, previousStartDateInPrevYear, TaxYearResolver.endOfCurrentTaxYear, taxCodeRecord.employerName,
          taxCodeRecord.payrollNumber, taxCodeRecord.pensionIndicator, taxCodeRecord.isPrimary)

        val expectedResult = TaxCodeChange(Seq(taxCodeChangeRecord), Seq.empty[TaxCodeRecordWithEndDate])

        Await.result(service.taxCodeChange(nino), 5.seconds) mustEqual expectedResult
      }

      "this is the first ever employment" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)

        val nino = randomNino
        val taxCodeRecord = TaxCodeRecord("1185L", 1, Cumulative, "Employer 1", true, currentStartDate, Some(RandomInt().toString), false, Primary)
        val taxCodeHistory = TaxCodeHistory(nino.withoutSuffix, Seq(taxCodeRecord))

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val taxCodeChangeRecord = TaxCodeRecordWithEndDate(
          taxCodeRecord.taxCode, taxCodeRecord.taxCodeId, taxCodeRecord.basisOfOperation, currentStartDate, TaxYearResolver.endOfCurrentTaxYear, taxCodeRecord.employerName,
          taxCodeRecord.payrollNumber, taxCodeRecord.pensionIndicator, taxCodeRecord.isPrimary)

        val expectedResult = TaxCodeChange(Seq(taxCodeChangeRecord), Seq.empty[TaxCodeRecordWithEndDate])

        Await.result(service.taxCodeChange(nino), 5.seconds) mustEqual expectedResult
      }
    }

    "return an empty TaxCodeChange for multiple employments" when {

      "there has not been a tax code change in the year" in {
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val payrollNumberPrev = RandomInt().toString

        val testNino = randomNino


        val previousTaxCodeRecord1 = TaxCodeRecord("1000L", RandomInt(), Cumulative, "Employer 1", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val previousTaxCodeRecord2 = TaxCodeRecord("185L", RandomInt(), Cumulative, "Employer 2", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Secondary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord1, previousTaxCodeRecord2)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq.empty[TaxCodeRecordWithEndDate], Seq.empty[TaxCodeRecordWithEndDate])

        Await.result(createService().taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }

      "this is the first ever employment (2 employments starting together)" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
        val payrollNumberCurr1 = RandomInt().toString
        val payrollNumberCurr2 = RandomInt().toString

        val testNino = randomNino

        val currentTaxCodeRecord1 = TaxCodeRecordFactory.create(dateOfCalculation = currentStartDate, payrollNumber = payrollNumberCurr1)
        val currentTaxCodeRecord2 = TaxCodeRecordFactory.create(dateOfCalculation = currentStartDate, payrollNumber = payrollNumberCurr2, employmentType = Secondary)

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(currentTaxCodeRecord1, currentTaxCodeRecord2)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq.empty[TaxCodeRecordWithEndDate], Seq.empty[TaxCodeRecordWithEndDate])

        Await.result(createService().taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }
    }

    "audit the TaxCodeChange" when {
      "there has been a valid tax code change" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val payrollNumberPrev = RandomInt().toString
        val payrollNumberCurr = RandomInt().toString
        val payrollNumberCurr2 = RandomInt().toString

        val testNino = randomNino

        val previousTaxCodeRecord = TaxCodeRecord("1185L", 1, Cumulative, "Employer 1", operatedTaxCode = true, previousStartDateInPrevYear, Some(payrollNumberPrev), pensionIndicator = false, Primary)
        val currentTaxCodeRecordPrimary = TaxCodeRecord("1000L", 2, Cumulative, "Employer 1", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Primary)
        val currentTaxCodeRecordSecondary = TaxCodeRecord("1001L", 3, Cumulative, "Employer 2", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr), pensionIndicator = false, Secondary)
        val currentTaxCodeRecordSecondary2 = TaxCodeRecord("1002L", 4, Cumulative, "Employer 2", operatedTaxCode = true, currentStartDate, Some(payrollNumberCurr2), pensionIndicator = false, Secondary)

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

  "taxCodeMismatch" should {

    val newCodeDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
    val previousCodeDate = TaxYearResolver.startOfCurrentTaxYear
    val payrollNumber1 = RandomInt().toString
    val payrollNumber2 = RandomInt().toString
    val nino = randomNino

    "return false and list of confirmed and unconfirmed tax codes" when {
      "the one tax code returned from tax account record, matches the one returned from tax code list" in {

        val taxCodeHistory = TaxCodeHistory(
          nino.withoutSuffix,
          taxCodeRecord = Seq(
            taxCodeRecord(dateOfCalculation = newCodeDate),
            taxCodeRecord(dateOfCalculation = previousCodeDate))
        )

        val taxCodeIncomes = Seq(baseTaxCodeIncome.copy(taxCode = "1185L"))

        when(incomeService.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))
        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val expectedResult = TaxCodeMismatch(false, Seq("1185L"), Seq("1185L"))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector, auditor, incomeService)
        Await.result(service.taxCodeMismatch(nino), 5.seconds) mustEqual expectedResult
      }

      "tax codes returned from tax account record, match the ones returned from tax code list" in {

        val taxCodeHistory = TaxCodeHistory(
          nino = nino.withoutSuffix,
          taxCodeRecord = Seq(
            taxCodeRecord(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1), taxCode = "1155L"),
            taxCodeRecord(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1), taxCode = "1175L"),
            taxCodeRecord(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1), taxCode = "1195L"),
            taxCodeRecord(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber2), employmentType = Secondary),
            taxCodeRecord(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber1)),
            taxCodeRecord(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber2), employmentType = Secondary)
          )
        )

        val taxCodeIncomes = Seq(
          baseTaxCodeIncome.copy(taxCode = "1185L"),
          baseTaxCodeIncome.copy(taxCode = "1155L"),
          baseTaxCodeIncome.copy(taxCode = "1175L"),
          baseTaxCodeIncome.copy(taxCode = "1195L")
        )

        when(incomeService.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))
        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val confirmedTaxCodes = Seq("1185L","1155L","1175L","1195L").sorted
        val unconfirmedTaxCodes = taxCodeIncomes.map(_.taxCode).sorted

        val expectedResult = TaxCodeMismatch(false, unconfirmedTaxCodes , confirmedTaxCodes)

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
        Await.result(service.taxCodeMismatch(nino), 5.seconds) mustEqual expectedResult
      }
    }

    "return true and list of confirmed and unconfirmed tax codes" when {

      "tax code change returns an empty current tax code " +
        "(tax code history will never return empty since an exception is thrown at trying to parse the api response)" in {

        val taxCodeHistory = TaxCodeHistory(nino.withoutSuffix, Seq())
        val taxCodeIncomes = Seq(baseTaxCodeIncome.copy(taxCode = "1185L"))

        when(incomeService.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))
        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val expectedResult = TaxCodeMismatch(true, Seq("1185L"), Seq())

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector, auditor, incomeService)
        Await.result(service.taxCodeMismatch(nino), 5.seconds) mustEqual expectedResult
      }

      "the one tax code returned from tax account record, does not match the one returned from tax code list" in {

        val taxCodeHistory = TaxCodeHistory(
          nino = nino.withoutSuffix,
          taxCodeRecord = Seq(
            taxCodeRecord(dateOfCalculation = newCodeDate),
            taxCodeRecord(dateOfCalculation = previousCodeDate)))

        val taxCodeIncomes = Seq(TaxCodeIncome(EmploymentIncome, Some(1), BigDecimal(0),
          "EmploymentIncome", "1000L", "Employer1", Week1Month1BasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0)))

        when(incomeService.taxCodeIncomes(any(),any())(any())).thenReturn(Future.successful(taxCodeIncomes))
        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val expectedResult = TaxCodeMismatch(true, taxCodeIncomes.map(_.taxCode), Seq("1185L"))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
        Await.result(service.taxCodeMismatch(nino), 5.seconds) mustEqual expectedResult
      }

      "tax codes returned from tax account record, do not match the ones returned from tax code list" in {

        val taxCodeHistory = TaxCodeHistory(
          nino = nino.withoutSuffix,
          taxCodeRecord = Seq(
            taxCodeRecord(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1), taxCode = "1155L"),
            taxCodeRecord(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1), taxCode = "1175L"),
            taxCodeRecord(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1), taxCode = "1195L"),
            taxCodeRecord(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber2), employmentType = Secondary),
            taxCodeRecord(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber1)),
            taxCodeRecord(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber2), employmentType = Secondary)
          )
        )

        val taxCodeIncomes = Seq(
          baseTaxCodeIncome.copy(taxCode = "1155L"),
          baseTaxCodeIncome.copy(taxCode = "1175L"),
          baseTaxCodeIncome.copy(taxCode = "1195L")
        )

        when(incomeService.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))
        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val confirmedTaxCodes = Seq("1185L","1155L","1175L","1195L").sorted
        val unconfirmedTaxCodes = taxCodeIncomes.map(_.taxCode).sorted

        val expectedResult = TaxCodeMismatch(true, unconfirmedTaxCodes , confirmedTaxCodes)

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)
        Await.result(service.taxCodeMismatch(nino), 5.seconds) mustEqual expectedResult
      }

    }

    "return a bad request exception response and empty model when tax code history fails" in {

      val taxCodeIncomes = Seq(
        baseTaxCodeIncome.copy(taxCode = "1155L"),
        baseTaxCodeIncome.copy(taxCode = "1175L"),
        baseTaxCodeIncome.copy(taxCode = "1195L")
      )
      when(incomeService.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))
      when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.failed(new RuntimeException("Mismatch Problem")))

      val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

      val ex = the[BadRequestException] thrownBy Await.result(service.taxCodeMismatch(nino), 5.seconds)
      ex.getMessage must include("Mismatch Problem")
    }

    "return default error response and empty model when tax code income fails" in {

      val taxCodeHistory = TaxCodeHistory(
        nino = nino.withoutSuffix,
        taxCodeRecord = Seq(
          taxCodeRecord(dateOfCalculation = newCodeDate),
          taxCodeRecord(dateOfCalculation = previousCodeDate)))

      when(incomeService.taxCodeIncomes(any(), any())(any())).thenReturn(Future.failed(new RuntimeException("Runtime")))
      when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

      val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

      val ex = the[BadRequestException] thrownBy Await.result(service.taxCodeMismatch(nino), 5.seconds)
      ex.getMessage must include("Runtime")
    }

  }

  "taxCodeChangeIabdComparison" should {

    "return a list of Iabd for a nino" in {
      val testNino = randomNino


      // setup for API 1
      val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(2)
      val previousStartDate = TaxYearResolver.startOfCurrentTaxYear.plusDays(1)

      val taxCodeIdPrevious = RandomInt(3)
      val taxCodeIdCurrent = RandomInt(3)

      val taxCodeHistory = TaxCodeHistory(
        testNino.withoutSuffix,
        Seq(
          TaxCodeRecordFactory.create(taxCodeId = taxCodeIdPrevious, dateOfCalculation = previousStartDate),
          TaxCodeRecordFactory.create(taxCodeId = taxCodeIdCurrent, dateOfCalculation = currentStartDate)
        )
      )

      when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

      // setup for API 2 - first tax code id
      val iabdSummaryAllowancePrev = des.IabdSummary(8105, 118,Some("Personal Allowance (PA)"), Some(1), None, None)
      val allowancePrev = des.Allowance("personal allowance", 8105, 11, List(iabdSummaryAllowancePrev), 8105)

      val iabdSummaryDeductionPrev = des.IabdSummary(105, 18, Some("deduction"), Some(1), None, None)
      val deductionPrev = des.Deduction("personal allowance", 105, 18, List(iabdSummaryDeductionPrev), 105)

      val incomePrev = IncomeSource(1,1,1,754, "employmentPayeRef", false, false, false, "incomeSourceName", "1035L", 1,
        None, 0, 0, 0, 0, 0, List(allowancePrev), List(deductionPrev), Json.obj())

      val taxAccountHistoryResponsePrevious = TaxAccountDetails(taxCodeIdPrevious, "02/08/2018", testNino, false, TaxYear(2018),
        6, 1, None, None, 16956, 1, 0, List(incomePrev))

      when(taxCodeChangeConnector.taxAccountHistory(testNino, taxCodeIdPrevious)).thenReturn(Future.successful(Try(taxAccountHistoryResponsePrevious)))


      // setup for API 2 - first tax code id
      val iabdSummaryAllowanceCurr = des.IabdSummary(8105, 118,Some("Personal Allowance (PA)"), Some(3), None, None)
      val allowanceCurr = des.Allowance("personal allowance", 8105, 11, List(iabdSummaryAllowanceCurr), 8105)

      val iabdSummaryDeductionCurr = des.IabdSummary(105, 18, Some("deduction"), Some(3), None, None)
      val deduction2 = des.Deduction("personal allowance", 105, 18, List(iabdSummaryDeductionCurr), 105)

      val incomeCurr = IncomeSource(3,1,1,754, "employmentPayeRef", false, false, false, "incomeSourceName", "1035L", 1,
        None, 0, 0, 0, 0, 0, List(allowanceCurr), List(deduction2), Json.obj())

      val taxAccountHistoryResponseCurrent = TaxAccountDetails(taxCodeIdCurrent, "02/08/2018", testNino, false, TaxYear(2018),
        6, 1, None, None, 16956, 1, 0, List(incomeCurr))

      when(taxCodeChangeConnector.taxAccountHistory(testNino, taxCodeIdCurrent)).thenReturn(Future.successful(Try(taxAccountHistoryResponseCurrent)))

      // Expected response

      val expectedIabds: TaxCodeComparison = TaxCodeComparison(
        previous = TaxCodeComponent(
          allowances = Seq(iabdSummaryAllowancePrev),
          deductions = Seq(iabdSummaryDeductionPrev)
        ),
        TaxCodeComponent(
          allowances = Seq(iabdSummaryAllowanceCurr),
          deductions = Seq(iabdSummaryDeductionCurr)
        ))


      Await.result(createService().iabdComparison(testNino),5.seconds) mustBe expectedIabds

    }
  }

  private def taxCodeRecord(taxCode: String = "1185L",
                            employerName: String = "Employer 1",
                            operatedTaxCode: Boolean = true,
                            dateOfCalculation: LocalDate,
                            payrollNumber: Option[String] = Some(RandomInt().toString),
                            pensionIndicator: Boolean = false,
                            employmentType: String = Primary): TaxCodeRecord = {

    TaxCodeRecord(taxCode, 1, Cumulative, employerName, operatedTaxCode, dateOfCalculation, payrollNumber, pensionIndicator, employmentType)
  }

  val taxCodeChangeConnector: TaxCodeChangeConnector = mock[TaxCodeChangeConnector]
  val auditor = mock[Auditor]
  val incomeService: IncomeService = mock[IncomeService]

  private def createService(
                             mockConnector: TaxCodeChangeConnector = taxCodeChangeConnector,
                             mockAuditor: Auditor = auditor,
                             incomeService: IncomeService = incomeService) = {

    new TaxCodeChangeServiceImpl(mockConnector, mockAuditor, incomeService)
  }

  private def randomNino: Nino = new Generator(new Random).nextNino

}
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
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.TaxCodeChangeConnector
import uk.gov.hmrc.tai.factory.TaxCodeRecordFactory
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.model.api.{TaxCodeChange, TaxCodeRecordWithEndDate}
import uk.gov.hmrc.tai.model.domain.EmploymentIncome
import uk.gov.hmrc.tai.model.domain.income.{Live, TaxCodeIncome, Week1Month1BasisOperation}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.TaxCodeHistoryConstants
import uk.gov.hmrc.time.TaxYearResolver

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

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
              TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = newCodeDate),
              TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousCodeDate)
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
              TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = newCodeDate),
              TaxCodeRecordFactory.createPrimaryEmployment(taxCode = "1080L", dateOfCalculation = previousCodeDate)
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
              TaxCodeRecordFactory.createPrimaryEmployment(taxCode ="1180L", dateOfCalculation = newCodeDate),
              TaxCodeRecordFactory.createPrimaryEmployment(taxCode = "1080L", dateOfCalculation = previousCodeDate)
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
              TaxCodeRecordFactory.createPrimaryEmployment(taxCode ="1180L", dateOfCalculation = newCodeDate),
              TaxCodeRecordFactory.createPrimaryEmployment(taxCode ="1080L", dateOfCalculation = previousCodeDate),
              TaxCodeRecordFactory.createPrimaryEmployment(taxCode ="1000L", dateOfCalculation = annualCodeDate)
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
          val payrollNumber1 = randomInt().toString
          val payrollNumber2 = randomInt().toString


          val taxCodeHistory = TaxCodeHistory(
            nino = testNino.withoutSuffix,
            taxCodeRecord = Seq(
              TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1)),
              TaxCodeRecordFactory.createSecondaryEmployment(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber2)),
              TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber1)),
              TaxCodeRecordFactory.createSecondaryEmployment(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber2))
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
          val payrollNumber1 = randomInt().toString
          val payrollNumber2 = randomInt().toString

          val taxCodeRecords = Seq(
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1)),
            TaxCodeRecordFactory.createSecondaryEmployment(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber2)),
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber1))
          )

          val taxCodeHistory = TaxCodeHistory(nino = testNino.withoutSuffix, taxCodeRecords = taxCodeRecords)

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
          val payrollNumber1 = randomInt().toString
          val payrollNumber2 = randomInt().toString

          val taxCodeRecords = Seq(
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1)),
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber1)),
            TaxCodeRecordFactory.createSecondaryEmployment(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber2))
          )

          val taxCodeHistory = TaxCodeHistory(nino = testNino.withoutSuffix, taxCodeRecords = taxCodeRecords)
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
          val payrollNumber1 = randomInt().toString
          val payrollNumber2 = randomInt().toString

          val taxCodeRecords = Seq(
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1)),
            TaxCodeRecordFactory.createSecondaryEmployment(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber2)),
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber1)),
            TaxCodeRecordFactory.createSecondaryEmployment(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber2))
          )

          val taxCodeHistory = TaxCodeHistory(nino = testNino.withoutSuffix, taxCodeRecords = taxCodeRecords)
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
            Seq(TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = newCodeDate))
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
              TaxCodeRecordFactory.createNonOperatedEmployment(dateOfCalculation = newCodeDate),
              TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousCodeDate)
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
            Seq(TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = annualCodeDate))
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
            Seq(TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = annualCodeDate))
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
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = dailyCodingDate),
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = annualCodingDate)
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
              TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = dailyCodeDate),
              TaxCodeRecordFactory.createSecondaryEmployment(dateOfCalculation = dailyCodeDate)
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
              TaxCodeRecordFactory.createPrimaryEmployment(employerName = "Employer A", dateOfCalculation = annualCodeDate),
              TaxCodeRecordFactory.createSecondaryEmployment(employerName = "Employer B", dateOfCalculation = annualCodeDate)
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

        val expectedPreviousTaxCodeChange = TaxCodeRecordWithEndDate("1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange = TaxCodeRecordWithEndDate("1000L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)

        val previousTaxCodeRecord = TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousStartDateInPrevYear, payrollNumber = Some(payrollNumberPrev))
        val currentTaxCodeRecord = TaxCodeRecordFactory.createPrimaryEmployment(taxCode = "1000L", dateOfCalculation = currentStartDate, payrollNumber = Some(payrollNumberCurr))

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

        val expectedPreviousTaxCodeChange = TaxCodeRecordWithEndDate("1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange = TaxCodeRecordWithEndDate("1000L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)

        val previousTaxCodeRecord = TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousStartDate, payrollNumber = Some(payrollNumberPrev))
        val currentTaxCodeRecord = TaxCodeRecordFactory.createPrimaryEmployment(taxCode = "1000L", dateOfCalculation = currentStartDate, payrollNumber = Some(payrollNumberCurr))

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

        val expectedPreviousTaxCodeChange = TaxCodeRecordWithEndDate("1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange = TaxCodeRecordWithEndDate("1000L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)

        val previousTaxCodeRecord = TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousStartDate, payrollNumber = Some(payrollNumberPrev))
        val currentTaxCodeRecord = TaxCodeRecordFactory.createPrimaryEmployment(taxCode = "1000L", dateOfCalculation = currentStartDate, payrollNumber = Some(payrollNumberCurr))
        val nonOperatedCode = TaxCodeRecordFactory.createNonOperatedEmployment(dateOfCalculation = nonOperatedStartDate, payrollNumber = Some(payrollNumberNotOp))

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

        val expectedPreviousTaxCodeChange1 = TaxCodeRecordWithEndDate("1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedPreviousTaxCodeChange2 = TaxCodeRecordWithEndDate("BR", Cumulative, previousStartDate, previousEndDate, "Employer 2", Some(payrollNumberPrev), pensionIndicator = false, primary = false)
        val expectedCurrentTaxCodeChange1 = TaxCodeRecordWithEndDate("1000L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange2 = TaxCodeRecordWithEndDate("185L", Cumulative, currentStartDate, currentEndDate, "Employer 2", Some(payrollNumberCurr), pensionIndicator = false, primary = false)


        val previousTaxCodeRecord1 = TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousStartDateInPrevYear, payrollNumber = Some(payrollNumberPrev))
        val previousTaxCodeRecord2 = TaxCodeRecordFactory.createSecondaryEmployment(taxCode = "BR", employerName = "Employer 2", dateOfCalculation = previousStartDateInPrevYear, payrollNumber = Some(payrollNumberPrev))

        val currentTaxCodeRecord1 = TaxCodeRecordFactory.createPrimaryEmployment(taxCode = "1000L", dateOfCalculation = currentStartDate, payrollNumber = Some(payrollNumberCurr))
        val currentTaxCodeRecord2 = TaxCodeRecordFactory.createSecondaryEmployment(taxCode = "185L", employerName = "Employer 2", dateOfCalculation = currentStartDate, payrollNumber = Some(payrollNumberCurr))

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

        val expectedPreviousTaxCodeChange1 = TaxCodeRecordWithEndDate("1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange1 = TaxCodeRecordWithEndDate("1000L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange2 = TaxCodeRecordWithEndDate("185L", Cumulative, currentStartDate, currentEndDate, "Employer 2", Some(payrollNumberCurr), pensionIndicator = false, primary = false)

        val previousTaxCodeRecord1 = TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousStartDateInPrevYear, payrollNumber = Some(payrollNumberPrev))
        val currentTaxCodeRecord1 = TaxCodeRecordFactory.createPrimaryEmployment(taxCode = "1000L", dateOfCalculation = currentStartDate, payrollNumber = Some(payrollNumberCurr))
        val currentTaxCodeRecord2 = TaxCodeRecordFactory.createSecondaryEmployment(taxCode = "185L", employerName = "Employer 2", dateOfCalculation = currentStartDate, payrollNumber = Some(payrollNumberCurr))

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

        val expectedPreviousTaxCodeChange1 = TaxCodeRecordWithEndDate("1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedPreviousTaxCodeChange2 = TaxCodeRecordWithEndDate("BR", Cumulative, previousStartDate, previousEndDate, "Employer 2", Some(payrollNumberPrev), pensionIndicator = false, primary = false)
        val expectedCurrentTaxCodeChange1 = TaxCodeRecordWithEndDate("1185L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)

        val previousTaxCodeRecord1 = TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousStartDateInPrevYear, payrollNumber = Some(payrollNumberPrev))
        val previousTaxCodeRecord2 = TaxCodeRecordFactory.createSecondaryEmployment(taxCode = "BR", employerName = "Employer 2", dateOfCalculation = previousStartDateInPrevYear, payrollNumber = Some(payrollNumberPrev))
        val currentTaxCodeRecord1 = TaxCodeRecordFactory.createPrimaryEmployment(taxCode = "1185L", dateOfCalculation = currentStartDate, payrollNumber = Some(payrollNumberCurr))

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

        val expectedPreviousTaxCodeChange1 = TaxCodeRecordWithEndDate("1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedPreviousTaxCodeChange2 = TaxCodeRecordWithEndDate("BR", Cumulative, previousStartDate, previousEndDate, "Employer 2", Some(payrollNumberPrev), pensionIndicator = false, primary = false)
        val expectedCurrentTaxCodeChange1 = TaxCodeRecordWithEndDate("1000L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange2 = TaxCodeRecordWithEndDate("185L", Cumulative, currentStartDate, currentEndDate, "Employer 2", Some(payrollNumberCurr), pensionIndicator = false, primary = false)

        val previousTaxCodeRecord1 = TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousStartDateInPrevYear, payrollNumber = Some(payrollNumberPrev))
        val previousTaxCodeRecord2 = TaxCodeRecordFactory.createSecondaryEmployment(taxCode = "BR", employerName = "Employer 2", dateOfCalculation = previousStartDateInPrevYear, payrollNumber = Some(payrollNumberPrev))
        val currentTaxCodeRecord1 = TaxCodeRecordFactory.createPrimaryEmployment(taxCode = "1000L", dateOfCalculation = currentStartDate, payrollNumber = Some(payrollNumberCurr))
        val currentTaxCodeRecord2 = TaxCodeRecordFactory.createSecondaryEmployment(taxCode = "185L", employerName = "Employer 2", dateOfCalculation = currentStartDate, payrollNumber = Some(payrollNumberCurr))

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

        val expectedPreviousTaxCodeChange1 = TaxCodeRecordWithEndDate("1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange1 = TaxCodeRecordWithEndDate("1000L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)
        val expectedCurrentTaxCodeChange2 = TaxCodeRecordWithEndDate("185L", Cumulative, currentStartDate, currentEndDate, "Employer 2", Some(payrollNumberCurr), pensionIndicator = false, primary = false)

        val previousTaxCodeRecord1 = TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousStartDateInPrevYear, payrollNumber = Some(payrollNumberPrev))
        val currentTaxCodeRecord1 = TaxCodeRecordFactory.createPrimaryEmployment(taxCode = "1000L", dateOfCalculation = currentStartDate, payrollNumber = Some(payrollNumberCurr))
        val currentTaxCodeRecord2 = TaxCodeRecordFactory.createSecondaryEmployment(taxCode = "185L", employerName = "Employer 2", dateOfCalculation = currentStartDate, payrollNumber = Some(payrollNumberCurr))

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

        val expectedPreviousTaxCodeChange1 = TaxCodeRecordWithEndDate("1185L", Cumulative, previousStartDate, previousEndDate, "Employer 1", Some(payrollNumberPrev), pensionIndicator = false, primary = true)
        val expectedPreviousTaxCodeChange2 = TaxCodeRecordWithEndDate("BR", Cumulative, previousStartDate, previousEndDate, "Employer 2", Some(payrollNumberPrev), pensionIndicator = false, primary = false)
        val expectedCurrentTaxCodeChange1 = TaxCodeRecordWithEndDate("1185L", Cumulative, currentStartDate, currentEndDate, "Employer 1", Some(payrollNumberCurr), pensionIndicator = false, primary = true)

        val previousTaxCodeRecord1 = TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousStartDateInPrevYear, payrollNumber = Some(payrollNumberPrev))
        val previousTaxCodeRecord2 = TaxCodeRecordFactory.createSecondaryEmployment(taxCode = "BR", employerName = "Employer 2", dateOfCalculation = previousStartDateInPrevYear, payrollNumber = Some(payrollNumberPrev))
        val currentTaxCodeRecord1 = TaxCodeRecordFactory.createPrimaryEmployment(taxCode = "1185L", dateOfCalculation = currentStartDate, payrollNumber = Some(payrollNumberCurr))

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

    "return one TaxCodeChange(record) for single employments" when {

      "there has not been a tax code change in the year" in {
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val nino = randomNino
        val taxCodeRecord = TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousStartDateInPrevYear)
        val taxCodeHistory = TaxCodeHistory(nino.withoutSuffix, Seq(taxCodeRecord))

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val taxCodeChangeRecord = TaxCodeRecordWithEndDate(
          taxCodeRecord.taxCode, taxCodeRecord.basisOfOperation, previousStartDateInPrevYear, TaxYearResolver.endOfCurrentTaxYear, taxCodeRecord.employerName,
          taxCodeRecord.payrollNumber, taxCodeRecord.pensionIndicator, taxCodeRecord.isPrimary)

        val expectedResult = TaxCodeChange(Seq(taxCodeChangeRecord), Seq.empty[TaxCodeRecordWithEndDate])

        Await.result(service.taxCodeChange(nino), 5.seconds) mustEqual expectedResult
      }

      "this is the first ever employment" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)

        val nino = randomNino
        val taxCodeRecord = TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = currentStartDate)

        val taxCodeHistory = TaxCodeHistory(nino.withoutSuffix, Seq(taxCodeRecord))

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val taxCodeChangeRecord = TaxCodeRecordWithEndDate(
          taxCodeRecord.taxCode, taxCodeRecord.basisOfOperation, currentStartDate, TaxYearResolver.endOfCurrentTaxYear, taxCodeRecord.employerName,
          taxCodeRecord.payrollNumber, taxCodeRecord.pensionIndicator, taxCodeRecord.isPrimary)

        val expectedResult = TaxCodeChange(Seq(taxCodeChangeRecord), Seq.empty[TaxCodeRecordWithEndDate])

        Await.result(service.taxCodeChange(nino), 5.seconds) mustEqual expectedResult
      }
    }

    "return an empty TaxCodeChange for multiple employments" when {

      "there has not been a tax code change in the year" in {
        val previousStartDateInPrevYear = TaxYearResolver.startOfCurrentTaxYear.minusDays(2)
        val payrollNumberPrev = randomInt().toString

        val testNino = randomNino


        val previousTaxCodeRecord1 = TaxCodeRecordFactory.createPrimaryEmployment(taxCode = "1000L", dateOfCalculation = previousStartDateInPrevYear, payrollNumber = Some(payrollNumberPrev))
        val previousTaxCodeRecord2 = TaxCodeRecordFactory.createSecondaryEmployment(taxCode = "185L", employerName = "Employer 2", dateOfCalculation = previousStartDateInPrevYear, payrollNumber = Some(payrollNumberPrev))

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(previousTaxCodeRecord1, previousTaxCodeRecord2)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq.empty[TaxCodeRecordWithEndDate], Seq.empty[TaxCodeRecordWithEndDate])

        Await.result(service.taxCodeChange(testNino), 5.seconds) mustEqual expectedResult
      }

      "this is the first ever employment (2 employments starting together)" in {
        val currentStartDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2)
        val payrollNumberCurr1 = randomInt().toString
        val payrollNumberCurr2 = randomInt().toString

        val testNino = randomNino

        val currentTaxCodeRecord1 = TaxCodeRecordFactory.createPrimaryEmployment(taxCode = "1000L", dateOfCalculation = currentStartDate, payrollNumber = Some(payrollNumberCurr1))
        val currentTaxCodeRecord2 = TaxCodeRecordFactory.createSecondaryEmployment(taxCode = "185L", employerName = "Employer 2", dateOfCalculation = currentStartDate, payrollNumber = Some(payrollNumberCurr2))

        val taxCodeHistory = TaxCodeHistory(
          testNino.withoutSuffix,
          Seq(currentTaxCodeRecord1, currentTaxCodeRecord2)
        )

        when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

        val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

        val expectedResult = TaxCodeChange(Seq.empty[TaxCodeRecordWithEndDate], Seq.empty[TaxCodeRecordWithEndDate])

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

        val previousTaxCodeRecord = TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousStartDateInPrevYear, payrollNumber = Some(payrollNumberPrev))
        val currentTaxCodeRecordPrimary = TaxCodeRecordFactory.createPrimaryEmployment(taxCode = "1000L", dateOfCalculation = currentStartDate, payrollNumber = Some(payrollNumberCurr))
        val currentTaxCodeRecordSecondary = TaxCodeRecordFactory.createSecondaryEmployment(taxCode = "1001L", employerName = "Employer 2", dateOfCalculation = currentStartDate, payrollNumber = Some(payrollNumberCurr))
        val currentTaxCodeRecordSecondary2 = TaxCodeRecordFactory.createSecondaryEmployment(taxCode = "1002L", employerName = "Employer 2", dateOfCalculation = currentStartDate, payrollNumber = Some(payrollNumberCurr2))

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
    val payrollNumber1 = randomInt().toString
    val payrollNumber2 = randomInt().toString
    val nino = randomNino

    "return false and list of confirmed and unconfirmed tax codes" when {
      "the one tax code returned from tax account record, matches the one returned from tax code list" in {

        val taxCodeHistory = TaxCodeHistory(
          nino.withoutSuffix,
          taxCodeRecord = Seq(
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = newCodeDate),
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousCodeDate))
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
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1), taxCode = "1155L"),
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1), taxCode = "1175L"),
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1), taxCode = "1195L"),
            TaxCodeRecordFactory.createSecondaryEmployment(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber2)),
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber1)),
            TaxCodeRecordFactory.createSecondaryEmployment(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber2))
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
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = newCodeDate),
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousCodeDate)))

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
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1), taxCode = "1155L"),
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1), taxCode = "1175L"),
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber1), taxCode = "1195L"),
            TaxCodeRecordFactory.createSecondaryEmployment(dateOfCalculation = newCodeDate, payrollNumber = Some(payrollNumber2)),
            TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber1)),
            TaxCodeRecordFactory.createSecondaryEmployment(dateOfCalculation = previousCodeDate, payrollNumber = Some(payrollNumber2))
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
          TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = newCodeDate),
          TaxCodeRecordFactory.createPrimaryEmployment(dateOfCalculation = previousCodeDate)))

      when(incomeService.taxCodeIncomes(any(), any())(any())).thenReturn(Future.failed(new RuntimeException("Runtime")))
      when(taxCodeChangeConnector.taxCodeHistory(any(), any(), any())).thenReturn(Future.successful(taxCodeHistory))

      val service: TaxCodeChangeServiceImpl = createService(taxCodeChangeConnector)

      val ex = the[BadRequestException] thrownBy Await.result(service.taxCodeMismatch(nino), 5.seconds)
      ex.getMessage must include("Runtime")
    }

  }


  "latestTaxCodes" should {

    val nino = randomNino
    val startYear = TaxYear(TaxYearResolver.startOfCurrentTaxYear.minusYears(1))
    val endYear = TaxYear(TaxYearResolver.currentTaxYear)
    val dateOfCalculation = TaxYearResolver.startOfCurrentTaxYear.minusMonths(1)
    val endOfTaxCode = TaxYear().prev.end
    val taxCodeRecord1 = TaxCodeRecord("1185L", "", "Employer 1", true, dateOfCalculation,Some("123"),false,Primary)
    val taxCodeRecord2 = TaxCodeRecord("1085L", "", "Employer 1", true, dateOfCalculation,Some("321"),false,Primary)
    val taxCodeRecordWithEndDate1 = TaxCodeRecordWithEndDate("1185L", "",dateOfCalculation,dateOfCalculation, "Employer 1",Some("123"), true, true)
    val taxCodeRecordWithEndDate2 = TaxCodeRecordWithEndDate("1085L", "",dateOfCalculation,dateOfCalculation, "Employer 1",Some("123"), true, false)

    "return an empty sequence" when {
      "no tax code records are returned" in {

        val taxCodeRecordList = Seq.empty
        val taxCodeHistory = TaxCodeHistory(nino.toString(), taxCodeRecordList)

        when(taxCodeChangeConnector.taxCodeHistory(nino, startYear, endYear)) thenReturn (Future.successful(taxCodeHistory))

        val latestTaxCodes = Await.result(createService(taxCodeChangeConnector).latestTaxCodes(nino, startYear), 5.seconds)
        val expectedResult = Seq.empty

        latestTaxCodes mustEqual expectedResult
      }
    }

    "return a list of most recent tax codes" when {

      "there is a single tax code under a single employer CY-1" in {

        val taxCodeRecord1 = TaxCodeRecord("1185L", "", "Employer 1", true, dateOfCalculation,Some("123"),false,Primary)
        val taxCodeRecordWithEndDate1 = TaxCodeRecordWithEndDate(
          "1185L",
          "",
          dateOfCalculation,
          endOfTaxCode,
          "Employer 1",
          Some("123"),
          false,
          true
        )

        val taxCodeRecordList = Seq(taxCodeRecord1)
        val taxCodeHistory = TaxCodeHistory(nino.toString(), taxCodeRecordList)

        when(taxCodeChangeConnector.taxCodeHistory(nino,startYear,endYear)) thenReturn(Future.successful(taxCodeHistory))

        val latestTaxCodes = Await.result(createService(taxCodeChangeConnector).latestTaxCodes(nino,startYear),5.seconds)
        val expectedResult = Seq(taxCodeRecordWithEndDate1)

        latestTaxCodes mustEqual expectedResult
      }

      "there are multiple tax codes with the same date of calculation under a single employer" in {

        val taxCodeRecord1 = TaxCodeRecord("1185L", "", "Employer 1", true, dateOfCalculation,Some("123"),false,Primary)
        val taxCodeRecord2 = TaxCodeRecord("1085L", "", "Employer 1", true, dateOfCalculation,Some("321"),false,Secondary)

        val taxCodeRecordWithEndDate1 = TaxCodeRecordWithEndDate(
          "1185L",
          "",
          dateOfCalculation,
          endOfTaxCode,
          "Employer 1",
          Some("123"),
          false,
          true
        )
        val taxCodeRecordWithEndDate2 = TaxCodeRecordWithEndDate(
          "1085L",
          "",
          dateOfCalculation,
          endOfTaxCode,
          "Employer 1",
          Some("321"),
          false,
          false
        )

        val taxCodeRecordList = Seq(taxCodeRecord1, taxCodeRecord2)
        val taxCodeHistory = TaxCodeHistory(nino.toString(), taxCodeRecordList)

        when(taxCodeChangeConnector.taxCodeHistory(nino,startYear,endYear)) thenReturn(Future.successful(taxCodeHistory))

        val latestTaxCodes = Await.result(createService(taxCodeChangeConnector).latestTaxCodes(nino,startYear),5.seconds)
        val expectedResult = Seq(taxCodeRecordWithEndDate1, taxCodeRecordWithEndDate2)

        latestTaxCodes mustEqual expectedResult
      }

      "there are multiple tax codes with different date of calculation under a single employer" in {

        val date = TaxYearResolver.startOfCurrentTaxYear.minusMonths(3)

        val taxCodeRecord1 = TaxCodeRecord("1185L", "", "Employer 1", true, dateOfCalculation,Some("123"),false,Primary)
        val taxCodeRecord2 = TaxCodeRecord("1085L", "", "Employer 1", true, date,Some("321"),false,Secondary)

        val taxCodeRecordWithEndDate1 = TaxCodeRecordWithEndDate(
          "1185L",
          "",
          dateOfCalculation,
          endOfTaxCode,
          "Employer 1",
          Some("123"),
          false,
          true
        )

        val taxCodeRecordList = Seq(taxCodeRecord1, taxCodeRecord2)
        val taxCodeHistory = TaxCodeHistory(nino.toString(), taxCodeRecordList)

        when(taxCodeChangeConnector.taxCodeHistory(nino,startYear,endYear)) thenReturn(Future.successful(taxCodeHistory))

        val latestTaxCodes = Await.result(createService(taxCodeChangeConnector).latestTaxCodes(nino,startYear),5.seconds)
        val expectedResult = Seq(taxCodeRecordWithEndDate1)

        latestTaxCodes mustEqual expectedResult
      }

      "there are multiple tax codes with different date of calculation under multiple employers" in {

        val dateOfCalculation = TaxYearResolver.startOfCurrentTaxYear.minusMonths(1)
        val taxCodeRecord1 = TaxCodeRecord("1L", "", "Employer 1", true, dateOfCalculation.minusMonths(2),Some("123"),false,Primary)
        val taxCodeRecord2 = TaxCodeRecord("2L", "", "Employer 1", true, dateOfCalculation.minusMonths(3),Some("321"),false,Secondary)
        val taxCodeRecord3 = TaxCodeRecord("3L", "", "Employer 2", true, dateOfCalculation.minusMonths(5),Some("321"),false,Secondary)
        val taxCodeRecord4 = TaxCodeRecord("4L", "", "Employer 2", true, dateOfCalculation.minusMonths(5),Some("321"),false,Secondary)
        val taxCodeRecord5 = TaxCodeRecord("5L", "", "Employer 3", true, dateOfCalculation.minusMonths(5),Some("321"),false,Secondary)
        val taxCodeRecord6 = TaxCodeRecord("6L", "", "Employer 3", true, dateOfCalculation.minusDays(4),Some("321"),false,Secondary)

        val taxCodeRecordWithEndDate1 = TaxCodeRecordWithEndDate(
          "1L",
          "",
          dateOfCalculation.minusMonths(2),
          endOfTaxCode,
          "Employer 1",
          Some("123"),
          false,
          true
        )
        val taxCodeRecordWithEndDate3 = TaxCodeRecordWithEndDate(
          "3L",
          "",
          dateOfCalculation.minusMonths(5),
          endOfTaxCode,
          "Employer 2",
          Some("321"),
          false,
          false
        )
        val taxCodeRecordWithEndDate4 = TaxCodeRecordWithEndDate(
          "4L",
          "",
          dateOfCalculation.minusMonths(5),
          endOfTaxCode,
          "Employer 2",
          Some("321"),
          false,
          false
        )
        val taxCodeRecordWithEndDate6 = TaxCodeRecordWithEndDate(
          "6L",
          "",
          dateOfCalculation.minusDays(4),
          endOfTaxCode,
          "Employer 3",
          Some("321"),
          false,
          false
        )

        val taxCodeRecordList = Seq(taxCodeRecord1,taxCodeRecord2,taxCodeRecord3,taxCodeRecord4,taxCodeRecord5,taxCodeRecord6)
        val taxCodeHistory = TaxCodeHistory(nino.toString(), taxCodeRecordList)

        when(taxCodeChangeConnector.taxCodeHistory(nino,startYear,endYear)) thenReturn Future.successful(taxCodeHistory)

        val latestTaxCodes = Await.result(createService(taxCodeChangeConnector).latestTaxCodes(nino,startYear),5.seconds)
        val expectedResult = Seq(taxCodeRecordWithEndDate1, taxCodeRecordWithEndDate3, taxCodeRecordWithEndDate4, taxCodeRecordWithEndDate6)

        latestTaxCodes.sortBy(_.employerName) mustEqual expectedResult.sortBy(_.employerName)
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
  val auditor = mock[Auditor]
  val incomeService: IncomeService = mock[IncomeService]

  private def createService(
                             mockConnector: TaxCodeChangeConnector = taxCodeChangeConnector,
                             mockAuditor: Auditor = auditor,
                             incomeService: IncomeService = incomeService) = {

    new TaxCodeChangeServiceImpl(mockConnector, mockAuditor, incomeService)
  }

  private def randomNino: Nino = new Generator(new Random).nextNino

  private def randomInt(maxDigits: Int = 5) = {
    import scala.math.pow
    val random = new Random
    random.nextInt(pow(10, maxDigits).toInt)
  }
}

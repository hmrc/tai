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
import org.mockito.Matchers.{any, eq => Meq}
import org.mockito.Mockito.{doNothing, times, verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.model.TaiRoot
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.income._
import uk.gov.hmrc.tai.model.domain.response._
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.{IncomeRepository, TaxAccountRepository}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class IncomeServiceSpec extends PlaySpec with MockitoSugar {

  "untaxedInterest" must {
    "return total amount only for passed nino and year" when {
      "no bank details exist" in {
        val mockIncomeRepository = mock[IncomeRepository]
        val incomes = Incomes(Seq.empty[TaxCodeIncome], NonTaxCodeIncome(Some(untaxedInterest), Seq.empty[OtherNonTaxCodeIncome]))
        when(mockIncomeRepository.incomes(any(), any())(any())).thenReturn(Future.successful(incomes))

        val SUT = createSUT(employmentService = mock[EmploymentService], incomeRepository = mockIncomeRepository)
        val result = Await.result(SUT.untaxedInterest(nino)(HeaderCarrier()), 5.seconds)

        result mustBe Some(untaxedInterest)
      }
    }

    "return total amount and bank accounts" when {
      "bank accounts exist" in {
        val mockIncomeRepository = mock[IncomeRepository]
        val incomes = Incomes(Seq.empty[TaxCodeIncome], NonTaxCodeIncome(Some(untaxedInterestWithBankAccount), Seq.empty[OtherNonTaxCodeIncome]))
        when(mockIncomeRepository.incomes(any(), any())(any())).thenReturn(Future.successful(incomes))

        val SUT = createSUT(incomeRepository = mockIncomeRepository)
        val result = Await.result(SUT.untaxedInterest(nino)(HeaderCarrier()), 5.seconds)

        result mustBe Some(untaxedInterestWithBankAccount)
      }
    }

  }

  "taxCodeIncome" must {
    "return the list of taxCodeIncomes for passed nino" in {
      val taxCodeIncomes = Seq(TaxCodeIncome(EmploymentIncome, Some(1), BigDecimal(0),
        "EmploymentIncome", "1150L", "Employer1", Week1Month1BasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0)),
        TaxCodeIncome(EmploymentIncome, Some(2), BigDecimal(0),
          "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0)))

      val mockIncomeRepository = mock[IncomeRepository]
      when(mockIncomeRepository.taxCodeIncomes(any(), any())(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      val SUT = createSUT(incomeRepository = mockIncomeRepository)
      val result = Await.result(SUT.taxCodeIncomes(nino, TaxYear())(HeaderCarrier()), 5 seconds)

      result mustBe taxCodeIncomes
    }
  }

  "Income" must {
    "return tax-code and non-tax code incomes" in {
      val mockIncomeRepository = mock[IncomeRepository]
      val incomes = Incomes(Seq.empty[TaxCodeIncome], NonTaxCodeIncome(None, Seq.empty[OtherNonTaxCodeIncome]))
      when(mockIncomeRepository.incomes(any(), any())(any())).thenReturn(Future.successful(incomes))

      val sut = createSUT(incomeRepository = mockIncomeRepository)

      val result = Await.result(sut.incomes(nino, TaxYear()), 5.seconds)

      result mustBe incomes

    }
  }

  "updateTaxCodeIncome" must {
    "for current year" must {
      "return an income success" when {
        "a valid update amount is provided" in {
          val taxYear = TaxYear()

          val taxCodeIncomes = Seq(TaxCodeIncome(EmploymentIncome, Some(1), BigDecimal(123.45),
            "", "", "", Week1Month1BasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0)))

          val mockEmploymentSvc = mock[EmploymentService]
          when(mockEmploymentSvc.employment(any(), any())(any()))
            .thenReturn(Future.successful(Some(Employment("", None, LocalDate.now(), None, Seq.empty[AnnualAccount], "", "", 0, Some(100), false, false))))

          val mockTaxAccountSvc = mock[TaxAccountService]
          when(mockTaxAccountSvc.personDetails(any())(any())).thenReturn(Future.successful(taiRoot))

          val mockIncomeRepository = mock[IncomeRepository]
          when(mockIncomeRepository.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))

          val mockTaxAccountRepository = mock[TaxAccountRepository]
          when(
            mockTaxAccountRepository.updateTaxCodeAmount(any(), Meq[TaxYear](taxYear), any(), any(), any(), any())(any())
          ).thenReturn(
            Future.successful(HodUpdateSuccess)
          )
          when(
            mockTaxAccountRepository.updateTaxCodeAmount(any(), Meq[TaxYear](taxYear.next), any(), any(), any(), any())(any())
          ).thenReturn(
            Future.successful(HodUpdateSuccess)
          )

          val mockAuditor = mock[Auditor]
          doNothing().when(mockAuditor)
            .sendDataEvent(any(), any())(any())

          val SUT = createSUT(
            employmentService = mockEmploymentSvc,
            taxAccountService = mockTaxAccountSvc,
            incomeRepository = mockIncomeRepository,
            taxAccountRepository = mockTaxAccountRepository,
            auditor = mockAuditor)

          val result = Await.result(SUT.updateTaxCodeIncome(nino, taxYear,1,1234)(HeaderCarrier()), 5 seconds)

          result mustBe IncomeUpdateSuccess
          verify(mockTaxAccountSvc, times(1)).invalidateTaiCacheData()(any())
          verify(mockAuditor).sendDataEvent(Matchers.eq("Update Multiple Employments Data"), any())(any())
        }
      }

      "return an error indicating a CY update failure" when {
        "the hod update fails for a CY update" in {
          val taxYear = TaxYear()

          val taxCodeIncomes = Seq(TaxCodeIncome(EmploymentIncome, Some(1), BigDecimal(123.45),
            "", "", "", Week1Month1BasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0)))

          val mockEmploymentSvc = mock[EmploymentService]
          when(mockEmploymentSvc.employment(any(), any())(any()))
            .thenReturn(Future.successful(Some(Employment("", None, LocalDate.now(), None, Seq.empty[AnnualAccount], "", "", 0, Some(100), false, false))))

          val mockIncomeRepository = mock[IncomeRepository]
          when(mockIncomeRepository.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))

          val mockTaxAccountRepository = mock[TaxAccountRepository]
          when(
            mockTaxAccountRepository.updateTaxCodeAmount(any(), Meq[TaxYear](taxYear), any(), any(), any(), any())(any())
          ).thenReturn(
            Future.successful(HodUpdateFailure)
          )

          val mockTaxAccountSvc = mock[TaxAccountService]
          when(mockTaxAccountSvc.personDetails(any())(any())).thenReturn(Future.successful(taiRoot))

          val SUT = createSUT(employmentService = mockEmploymentSvc,
            incomeRepository = mockIncomeRepository,
            taxAccountRepository = mockTaxAccountRepository,
            taxAccountService = mockTaxAccountSvc)

          val result = Await.result(SUT.updateTaxCodeIncome(nino, taxYear,1,1234)(HeaderCarrier()), 5 seconds)

          result mustBe IncomeUpdateFailed("Hod update failed for CY update")
        }
      }

      "return an error indicating a CY+1 update failure" when {
        "the hod update fails for a CY+1 update" in {

          val cyTaxYear = TaxYear()
          val cyPlusOneTaxYear = TaxYear().next

          val taxCodeIncomes = Seq(TaxCodeIncome(EmploymentIncome, Some(1), BigDecimal(123.45),
            "", "", "", Week1Month1BasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0)))

          val mockEmploymentSvc = mock[EmploymentService]
          when(mockEmploymentSvc.employment(any(), any())(any()))
            .thenReturn(Future.successful(Some(Employment("", None, LocalDate.now(), None, Seq.empty[AnnualAccount], "", "", 0, Some(100), false, false))))

          val mockTaxAccountSvc = mock[TaxAccountService]
          when(mockTaxAccountSvc.personDetails(any())(any())).thenReturn(Future.successful(taiRoot))

          val mockIncomeRepository = mock[IncomeRepository]
          when(mockIncomeRepository.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))

          val mockTaxAccountRepository = mock[TaxAccountRepository]
          when(
            mockTaxAccountRepository.updateTaxCodeAmount(any(), Matchers.eq(cyTaxYear), any(), any(), any(), any())(any())
          ).thenReturn(Future.successful(HodUpdateSuccess))

          when(
            mockTaxAccountRepository.updateTaxCodeAmount(any(), Matchers.eq(cyPlusOneTaxYear), any(), any(), any(), any())(any())
          ).thenReturn(Future.successful(HodUpdateFailure))

          val SUT = createSUT(employmentService = mockEmploymentSvc,
            taxAccountService = mockTaxAccountSvc,
            incomeRepository = mockIncomeRepository,
            taxAccountRepository = mockTaxAccountRepository)

          val result = Await.result(SUT.updateTaxCodeIncome(nino, TaxYear(),1,1234)(HeaderCarrier()), 5.seconds)

          result mustBe IncomeUpdateFailed("Hod update failed for CY+1 update")
        }
      }
    }

    "for next year" must {
      "return an income success" when {
        "anM update amount is provided" in {
          val taxYear = TaxYear().next

          val taxCodeIncomes = Seq(TaxCodeIncome(EmploymentIncome, Some(1), BigDecimal(123.45),
            "", "", "", Week1Month1BasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0)))

          val mockEmploymentSvc = mock[EmploymentService]
          when(mockEmploymentSvc.employment(any(), any())(any()))
            .thenReturn(Future.successful(Some(Employment("", None, LocalDate.now(), None, Seq.empty[AnnualAccount], "", "", 0, Some(100), false, false))))

          val mockTaxAccountSvc = mock[TaxAccountService]
          when(mockTaxAccountSvc.personDetails(any())(any())).thenReturn(Future.successful(taiRoot))

          val mockIncomeRepository = mock[IncomeRepository]
          when(mockIncomeRepository.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))

          val mockTaxAccountRepository = mock[TaxAccountRepository]
          when(
            mockTaxAccountRepository.updateTaxCodeAmount(any(), Meq[TaxYear](taxYear), any(), any(), any(), any())(any())
          ).thenReturn(
            Future.successful(HodUpdateSuccess)
          )

          val mockAuditor = mock[Auditor]
          doNothing().when(mockAuditor).sendDataEvent(any(), any())(any())

          val SUT = createSUT(
            employmentService = mockEmploymentSvc,
            taxAccountService = mockTaxAccountSvc,
            incomeRepository = mockIncomeRepository,
            taxAccountRepository = mockTaxAccountRepository,
            auditor = mockAuditor)

          val result = Await.result(SUT.updateTaxCodeIncome(nino, taxYear, 1, 1234)(HeaderCarrier()), 5.seconds)

          result mustBe IncomeUpdateSuccess
          verify(mockTaxAccountRepository, times(1)).updateTaxCodeAmount(Meq(nino), Meq(taxYear), any(), Meq(1), any(), Meq(1234))(any())
          verify(mockAuditor).sendDataEvent(Matchers.eq("Update Multiple Employments Data"), any())(any())
        }
      }

    }
  }

  "retrieveTaxCodeIncomeAmount" must {

    "return an amount" when {
      "an employment has been found" in {

        val employmentId = 1
        val requiredEmploymentId = 1

        val taxCodeIncomes = Seq(TaxCodeIncome(EmploymentIncome, Some(employmentId), BigDecimal(12300.45),
          "", "", "", Week1Month1BasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0)))



        val SUT = createSUT()

        val result = SUT.retrieveTaxCodeIncomeAmount(nino, requiredEmploymentId, taxCodeIncomes)

        result mustBe 12300.45
      }
    }

    "return zero" when {
      "an employment has not been found" in {

        val employmentId = 1
        val requiredEmploymentId = 2

        val taxCodeIncomes = Seq(TaxCodeIncome(EmploymentIncome, Some(employmentId), BigDecimal(12300.45),
          "", "", "", Week1Month1BasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0)))

        val SUT = createSUT()

        val result = SUT.retrieveTaxCodeIncomeAmount(nino, requiredEmploymentId, taxCodeIncomes)

        result mustBe 0
      }
    }
  }

  "retrieveEmploymentAmountYearToDate" must {

    "return the amount YTD" when {
      "payment information has be found" in {

        val payment = Payment(LocalDate.now(), 1234.56, 0, 0, 0, 0, 0, Weekly)
        val annualAccount = AnnualAccount("", TaxYear(), Available, Seq(payment), Nil)

        val SUT = createSUT()

        val result = SUT.retrieveEmploymentAmountYearToDate(nino, Some(Employment("", None, LocalDate.now(), None, Seq(annualAccount), "", "", 0, Some(100), false, false)))

        result mustBe 1234.56
      }
    }

    "return zero" when {
      "payment information cannot be found" in {

        val annualAccount = AnnualAccount("", TaxYear(), Available, Nil, Nil)

        val SUT = createSUT()

        val result = SUT.retrieveEmploymentAmountYearToDate(nino, Some(Employment("", None, LocalDate.now(), None, Seq(annualAccount), "", "", 0, Some(100), false, false)))

        result mustBe 0
      }
    }
  }

  private val nino = new Generator(new Random).nextNino
  private implicit val hc = HeaderCarrier()
  private val taiRoot = TaiRoot(nino.nino, 1, "", "", None, "", "", false, None)

  private val account = BankAccount(
    3, Some("12345678"),
    Some("234567"),
    Some("Bank Name"),
    1000,
    None,
    None)

  private val untaxedInterest = UntaxedInterest(UntaxedInterestIncome, Some(1), 123, "desc", Seq.empty[BankAccount])
  private val untaxedInterestWithBankAccount = UntaxedInterest(UntaxedInterestIncome, Some(1), 123, "desc", Seq(account))

  private def createSUT(employmentService: EmploymentService = mock[EmploymentService],
                        taxAccountService: TaxAccountService = mock[TaxAccountService],
                        incomeRepository: IncomeRepository = mock[IncomeRepository],
                        taxAccountRepository: TaxAccountRepository = mock[TaxAccountRepository],
                        auditor: Auditor = mock[Auditor]) =
    new IncomeService(employmentService, taxAccountService, incomeRepository, taxAccountRepository,auditor)
}
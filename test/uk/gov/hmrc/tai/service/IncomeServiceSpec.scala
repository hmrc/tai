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

import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.{doNothing, times, verify, when}
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.CitizenDetailsConnector
import uk.gov.hmrc.tai.model.ETag
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.income.{TaxCodeIncome, _}
import uk.gov.hmrc.tai.model.domain.response._
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.{IncomeRepository, TaxAccountRepository}
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class IncomeServiceSpec extends BaseSpec with ScalaFutures {

  "untaxedInterest" must {
    "return total amount only for passed nino and year" when {
      "no bank details exist" in {
        val mockIncomeRepository = mock[IncomeRepository]
        val incomes =
          Incomes(Seq.empty[TaxCodeIncome], NonTaxCodeIncome(Some(untaxedInterest), Seq.empty[OtherNonTaxCodeIncome]))
        when(mockIncomeRepository.incomes(any(), any())(any())).thenReturn(Future.successful(incomes))

        val SUT = createSUT(employmentService = mock[EmploymentService], incomeRepository = mockIncomeRepository)
        val result = Await.result(SUT.untaxedInterest(nino)(HeaderCarrier()), 5.seconds)

        result mustBe Some(untaxedInterest)
      }
    }

    "return total amount and bank accounts" when {
      "bank accounts exist" in {
        val mockIncomeRepository = mock[IncomeRepository]
        val incomes = Incomes(
          Seq.empty[TaxCodeIncome],
          NonTaxCodeIncome(Some(untaxedInterestWithBankAccount), Seq.empty[OtherNonTaxCodeIncome]))
        when(mockIncomeRepository.incomes(any(), any())(any())).thenReturn(Future.successful(incomes))

        val SUT = createSUT(incomeRepository = mockIncomeRepository)
        val result = Await.result(SUT.untaxedInterest(nino)(HeaderCarrier()), 5.seconds)

        result mustBe Some(untaxedInterestWithBankAccount)
      }
    }

  }

  "taxCodeIncome" must {
    "return the list of taxCodeIncomes for passed nino" in {
      val taxCodeIncomes = Seq(
        TaxCodeIncome(
          EmploymentIncome,
          Some(1),
          BigDecimal(0),
          "EmploymentIncome",
          "1150L",
          "Employer1",
          Week1Month1BasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0)
        ),
        TaxCodeIncome(
          EmploymentIncome,
          Some(2),
          BigDecimal(0),
          "EmploymentIncome",
          "1100L",
          "Employer2",
          OtherBasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0))
      )

      val mockIncomeRepository = mock[IncomeRepository]
      when(mockIncomeRepository.taxCodeIncomes(any(), any())(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      val SUT = createSUT(incomeRepository = mockIncomeRepository)
      val result = Await.result(SUT.taxCodeIncomes(nino, TaxYear())(HeaderCarrier()), 5 seconds)

      result mustBe taxCodeIncomes
    }
  }

  "matchedTaxCodeIncomesForYear" must {
    val mockIncomeRepository = mock[IncomeRepository]
    val mockEmploymentService = mock[EmploymentService]

    val taxCodeIncome = TaxCodeIncome(
      EmploymentIncome,
      Some(2),
      BigDecimal(0),
      EmploymentIncome.toString,
      "1100L",
      "Employer2",
      OtherBasisOperation,
      Live,
      BigDecimal(321.12),
      BigDecimal(0),
      BigDecimal(0)
    )

    val taxCodeIncomes: Seq[TaxCodeIncome] = Seq(
      TaxCodeIncome(
        PensionIncome,
        Some(1),
        BigDecimal(1100),
        PensionIncome.toString,
        "1150L",
        "Employer1",
        Week1Month1BasisOperation,
        Live,
        BigDecimal(0),
        BigDecimal(0),
        BigDecimal(0)
      ),
      TaxCodeIncome(
        EmploymentIncome,
        Some(2),
        BigDecimal(0),
        EmploymentIncome.toString,
        "1100L",
        "Employer2",
        OtherBasisOperation,
        Live,
        BigDecimal(321.12),
        BigDecimal(0),
        BigDecimal(0)
      ),
      TaxCodeIncome(
        EmploymentIncome,
        Some(3),
        BigDecimal(0),
        EmploymentIncome.toString,
        "1100L",
        "Employer2",
        OtherBasisOperation,
        Live,
        BigDecimal(321.12),
        BigDecimal(0),
        BigDecimal(0)
      )
    )

    val employment = Employment(
      "company name",
      Live,
      Some("888"),
      new LocalDate(TaxYear().next.year, 5, 26),
      None,
      Nil,
      "",
      "",
      2,
      Some(100),
      hasPayrolledBenefit = false,
      receivingOccupationalPension = true
    )
    val employments = Seq(employment, employment.copy(sequenceNumber = 1))
    val employmentWithDifferentSeqNumber = Seq(employment.copy(sequenceNumber = 99))

    "return a list of live and matched Employments & TaxCodeIncomes as IncomeSource for a given year" in {
      when(mockIncomeRepository.taxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(
          Future.successful(
            taxCodeIncomes
          ))

      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments :+ employment.copy(employmentStatus = Ceased)))

      val sut = createSUT(employmentService = mockEmploymentService, incomeRepository = mockIncomeRepository)
      val result = Await.result(
        sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, Live)(HeaderCarrier()),
        5.seconds)

      val expectedResult = Seq(IncomeSource(taxCodeIncomes(1), employment))

      result mustBe expectedResult
    }

    "return a list of ceased and matched Employments & TaxCodeIncomes as IncomeSource for a given year" in {
      when(mockIncomeRepository.taxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      when(mockEmploymentService.employments(meq(nino), meq(TaxYear().next))(any[HeaderCarrier]))
        .thenReturn(
          Future.successful(
            Seq(
              employment.copy(employmentStatus = Ceased),
              employment.copy(employmentStatus = Live)
            )))

      val sut = createSUT(employmentService = mockEmploymentService, incomeRepository = mockIncomeRepository)
      val result = Await.result(
        sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, Ceased)(HeaderCarrier()),
        5.seconds)

      val expectedResult = Seq(IncomeSource(taxCodeIncome, employment.copy(employmentStatus = Ceased)))

      result mustBe expectedResult
    }

    "return a list of potentially ceased and matched Employments & TaxCodeIncomes as IncomeSource for a given year" in {
      when(mockIncomeRepository.taxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      when(mockEmploymentService.employments(meq(nino), meq(TaxYear().next))(any[HeaderCarrier]))
        .thenReturn(
          Future.successful(
            Seq(
              employment.copy(employmentStatus = PotentiallyCeased),
              employment.copy(employmentStatus = Live)
            )))

      val sut = createSUT(employmentService = mockEmploymentService, incomeRepository = mockIncomeRepository)
      val result = Await.result(
        sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, PotentiallyCeased)(HeaderCarrier()),
        5.seconds)

      val expectedResult = Seq(IncomeSource(taxCodeIncome, employment.copy(employmentStatus = PotentiallyCeased)))

      result mustBe expectedResult
    }

    "return a list of not live and matched Employments & TaxCodeIncomes as IncomeSource for a given year" in {
      when(mockIncomeRepository.taxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(
          Future.successful(
            taxCodeIncomes
          ))

      when(mockEmploymentService.employments(meq(nino), meq(TaxYear().next))(any[HeaderCarrier]))
        .thenReturn(
          Future.successful(
            Seq(
              employment.copy(employmentStatus = Ceased),
              employment.copy(employmentStatus = PotentiallyCeased),
              employment.copy(employmentStatus = Live)
            )))

      val sut = createSUT(employmentService = mockEmploymentService, incomeRepository = mockIncomeRepository)
      val result = Await.result(
        sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, NotLive)(HeaderCarrier()),
        5.seconds)

      val expectedResult =
        Seq(
          IncomeSource(taxCodeIncomes(1), employment.copy(employmentStatus = Ceased)),
          IncomeSource(taxCodeIncomes(1), employment.copy(employmentStatus = PotentiallyCeased))
        )

      result mustBe expectedResult
    }

    "return empty JSON when no records match" in {
      when(mockIncomeRepository.taxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(Seq(taxCodeIncome.copy(employmentId = None))))

      when(mockEmploymentService.employments(meq(nino), meq(TaxYear().next))(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments))

      val sut = createSUT(employmentService = mockEmploymentService, incomeRepository = mockIncomeRepository)
      val result = Await.result(
        sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, PotentiallyCeased)(HeaderCarrier()),
        5.seconds)

      result mustBe Seq.empty

    }

    "return list of live and matched pension TaxCodeIncomes for a given year" in {
      when(mockIncomeRepository.taxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      when(mockEmploymentService.employments(meq(nino), meq(TaxYear().next))(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments))

      val sut = createSUT(employmentService = mockEmploymentService, incomeRepository = mockIncomeRepository)
      val result = Await
        .result(sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, PensionIncome, Live)(HeaderCarrier()), 5.seconds)

      val expectedResult =
        Seq(
          IncomeSource(
            TaxCodeIncome(
              componentType = PensionIncome,
              employmentId = Some(1),
              amount = 1100,
              description = "PensionIncome",
              taxCode = "1150L",
              name = "Employer1",
              basisOperation = Week1Month1BasisOperation,
              status = Live,
              inYearAdjustmentIntoCY = 0,
              totalInYearAdjustment = 0,
              inYearAdjustmentIntoCYPlusOne = 0
            ),
            Employment(
              name = "company name",
              employmentStatus = Live,
              payrollNumber = Some("888"),
              startDate = LocalDate.parse(s"${TaxYear().next.year}-05-26"),
              endDate = None,
              annualAccounts = Seq.empty,
              taxDistrictNumber = "",
              payeNumber = "",
              sequenceNumber = 1,
              cessationPay = Some(100),
              hasPayrolledBenefit = false,
              receivingOccupationalPension = true
            )
          ))

      result mustBe expectedResult

    }

    "return empty json when there are no matching live employments" in {
      when(mockIncomeRepository.taxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      when(mockEmploymentService.employments(meq(nino), meq(TaxYear().next))(any[HeaderCarrier]))
        .thenReturn(Future.successful(employmentWithDifferentSeqNumber))

      val sut = createSUT(employmentService = mockEmploymentService, incomeRepository = mockIncomeRepository)
      val result = Await.result(
        sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, Live)(HeaderCarrier()),
        5.seconds)

      result mustBe Seq.empty
    }

    "return empty json when there are no matching live pensions" in {
      when(mockIncomeRepository.taxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      when(mockEmploymentService.employments(meq(nino), meq(TaxYear().next))(any[HeaderCarrier]))
        .thenReturn(Future.successful(employmentWithDifferentSeqNumber))

      val sut = createSUT(employmentService = mockEmploymentService, incomeRepository = mockIncomeRepository)
      val result = Await
        .result(sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, PensionIncome, Live)(HeaderCarrier()), 5.seconds)

      result mustBe Seq.empty
    }

    "return empty json when there are no TaxCodeIncome records for a given nino" in {
      when(mockIncomeRepository.taxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(Seq.empty[TaxCodeIncome]))

      when(mockEmploymentService.employments(meq(nino), meq(TaxYear().next))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(employment)))

      val sut = createSUT(employmentService = mockEmploymentService, incomeRepository = mockIncomeRepository)
      val result = Await
        .result(sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, PensionIncome, Live)(HeaderCarrier()), 5.seconds)

      result mustBe Seq.empty
    }

    "return empty json when there are no employment records for a given nino" in {
      when(mockIncomeRepository.taxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      when(mockEmploymentService.employments(meq(nino), meq(TaxYear().next))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq.empty[Employment]))

      val sut = createSUT(employmentService = mockEmploymentService, incomeRepository = mockIncomeRepository)
      val result = Await
        .result(sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, PensionIncome, Live)(HeaderCarrier()), 5.seconds)

      result mustBe Seq.empty
    }
  }

  "nonMatchingCeasedEmployments" must {
    val mockIncomeRepository = mock[IncomeRepository]
    val mockEmploymentService = mock[EmploymentService]

    val taxCodeIncomes: Seq[TaxCodeIncome] = Seq(
      TaxCodeIncome(
        PensionIncome,
        Some(1),
        BigDecimal(1100),
        PensionIncome.toString,
        "1150L",
        "Employer1",
        Week1Month1BasisOperation,
        Live,
        BigDecimal(0),
        BigDecimal(0),
        BigDecimal(0)
      ),
      TaxCodeIncome(
        EmploymentIncome,
        Some(2),
        BigDecimal(0),
        EmploymentIncome.toString,
        "1100L",
        "Employer2",
        OtherBasisOperation,
        Live,
        BigDecimal(321.12),
        BigDecimal(0),
        BigDecimal(0)
      ),
      TaxCodeIncome(
        EmploymentIncome,
        Some(3),
        BigDecimal(0),
        EmploymentIncome.toString,
        "1100L",
        "Employer2",
        OtherBasisOperation,
        Live,
        BigDecimal(321.12),
        BigDecimal(0),
        BigDecimal(0)
      )
    )

    val employment = Employment(
      "company name",
      Live,
      Some("888"),
      new LocalDate(TaxYear().next.year, 5, 26),
      None,
      Nil,
      "",
      "",
      2,
      Some(100),
      hasPayrolledBenefit = false,
      receivingOccupationalPension = true
    )

    "return list of non matching ceased employments when some employments do have an end date" in {
      val employments =
        Seq(
          employment,
          employment.copy(
            employmentStatus = Ceased,
            sequenceNumber = 1,
            endDate = Some(new LocalDate(TaxYear().next.year, 8, 10))))

      when(mockIncomeRepository.taxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      when(mockEmploymentService.employments(meq(nino), meq(TaxYear().next))(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments))

      val nextTaxYear = TaxYear().next
      val sut = createSUT(incomeRepository = mockIncomeRepository, employmentService = mockEmploymentService)
      val result = Await.result(sut.nonMatchingCeasedEmployments(nino, nextTaxYear)(HeaderCarrier()), 5.seconds)

      val expectedResult =
        Seq(
          employment.copy(
            employmentStatus = Ceased,
            sequenceNumber = 1,
            endDate = Some(new LocalDate(TaxYear().next.year, 8, 10))))

      result mustBe expectedResult
    }

    "return empty json when no employments have an end date" in {
      val employments = Seq(employment, employment.copy(sequenceNumber = 1))

      when(mockIncomeRepository.taxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      when(mockEmploymentService.employments(meq(nino), meq(TaxYear().next))(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments))

      val nextTaxYear = TaxYear().next
      val sut = createSUT(incomeRepository = mockIncomeRepository, employmentService = mockEmploymentService)
      val result = Await.result(sut.nonMatchingCeasedEmployments(nino, nextTaxYear)(HeaderCarrier()), 5.seconds)

      result mustBe Seq.empty
    }

    "return empty json when TaxCodeIncomes do not have an Id" in {
      val employments = Seq(employment, employment.copy(sequenceNumber = 1))

      when(mockIncomeRepository.taxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(
          Future.successful(
            Seq(
              TaxCodeIncome(
                EmploymentIncome,
                None,
                BigDecimal(0),
                EmploymentIncome.toString,
                "1100L",
                "Employer2",
                OtherBasisOperation,
                Ceased,
                BigDecimal(321.12),
                BigDecimal(0),
                BigDecimal(0)
              )
            )))

      when(mockEmploymentService.employments(meq(nino), meq(TaxYear().next))(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments))

      val nextTaxYear = TaxYear().next
      val sut = createSUT(incomeRepository = mockIncomeRepository, employmentService = mockEmploymentService)
      val result = Await.result(sut.nonMatchingCeasedEmployments(nino, nextTaxYear)(HeaderCarrier()), 5.seconds)

      result mustBe Seq.empty
    }

    "return empty Json when there are no TaxCodeIncome records for a given nino" in {
      val employments = Seq(employment, employment.copy(sequenceNumber = 1))

      when(mockIncomeRepository.taxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(Seq.empty[TaxCodeIncome]))

      when(mockEmploymentService.employments(meq(nino), meq(TaxYear().next))(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments))

      val nextTaxYear = TaxYear().next
      val sut = createSUT(incomeRepository = mockIncomeRepository, employmentService = mockEmploymentService)
      val result = Await.result(sut.nonMatchingCeasedEmployments(nino, nextTaxYear)(HeaderCarrier()), 5.seconds)

      result mustBe Seq.empty
    }

    "return empty json when there are no employment records for a given nino" in {

      when(mockIncomeRepository.taxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(
          Future.successful(
            Seq(
              TaxCodeIncome(
                EmploymentIncome,
                None,
                BigDecimal(0),
                EmploymentIncome.toString,
                "1100L",
                "Employer2",
                OtherBasisOperation,
                Ceased,
                BigDecimal(321.12),
                BigDecimal(0),
                BigDecimal(0)
              )
            )))

      when(mockEmploymentService.employments(meq(nino), meq(TaxYear().next))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq.empty[Employment]))

      val nextTaxYear = TaxYear().next
      val sut = createSUT(incomeRepository = mockIncomeRepository, employmentService = mockEmploymentService)
      val result = Await.result(sut.nonMatchingCeasedEmployments(nino, nextTaxYear)(HeaderCarrier()), 5.seconds)

      result mustBe Seq.empty
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

  "Employments" must {
    "return sequence of employments when taxCodeIncomes is not empty" in {
      val mockEmploymentService = mock[EmploymentService]
      val emp = Employment(
        "company name",
        Live,
        Some("888"),
        new LocalDate(2017, 5, 26),
        None,
        Nil,
        "",
        "",
        2,
        Some(100),
        false,
        true)
      val taxCodeIncomes = Seq(
        TaxCodeIncome(
          EmploymentIncome,
          Some(1),
          BigDecimal(0),
          "EmploymentIncome",
          "1150L",
          "Employer1",
          Week1Month1BasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0)
        ),
        TaxCodeIncome(
          EmploymentIncome,
          Some(2),
          BigDecimal(0),
          "EmploymentIncome",
          "1100L",
          "Employer2",
          OtherBasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0))
      )

      when(mockEmploymentService.employments(any(), any())(any()))
        .thenReturn(Future.successful(Seq(emp)))

      val sut = createSUT(employmentService = mockEmploymentService)

      val result = Await.result(sut.employments(taxCodeIncomes, nino, TaxYear().next), 5.seconds)

      result mustBe Seq(emp)
    }

    "return empty sequence of when taxCodeIncomes is  empty" in {
      val taxCodeIncomes = Seq.empty[TaxCodeIncome]

      val sut = createSUT()

      val result = Await.result(sut.employments(taxCodeIncomes, nino, TaxYear()), 5.seconds)

      result mustBe Seq.empty[Employment]
    }
  }

  "updateTaxCodeIncome" must {
    "for current year" must {
      "return an income success" when {
        "a valid update amount is provided" in {
          val taxYear = TaxYear()

          val taxCodeIncomes = Seq(
            TaxCodeIncome(
              EmploymentIncome,
              Some(1),
              BigDecimal(123.45),
              "",
              "",
              "",
              Week1Month1BasisOperation,
              Live,
              BigDecimal(0),
              BigDecimal(0),
              BigDecimal(0)))

          val mockEmploymentSvc = mock[EmploymentService]
          when(mockEmploymentSvc.employment(any(), any())(any()))
            .thenReturn(
              Future.successful(
                Right(
                  Employment(
                    "",
                    Live,
                    None,
                    LocalDate.now(),
                    None,
                    Seq.empty[AnnualAccount],
                    "",
                    "",
                    0,
                    Some(100),
                    false,
                    false))))

          val citizenDetailsConnector = mock[CitizenDetailsConnector]
          when(citizenDetailsConnector.getEtag(any())(any())).thenReturn(Future.successful(Some(etag)))

          val mockIncomeRepository = mock[IncomeRepository]
          when(mockIncomeRepository.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))

          val mockTaxAccountRepository = mock[TaxAccountRepository]
          when(
            mockTaxAccountRepository.updateTaxCodeAmount(any(), meq[TaxYear](taxYear), any(), any(), any(), any())(
              any())
          ).thenReturn(
            Future.successful(HodUpdateSuccess)
          )

          val mockAuditor = mock[Auditor]
          doNothing()
            .when(mockAuditor)
            .sendDataEvent(any(), any())(any())

          val SUT = createSUT(
            employmentService = mockEmploymentSvc,
            citizenDetailsConnector = citizenDetailsConnector,
            incomeRepository = mockIncomeRepository,
            taxAccountRepository = mockTaxAccountRepository,
            auditor = mockAuditor
          )

          val result = Await.result(SUT.updateTaxCodeIncome(nino, taxYear, 1, 1234)(HeaderCarrier()), 5 seconds)

          result mustBe IncomeUpdateSuccess

          val auditMap = Map(
            "nino"          -> nino.value,
            "year"          -> taxYear.toString,
            "employmentId"  -> "1",
            "newAmount"     -> "1234",
            "currentAmount" -> "123.45")
          verify(mockAuditor).sendDataEvent(meq("Update Multiple Employments Data"), meq(auditMap))(any())
        }

        "the current amount is not provided due to no incomes returned" in {

          val taxYear = TaxYear()

          val mockEmploymentSvc = mock[EmploymentService]
          when(mockEmploymentSvc.employment(any(), any())(any()))
            .thenReturn(
              Future.successful(
                Right(
                  Employment(
                    "",
                    Live,
                    None,
                    LocalDate.now(),
                    None,
                    Seq.empty[AnnualAccount],
                    "",
                    "",
                    0,
                    Some(100),
                    false,
                    false))))

          val citizenDetailsConnector = mock[CitizenDetailsConnector]
          when(citizenDetailsConnector.getEtag(any())(any())).thenReturn(Future.successful(Some(etag)))

          val mockIncomeRepository = mock[IncomeRepository]
          when(mockIncomeRepository.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(Seq.empty))

          val mockTaxAccountRepository = mock[TaxAccountRepository]
          when(
            mockTaxAccountRepository.updateTaxCodeAmount(any(), meq[TaxYear](taxYear), any(), any(), any(), any())(
              any())
          ).thenReturn(
            Future.successful(HodUpdateSuccess)
          )

          val mockAuditor = mock[Auditor]
          doNothing()
            .when(mockAuditor)
            .sendDataEvent(any(), any())(any())

          val SUT = createSUT(
            employmentService = mockEmploymentSvc,
            citizenDetailsConnector = citizenDetailsConnector,
            incomeRepository = mockIncomeRepository,
            taxAccountRepository = mockTaxAccountRepository,
            auditor = mockAuditor
          )

          val result = Await.result(SUT.updateTaxCodeIncome(nino, taxYear, 1, 1234)(HeaderCarrier()), 5 seconds)

          result mustBe IncomeUpdateSuccess

          val auditMap = Map(
            "nino"          -> nino.value,
            "year"          -> taxYear.toString,
            "employmentId"  -> "1",
            "newAmount"     -> "1234",
            "currentAmount" -> "Unknown")

          verify(mockAuditor).sendDataEvent(meq("Update Multiple Employments Data"), meq(auditMap))(any())

        }

        "the current amount is not provided due to an income mismatch" in {

          val taxYear = TaxYear()

          val taxCodeIncomes = Seq(
            TaxCodeIncome(
              EmploymentIncome,
              Some(2),
              BigDecimal(123.45),
              "",
              "",
              "",
              Week1Month1BasisOperation,
              Live,
              BigDecimal(0),
              BigDecimal(0),
              BigDecimal(0)))

          val mockEmploymentSvc = mock[EmploymentService]
          when(mockEmploymentSvc.employment(any(), any())(any()))
            .thenReturn(
              Future.successful(
                Right(
                  Employment(
                    "",
                    Live,
                    None,
                    LocalDate.now(),
                    None,
                    Seq.empty[AnnualAccount],
                    "",
                    "",
                    0,
                    Some(100),
                    false,
                    false))))

          val citizenDetailsConnector = mock[CitizenDetailsConnector]
          when(citizenDetailsConnector.getEtag(any())(any())).thenReturn(Future.successful(Some(etag)))

          val mockIncomeRepository = mock[IncomeRepository]
          when(mockIncomeRepository.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))

          val mockTaxAccountRepository = mock[TaxAccountRepository]
          when(
            mockTaxAccountRepository.updateTaxCodeAmount(any(), meq[TaxYear](taxYear), any(), any(), any(), any())(
              any())
          ).thenReturn(
            Future.successful(HodUpdateSuccess)
          )

          val mockAuditor = mock[Auditor]
          doNothing()
            .when(mockAuditor)
            .sendDataEvent(any(), any())(any())

          val SUT = createSUT(
            employmentService = mockEmploymentSvc,
            citizenDetailsConnector = citizenDetailsConnector,
            incomeRepository = mockIncomeRepository,
            taxAccountRepository = mockTaxAccountRepository,
            auditor = mockAuditor
          )

          val result = Await.result(SUT.updateTaxCodeIncome(nino, taxYear, 1, 1234)(HeaderCarrier()), 5 seconds)

          result mustBe IncomeUpdateSuccess

          val auditMap = Map(
            "nino"          -> nino.value,
            "year"          -> taxYear.toString,
            "employmentId"  -> "1",
            "newAmount"     -> "1234",
            "currentAmount" -> "Unknown")

          verify(mockAuditor).sendDataEvent(meq("Update Multiple Employments Data"), meq(auditMap))(any())

        }
      }

      "return an error indicating a CY update failure" when {
        "the hod update fails for a CY update" in {
          val taxYear = TaxYear()

          val taxCodeIncomes = Seq(
            TaxCodeIncome(
              EmploymentIncome,
              Some(1),
              BigDecimal(123.45),
              "",
              "",
              "",
              Week1Month1BasisOperation,
              Live,
              BigDecimal(0),
              BigDecimal(0),
              BigDecimal(0)))

          val mockEmploymentSvc = mock[EmploymentService]
          when(mockEmploymentSvc.employment(any(), any())(any()))
            .thenReturn(
              Future.successful(
                Right(
                  Employment(
                    "",
                    Live,
                    None,
                    LocalDate.now(),
                    None,
                    Seq.empty[AnnualAccount],
                    "",
                    "",
                    0,
                    Some(100),
                    false,
                    false))))

          val mockIncomeRepository = mock[IncomeRepository]
          when(mockIncomeRepository.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))

          val mockTaxAccountRepository = mock[TaxAccountRepository]
          when(
            mockTaxAccountRepository.updateTaxCodeAmount(any(), meq[TaxYear](taxYear), any(), any(), any(), any())(
              any())
          ).thenReturn(
            Future.successful(HodUpdateFailure)
          )

          val citizenDetailsConnector = mock[CitizenDetailsConnector]
          when(citizenDetailsConnector.getEtag(any())(any())).thenReturn(Future.successful(Some(etag)))

          val SUT = createSUT(
            employmentService = mockEmploymentSvc,
            incomeRepository = mockIncomeRepository,
            taxAccountRepository = mockTaxAccountRepository,
            citizenDetailsConnector = citizenDetailsConnector
          )

          val result = Await.result(SUT.updateTaxCodeIncome(nino, taxYear, 1, 1234)(HeaderCarrier()), 5 seconds)

          result mustBe IncomeUpdateFailed(s"Hod update failed for ${taxYear.year} update")
        }
      }
    }

    "for next year" must {
      "return an income success" when {
        "an update amount is provided" in {
          val taxYear = TaxYear().next

          val taxCodeIncomes = Seq(
            TaxCodeIncome(
              EmploymentIncome,
              Some(1),
              BigDecimal(123.45),
              "",
              "",
              "",
              Week1Month1BasisOperation,
              Live,
              BigDecimal(0),
              BigDecimal(0),
              BigDecimal(0)))

          val mockEmploymentSvc = mock[EmploymentService]
          when(mockEmploymentSvc.employment(any(), any())(any()))
            .thenReturn(
              Future.successful(
                Right(
                  Employment(
                    "",
                    Live,
                    None,
                    LocalDate.now(),
                    None,
                    Seq.empty[AnnualAccount],
                    "",
                    "",
                    0,
                    Some(100),
                    false,
                    false))))

          val citizenDetailsConnector = mock[CitizenDetailsConnector]
          when(citizenDetailsConnector.getEtag(any())(any())).thenReturn(Future.successful(Some(etag)))

          val mockIncomeRepository = mock[IncomeRepository]
          when(mockIncomeRepository.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))

          val mockTaxAccountRepository = mock[TaxAccountRepository]
          when(
            mockTaxAccountRepository
              .updateTaxCodeAmount(any(), meq[TaxYear](taxYear), meq(1), any(), any(), any())(any())
          ).thenReturn(
            Future.successful(HodUpdateSuccess)
          )

          val mockAuditor = mock[Auditor]
          doNothing().when(mockAuditor).sendDataEvent(any(), any())(any())

          val SUT = createSUT(
            employmentService = mockEmploymentSvc,
            citizenDetailsConnector = citizenDetailsConnector,
            incomeRepository = mockIncomeRepository,
            taxAccountRepository = mockTaxAccountRepository,
            auditor = mockAuditor
          )

          val result = Await.result(SUT.updateTaxCodeIncome(nino, taxYear, 1, 1234)(HeaderCarrier()), 5.seconds)

          result mustBe IncomeUpdateSuccess
          verify(mockTaxAccountRepository, times(1))
            .updateTaxCodeAmount(meq(nino), meq(taxYear), any(), meq(1), any(), meq(1234))(any())

          val auditMap = Map(
            "nino"          -> nino.value,
            "year"          -> taxYear.toString,
            "employmentId"  -> "1",
            "newAmount"     -> "1234",
            "currentAmount" -> "123.45")

          verify(mockAuditor).sendDataEvent(meq("Update Multiple Employments Data"), meq(auditMap))(any())
        }
      }
    }

    "return a IncomeUpdateFailed if there is no etag" in {
      val taxYear = TaxYear()

      val taxCodeIncomes = Seq(
        TaxCodeIncome(
          EmploymentIncome,
          Some(1),
          BigDecimal(123.45),
          "",
          "",
          "",
          Week1Month1BasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0)))

      val mockEmploymentSvc = mock[EmploymentService]
      when(mockEmploymentSvc.employment(any(), any())(any()))
        .thenReturn(
          Future.successful(
            Right(
              Employment(
                "",
                Live,
                None,
                LocalDate.now(),
                None,
                Seq.empty[AnnualAccount],
                "",
                "",
                0,
                Some(100),
                false,
                false))))

      val mockIncomeRepository = mock[IncomeRepository]
      when(mockIncomeRepository.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))

      val mockTaxAccountRepository = mock[TaxAccountRepository]
      when(
        mockTaxAccountRepository
          .updateTaxCodeAmount(any(), meq[TaxYear](taxYear), meq(1), any(), any(), any())(any())
      ).thenReturn(
        Future.successful(HodUpdateSuccess)
      )

      val mockAuditor = mock[Auditor]
      doNothing().when(mockAuditor).sendDataEvent(any(), any())(any())

      val citizenDetailsConnector = mock[CitizenDetailsConnector]
      when(citizenDetailsConnector.getEtag(any())(any())).thenReturn(Future.successful(None))

      val SUT = createSUT(
        employmentService = mockEmploymentSvc,
        citizenDetailsConnector = citizenDetailsConnector,
        incomeRepository = mockIncomeRepository,
        taxAccountRepository = mockTaxAccountRepository,
        auditor = mockAuditor
      )

      val result = SUT.updateTaxCodeIncome(nino, taxYear, 1, 1234)(HeaderCarrier())
      result.futureValue mustBe IncomeUpdateFailed("Could not find an ETag")
    }

    "return a IncomeUpdateFailed if there etag is not an int" in {
      val taxYear = TaxYear()

      val taxCodeIncomes = Seq(
        TaxCodeIncome(
          EmploymentIncome,
          Some(1),
          BigDecimal(123.45),
          "",
          "",
          "",
          Week1Month1BasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0)))

      val mockEmploymentSvc = mock[EmploymentService]
      when(mockEmploymentSvc.employment(any(), any())(any()))
        .thenReturn(
          Future.successful(
            Right(
              Employment(
                "",
                Live,
                None,
                LocalDate.now(),
                None,
                Seq.empty[AnnualAccount],
                "",
                "",
                0,
                Some(100),
                false,
                false))))

      val mockIncomeRepository = mock[IncomeRepository]
      when(mockIncomeRepository.taxCodeIncomes(any(), any())(any())).thenReturn(Future.successful(taxCodeIncomes))

      val mockTaxAccountRepository = mock[TaxAccountRepository]
      when(
        mockTaxAccountRepository
          .updateTaxCodeAmount(any(), meq[TaxYear](taxYear), meq(1), any(), any(), any())(any())
      ).thenReturn(
        Future.successful(HodUpdateSuccess)
      )

      val mockAuditor = mock[Auditor]
      doNothing().when(mockAuditor).sendDataEvent(any(), any())(any())

      val citizenDetailsConnector = mock[CitizenDetailsConnector]
      when(citizenDetailsConnector.getEtag(any())(any())).thenReturn(Future.successful(Some(ETag("not an ETag"))))

      val SUT = createSUT(
        employmentService = mockEmploymentSvc,
        citizenDetailsConnector = citizenDetailsConnector,
        incomeRepository = mockIncomeRepository,
        taxAccountRepository = mockTaxAccountRepository,
        auditor = mockAuditor
      )

      val result = SUT.updateTaxCodeIncome(nino, taxYear, 1, 1234)(HeaderCarrier())
      result.futureValue mustBe IncomeUpdateFailed("Could not parse etag")
    }
  }

  private val etag = ETag("1")

  private val account = BankAccount(3, Some("12345678"), Some("234567"), Some("Bank Name"), 1000, None, None)

  private val untaxedInterest = UntaxedInterest(UntaxedInterestIncome, Some(1), 123, "desc", Seq.empty[BankAccount])
  private val untaxedInterestWithBankAccount =
    UntaxedInterest(UntaxedInterestIncome, Some(1), 123, "desc", Seq(account))

  private def createSUT(
    employmentService: EmploymentService = mock[EmploymentService],
    citizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector],
    incomeRepository: IncomeRepository = mock[IncomeRepository],
    taxAccountRepository: TaxAccountRepository = mock[TaxAccountRepository],
    auditor: Auditor = mock[Auditor]) =
    new IncomeService(employmentService, citizenDetailsConnector, incomeRepository, taxAccountRepository, auditor)
}

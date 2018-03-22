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

package uk.gov.hmrc.tai.repositories

import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.income._
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class IncomeRepositorySpec extends PlaySpec with MockitoSugar {

  "Income" must {
    "return empty sequence of non-tax code income" when {
      "there is no non-tax-code income present" in {
        val mockTaxAccountRepository = mock[TaxAccountRepository]
        val mockBbsiRepository = mock[BbsiRepository]
        when(mockTaxAccountRepository.taxAccount(any(), any())(any())).
          thenReturn(Future.successful(Json.arr()))
        when(mockBbsiRepository.bbsiDetails(any(), any())(any())).thenReturn(Future.successful(Seq.empty[BankAccount]))

        val sut = createSut(mockTaxAccountRepository, mockBbsiRepository)

        val result = Await.result(sut.incomes(nino, TaxYear()), 5.seconds)

        result.nonTaxCodeIncomes mustBe NonTaxCodeIncome(None, Seq.empty[OtherNonTaxCodeIncome])
      }
    }

    "return non-tax-code incomes" when {
      "there is non-tax-code income present and bank-accounts are not present" in {
        val mockTaxAccountRepository = mock[TaxAccountRepository]
        val mockBbsiRepository = mock[BbsiRepository]
        val json = taxAccountJsonWithIabds(npsIabdSummaries(1, Seq(19, 20, 21, 22, 23, 24, 25, 26, 62, 63, 64, 65, 66, 67, 68,
          69, 70, 71, 72, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 94, 116, 123, 125), 100))
        when(mockTaxAccountRepository.taxAccount(any(), any())(any())).
          thenReturn(Future.successful(json))
        when(mockBbsiRepository.bbsiDetails(any(), any())(any())).thenReturn(Future.successful(Seq.empty[BankAccount]))

        val sut = createSut(mockTaxAccountRepository, mockBbsiRepository)

        val result = Await.result(sut.incomes(nino, TaxYear()), 5.seconds)

        result.nonTaxCodeIncomes.otherNonTaxCodeIncomes mustBe Seq(
          OtherNonTaxCodeIncome(NonCodedIncome, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(Commission, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(OtherIncomeEarned, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(OtherIncomeNotEarned, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(PartTimeEarnings, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(Tips, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(OtherEarnings, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(CasualEarnings, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(ForeignDividendIncome, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(ForeignPropertyIncome, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(ForeignInterestAndOtherSavings, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(ForeignPensionsAndOtherIncome, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(StatePension, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(OccupationalPension, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(PublicServicesPension, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(ForcesPension, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(PersonalPensionAnnuity, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(Profit, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(BankOrBuildingSocietyInterest, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(UkDividend, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(UnitTrust, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(StockDividend, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(NationalSavings, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(SavingsBond, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(PurchasedLifeAnnuities, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(IncapacityBenefit, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(JobSeekersAllowance, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(EmploymentAndSupportAllowance, Some(1), 100, "desc")
        )

        result.nonTaxCodeIncomes.untaxedInterest mustBe Some(UntaxedInterest(UntaxedInterestIncome, Some(1), 100, "desc", Seq.empty[BankAccount]))
      }

      "non-tax-code income and bank accounts are present" in {
        val mockTaxAccountRepository = mock[TaxAccountRepository]
        val mockBbsiRepository = mock[BbsiRepository]
        val json = taxAccountJsonWithIabds(npsIabdSummaries(1, Seq(82), 100))
        when(mockTaxAccountRepository.taxAccount(any(), any())(any())).
          thenReturn(Future.successful(json))

        when(mockBbsiRepository.bbsiDetails(any(), any())(any())).thenReturn(Future.successful(Seq(bankAccount, bankAccount)))

        val sut = createSut(mockTaxAccountRepository, mockBbsiRepository)

        val result = Await.result(sut.incomes(nino, TaxYear()), 5.seconds)

        result.nonTaxCodeIncomes.untaxedInterest mustBe Some(UntaxedInterest(UntaxedInterestIncome, Some(1), 100, "desc",
          Seq(bankAccount, bankAccount)))
      }

      "bbsi api throws exception" in {
        val mockTaxAccountRepository = mock[TaxAccountRepository]
        val mockBbsiRepository = mock[BbsiRepository]
        val json = taxAccountJsonWithIabds(npsIabdSummaries(1, Seq(82), 100))
        when(mockTaxAccountRepository.taxAccount(any(), any())(any())).
          thenReturn(Future.successful(json))
        when(mockBbsiRepository.bbsiDetails(any(), any())(any())).thenReturn(Future.failed(new RuntimeException("Error")))

        val sut = createSut(mockTaxAccountRepository, mockBbsiRepository)

        val result = Await.result(sut.incomes(nino, TaxYear()), 5.seconds)

        result.nonTaxCodeIncomes.untaxedInterest mustBe Some(UntaxedInterest(UntaxedInterestIncome, Some(1), 100, "desc", Seq.empty[BankAccount]))
      }
    }
  }

  "taxCodeIncomeSource" must {
    "return a sequence of taxCodeIncomes" when {
      "provided with valid nino" in {
        val json = Json.obj(
          "taxYear" -> JsNumber(2017),
          "totalLiability" -> Json.obj(
            "untaxedInterest" -> Json.obj(
              "totalTaxableIncome" -> JsNumber(123)
            )
          ),
          "incomeSources" -> JsArray(Seq(Json.obj(
            "employmentId" -> JsNumber(1),
            "taxCode" -> JsString("1150L"),
            "name" -> JsString("Employer1"),
            "basisOperation" -> JsNumber(1),
            "employmentStatus" -> JsNumber(1)
          ),
            Json.obj(
              "employmentId" -> JsNumber(2),
              "taxCode" -> JsString("1100L"),
              "name" -> JsString("Employer2"),
              "basisOperation" -> JsNumber(2),
              "employmentStatus" -> JsNumber(1)
            ))
          ))

        val iabdJson = Json.arr()

        val mockTaxAccountRepository = mock[TaxAccountRepository]
        when(mockTaxAccountRepository.taxAccount(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
          .thenReturn(Future.successful(json))
        val mockIabdRepository = mock[IabdRepository]
        when(mockIabdRepository.iabds(any(), any())(any())).thenReturn(Future.successful(iabdJson))

        val sut = createSut(mockTaxAccountRepository, iabdRepository = mockIabdRepository)
        val result = Await.result(sut.taxCodeIncomes(nino, TaxYear()), 5 seconds)

        result mustBe Seq(TaxCodeIncome(EmploymentIncome, Some(1), BigDecimal(0),
          "EmploymentIncome", "1150L", "Employer1", Week1Month1BasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0), None),
          TaxCodeIncome(EmploymentIncome, Some(2), BigDecimal(0),
            "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0), None))
      }

      "iabd returns data for different employment" in {
        val json = Json.obj(
          "taxYear" -> JsNumber(2017),
          "totalLiability" -> Json.obj(
            "untaxedInterest" -> Json.obj(
              "totalTaxableIncome" -> JsNumber(123)
            )
          ),
          "incomeSources" -> JsArray(Seq(Json.obj(
            "employmentId" -> JsNumber(1),
            "taxCode" -> JsString("1150L"),
            "name" -> JsString("Employer1"),
            "basisOperation" -> JsNumber(1),
            "employmentStatus" -> JsNumber(1)
          ),
            Json.obj(
              "employmentId" -> JsNumber(2),
              "taxCode" -> JsString("1100L"),
              "name" -> JsString("Employer2"),
              "basisOperation" -> JsNumber(2),
              "employmentStatus" -> JsNumber(1)
            ))
          ))

        val iabdJson = Json.arr(
          Json.obj(
            "nino" -> nino.withoutSuffix,
            "taxYear" -> 2017,
            "type" -> 10,
            "source" -> 15,
            "grossAmount" -> JsNull,
            "receiptDate" -> JsNull,
            "captureDate" -> JsNull,
            "typeDescription" -> "Total gift aid Payments",
            "netAmount" -> 100
          ),
          Json.obj(
            "nino" -> nino.withoutSuffix,
            "employmentSequenceNumber" -> 10,
            "taxYear" -> 2017,
            "type" -> 27,
            "source" -> 15,
            "grossAmount" -> JsNull,
            "receiptDate" -> JsNull,
            "captureDate" -> JsNull,
            "typeDescription" -> "Total gift aid Payments",
            "netAmount" -> 100
          )
        )

        val mockTaxAccountRepository = mock[TaxAccountRepository]
        when(mockTaxAccountRepository.taxAccount(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
          .thenReturn(Future.successful(json))
        val mockIabdRepository = mock[IabdRepository]
        when(mockIabdRepository.iabds(any(), any())(any())).thenReturn(Future.successful(iabdJson))

        val sut = createSut(mockTaxAccountRepository, iabdRepository = mockIabdRepository)
        val result = Await.result(sut.taxCodeIncomes(nino, TaxYear()), 5 seconds)

        result mustBe Seq(TaxCodeIncome(EmploymentIncome, Some(1), BigDecimal(0),
          "EmploymentIncome", "1150L", "Employer1", Week1Month1BasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0), None),
          TaxCodeIncome(EmploymentIncome, Some(2), BigDecimal(0),
            "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0), None))
      }

      "iabd returns data for same employment" in {
        val json = Json.obj(
          "taxYear" -> JsNumber(2017),
          "totalLiability" -> Json.obj(
            "untaxedInterest" -> Json.obj(
              "totalTaxableIncome" -> JsNumber(123)
            )
          ),
          "incomeSources" -> JsArray(Seq(Json.obj(
            "employmentId" -> JsNumber(1),
            "taxCode" -> JsString("1150L"),
            "name" -> JsString("Employer1"),
            "basisOperation" -> JsNumber(1),
            "employmentStatus" -> JsNumber(1)
          ),
            Json.obj(
              "employmentId" -> JsNumber(2),
              "taxCode" -> JsString("1100L"),
              "name" -> JsString("Employer2"),
              "basisOperation" -> JsNumber(2),
              "employmentStatus" -> JsNumber(1)
            ))
          ))

        val iabdJson = Json.arr(
          Json.obj(
            "nino" -> nino.withoutSuffix,
            "employmentSequenceNumber" -> 1,
            "taxYear" -> 2017,
            "type" -> 10,
            "source" -> 15,
            "grossAmount" -> JsNull,
            "receiptDate" -> JsNull,
            "captureDate" -> JsNull,
            "typeDescription" -> "Total gift aid Payments",
            "netAmount" -> 100
          ),
          Json.obj(
            "nino" -> nino.withoutSuffix,
            "employmentSequenceNumber" -> 2,
            "taxYear" -> 2017,
            "type" -> 27,
            "source" -> 18,
            "grossAmount" -> JsNull,
            "receiptDate" -> JsNull,
            "captureDate" -> JsNull,
            "typeDescription" -> "Total gift aid Payments",
            "netAmount" -> 100
          )
        )

        val mockTaxAccountRepository = mock[TaxAccountRepository]
        when(mockTaxAccountRepository.taxAccount(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
          .thenReturn(Future.successful(json))
        val mockIabdRepository = mock[IabdRepository]
        when(mockIabdRepository.iabds(any(), any())(any())).thenReturn(Future.successful(iabdJson))

        val sut = createSut(mockTaxAccountRepository, iabdRepository = mockIabdRepository)
        val result = Await.result(sut.taxCodeIncomes(nino, TaxYear()), 5 seconds)

        result mustBe Seq(TaxCodeIncome(EmploymentIncome, Some(1), BigDecimal(0),
          "EmploymentIncome", "1150L", "Employer1", Week1Month1BasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0), Some(ManualTelephone)),
          TaxCodeIncome(EmploymentIncome, Some(2), BigDecimal(0),
            "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0), Some(AgentContact)))
      }

      "iabd returns data for same employment but code not present" in {
        val json = Json.obj(
          "taxYear" -> JsNumber(2017),
          "totalLiability" -> Json.obj(
            "untaxedInterest" -> Json.obj(
              "totalTaxableIncome" -> JsNumber(123)
            )
          ),
          "incomeSources" -> JsArray(Seq(Json.obj(
            "employmentId" -> JsNumber(1),
            "taxCode" -> JsString("1150L"),
            "name" -> JsString("Employer1"),
            "basisOperation" -> JsNumber(1),
            "employmentStatus" -> JsNumber(1)
          ),
            Json.obj(
              "employmentId" -> JsNumber(2),
              "taxCode" -> JsString("1100L"),
              "name" -> JsString("Employer2"),
              "basisOperation" -> JsNumber(2),
              "employmentStatus" -> JsNumber(1)
            ))
          ))

        val iabdJson = Json.arr(
          Json.obj(
            "nino" -> nino.withoutSuffix,
            "employmentSequenceNumber" -> 1,
            "taxYear" -> 2017,
            "type" -> 10,
            "source" -> 15,
            "grossAmount" -> JsNull,
            "receiptDate" -> JsNull,
            "captureDate" -> "10/04/2017",
            "typeDescription" -> "Total gift aid Payments",
            "netAmount" -> 100
          ),
          Json.obj(
            "nino" -> nino.withoutSuffix,
            "employmentSequenceNumber" -> 2,
            "taxYear" -> 2017,
            "type" -> 27,
            "source" -> 418,
            "grossAmount" -> JsNull,
            "receiptDate" -> "10/04/2017",
            "captureDate" -> "10/04/2017",
            "typeDescription" -> "Total gift aid Payments",
            "netAmount" -> 100
          )
        )

        val mockTaxAccountRepository = mock[TaxAccountRepository]
        when(mockTaxAccountRepository.taxAccount(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
          .thenReturn(Future.successful(json))
        val mockIabdRepository = mock[IabdRepository]
        when(mockIabdRepository.iabds(any(), any())(any())).thenReturn(Future.successful(iabdJson))

        val sut = createSut(mockTaxAccountRepository, iabdRepository = mockIabdRepository)
        val result = Await.result(sut.taxCodeIncomes(nino, TaxYear()), 5 seconds)

        result mustBe Seq(TaxCodeIncome(EmploymentIncome, Some(1), BigDecimal(0),
          "EmploymentIncome", "1150L", "Employer1", Week1Month1BasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0), Some(ManualTelephone),
          None, Some(LocalDate.parse("2017-04-10"))),
          TaxCodeIncome(EmploymentIncome, Some(2), BigDecimal(0),
            "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0), None,
            Some(LocalDate.parse("2017-04-10")), Some(LocalDate.parse("2017-04-10"))))
      }
    }
  }

  private val bankAccount = BankAccount(0, Some("123"), Some("123456"), Some("TEST"), 10.80, Some("Customer"), Some(1))

  private def npsIabdSummaries(empId: Int, types: Seq[Int], amount: Int): Seq[JsObject] = {
    types.map { tp =>
      Json.obj(
        "amount" -> amount,
        "type" -> tp,
        "npsDescription" -> "desc",
        "employmentId" -> empId,
        "estimatesPaySource" -> 1
      )
    }
  }

  private def taxAccountJsonWithIabds(incomeIabdSummaries: Seq[JsObject] = Seq.empty[JsObject],
                                      allowReliefIabdSummaries: Seq[JsObject] = Seq.empty[JsObject]): JsObject = {
    Json.obj(
      "taxAccountId" -> "id",
      "nino" -> nino.nino,
      "totalLiability" -> Json.obj(
        "nonSavings" -> Json.obj(
          "totalIncome" -> Json.obj(
            "iabdSummaries" -> JsArray(incomeIabdSummaries)
          ),
          "allowReliefDeducts" -> Json.obj(
            "iabdSummaries" -> JsArray(allowReliefIabdSummaries)
          )
        )
      )
    )
  }

  private val nino: Nino = new Generator(new Random).nextNino
  private implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("testSession")))

  private def createSut(taxAccountRepository: TaxAccountRepository = mock[TaxAccountRepository],
                        bbsiRepository: BbsiRepository = mock[BbsiRepository],
                        iabdRepository: IabdRepository = mock[IabdRepository]) = {
    new IncomeRepository(taxAccountRepository, bbsiRepository, iabdRepository)
  }
}

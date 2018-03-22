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

import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.controllers.FakeTaiPlayApplication
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.domain.taxAdjustments._
import uk.gov.hmrc.tai.model.domain.{CommunityInvestmentTaxCredit, EstimatedTaxYouOweThisYear, OutstandingDebt, UnderPaymentFromPreviousYear}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class TaxAccountSummaryRepositorySpec extends PlaySpec
  with MockitoSugar
  with FakeTaiPlayApplication {

  "TaxAccountSummary" must {
    "return totalEstimatedTax from the TaxAccountSummary connector" when {
      "underpayment from previous year present" in {
        val mockTaxAccountRepository = mock[TaxAccountRepository]
        when(mockTaxAccountRepository.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(taxAccountSummaryNpsJson))
        val mockCodingComponentRepository = mock[CodingComponentRepository]
        when(mockCodingComponentRepository.codingComponents(any(), any())(any())).thenReturn(Future.successful(
          Seq(
            codingComponent
          )))

        val sut = createSUT(mockTaxAccountRepository, mockCodingComponentRepository)
        val responseFuture = sut.taxAccountSummary(nino, TaxYear())

        val result = Await.result(responseFuture, 5 seconds)
        result mustBe BigDecimal(1211)
      }

      "outstanding debt present" in {
        val mockTaxAccountRepository = mock[TaxAccountRepository]
        when(mockTaxAccountRepository.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(taxAccountSummaryNpsJson))
        val mockCodingComponentRepository = mock[CodingComponentRepository]
        when(mockCodingComponentRepository.codingComponents(any(), any())(any())).thenReturn(Future.successful(
          Seq(
            codingComponent.copy(componentType = OutstandingDebt)
          )))

        val sut = createSUT(mockTaxAccountRepository, mockCodingComponentRepository)
        val responseFuture = sut.taxAccountSummary(nino, TaxYear())

        val result = Await.result(responseFuture, 5 seconds)
        result mustBe BigDecimal(1211)
      }

      "EstimatedTaxYouOweThisYear present" in {
        val mockTaxAccountRepository = mock[TaxAccountRepository]
        when(mockTaxAccountRepository.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(taxAccountSummaryNpsJson))
        val mockCodingComponentRepository = mock[CodingComponentRepository]
        when(mockCodingComponentRepository.codingComponents(any(), any())(any())).thenReturn(Future.successful(
          Seq(
            codingComponent.copy(componentType = EstimatedTaxYouOweThisYear)
          )))

        val sut = createSUT(mockTaxAccountRepository, mockCodingComponentRepository)
        val responseFuture = sut.taxAccountSummary(nino, TaxYear())

        val result = Await.result(responseFuture, 5 seconds)
        result mustBe BigDecimal(1211)
      }

      "all components" which {
        "can affect the totalTax are present" in {
          val mockTaxAccountRepository = mock[TaxAccountRepository]
          when(mockTaxAccountRepository.taxAccount(any(), any())(any()))
            .thenReturn(Future.successful(taxAccountSummaryNpsJson))
          val mockCodingComponentRepository = mock[CodingComponentRepository]
          when(mockCodingComponentRepository.codingComponents(any(), any())(any())).thenReturn(Future.successful(
            Seq(
              codingComponent,
              codingComponent.copy(componentType = OutstandingDebt),
              codingComponent.copy(componentType = EstimatedTaxYouOweThisYear),
              codingComponent.copy(componentType = CommunityInvestmentTaxCredit)
            )))

          val sut = createSUT(mockTaxAccountRepository, mockCodingComponentRepository)
          val responseFuture = sut.taxAccountSummary(nino, TaxYear())

          val result = Await.result(responseFuture, 5 seconds)
          result mustBe BigDecimal(1411)
        }
      }

      "no components" which {
        "can affect the totalTax are present" in {
          val mockTaxAccountRepository = mock[TaxAccountRepository]
          when(mockTaxAccountRepository.taxAccount(any(), any())(any()))
            .thenReturn(Future.successful(taxAccountSummaryNpsJson))
          val mockCodingComponentRepository = mock[CodingComponentRepository]
          when(mockCodingComponentRepository.codingComponents(any(), any())(any())).thenReturn(Future.successful(
            Seq(
              codingComponent.copy(componentType = CommunityInvestmentTaxCredit)
            )))

          val sut = createSUT(mockTaxAccountRepository, mockCodingComponentRepository)
          val responseFuture = sut.taxAccountSummary(nino, TaxYear())

          val result = Await.result(responseFuture, 5 seconds)
          result mustBe BigDecimal(1111)
        }
      }
    }

    "return tax adjustment components" when {
      "components are present" in {
        val mockTaxAccountRepository = mock[TaxAccountRepository]
        when(mockTaxAccountRepository.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(taxAccountSummaryNpsJson))
        val sut = createSUT(mockTaxAccountRepository, mock[CodingComponentRepository])

        val result = Await.result(sut.taxAdjustmentComponents(nino, TaxYear()), 5.seconds)

        result mustBe Some(TaxAdjustment(2500.5, Seq(
          TaxAdjustmentComponent(EnterpriseInvestmentSchemeRelief, 100),
          TaxAdjustmentComponent(ConcessionalRelief, 100.5),
          TaxAdjustmentComponent(MaintenancePayments, 200),
          TaxAdjustmentComponent(MarriedCouplesAllowance, 300),
          TaxAdjustmentComponent(DoubleTaxationRelief, 400),
          TaxAdjustmentComponent(ExcessGiftAidTax, 100),
          TaxAdjustmentComponent(ExcessWidowsAndOrphans, 100),
          TaxAdjustmentComponent(PensionPaymentsAdjustment, 200),
          TaxAdjustmentComponent(ChildBenefit, 300),
          TaxAdjustmentComponent(TaxOnBankBSInterest, 100),
          TaxAdjustmentComponent(TaxCreditOnUKDividends, 100),
          TaxAdjustmentComponent(TaxCreditOnForeignInterest, 200),
          TaxAdjustmentComponent(TaxCreditOnForeignIncomeDividends, 300)
        )))
      }
    }

    "return empty tax adjustment components" when {
      "no tax adjustment component is present" in {
        val mockTaxAccountRepository = mock[TaxAccountRepository]
        when(mockTaxAccountRepository.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(Json.obj()))
        val sut = createSUT(mockTaxAccountRepository, mock[CodingComponentRepository])

        val result = Await.result(sut.taxAdjustmentComponents(nino, TaxYear()), 5.seconds)

        result mustBe None
      }
    }
  }

  "Reliefs Giving Back Tax Components" must {
    "return only reliefs giving back tax components" in {
      val mockTaxAccountRepository = mock[TaxAccountRepository]
      when(mockTaxAccountRepository.taxAccount(any(), any())(any()))
        .thenReturn(Future.successful(taxAccountSummaryNpsJson))
      val sut = createSUT(mockTaxAccountRepository, mock[CodingComponentRepository])

      val result = Await.result(sut.reliefsGivingBackTaxComponents(nino, TaxYear()), 5.seconds)

      result mustBe Some(TaxAdjustment(1100.5, Seq(
        TaxAdjustmentComponent(EnterpriseInvestmentSchemeRelief, 100),
        TaxAdjustmentComponent(ConcessionalRelief, 100.5),
        TaxAdjustmentComponent(MaintenancePayments, 200),
        TaxAdjustmentComponent(MarriedCouplesAllowance, 300),
        TaxAdjustmentComponent(DoubleTaxationRelief, 400)
      )))
    }

    "return empty list " when {
      "reliefs giving back tax components are not present" in {
        val mockTaxAccountRepository = mock[TaxAccountRepository]
        when(mockTaxAccountRepository.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(Json.obj()))
        val sut = createSUT(mockTaxAccountRepository, mock[CodingComponentRepository])

        val result = Await.result(sut.reliefsGivingBackTaxComponents(nino, TaxYear()), 5.seconds)

        result mustBe None
      }
    }
  }

  "Other Tax Due Components" must {
    "return only other tax due components" in {
      val mockTaxAccountRepository = mock[TaxAccountRepository]
      when(mockTaxAccountRepository.taxAccount(any(), any())(any()))
        .thenReturn(Future.successful(taxAccountSummaryNpsJson))
      val sut = createSUT(mockTaxAccountRepository, mock[CodingComponentRepository])

      val result = Await.result(sut.otherTaxDueComponents(nino, TaxYear()), 5.seconds)

      result mustBe Some(TaxAdjustment(700, Seq(
        TaxAdjustmentComponent(ExcessGiftAidTax, 100),
        TaxAdjustmentComponent(ExcessWidowsAndOrphans, 100),
        TaxAdjustmentComponent(PensionPaymentsAdjustment, 200),
        TaxAdjustmentComponent(ChildBenefit, 300)
      )))
    }

    "return empty list " when {
      "other tax components are not present" in {
        val mockTaxAccountRepository = mock[TaxAccountRepository]
        when(mockTaxAccountRepository.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(Json.obj()))
        val sut = createSUT(mockTaxAccountRepository, mock[CodingComponentRepository])

        val result = Await.result(sut.otherTaxDueComponents(nino, TaxYear()), 5.seconds)

        result mustBe None
      }
    }
  }

  "Already Taxed At Sources Components" must {
    "return only already taxed at source components" in {
      val mockTaxAccountRepository = mock[TaxAccountRepository]
      when(mockTaxAccountRepository.taxAccount(any(), any())(any()))
        .thenReturn(Future.successful(taxAccountSummaryNpsJson))
      val sut = createSUT(mockTaxAccountRepository, mock[CodingComponentRepository])

      val result = Await.result(sut.alreadyTaxedAtSourceComponents(nino, TaxYear()), 5.seconds)

      result mustBe Some(TaxAdjustment(700, Seq(
        TaxAdjustmentComponent(TaxOnBankBSInterest, 100),
        TaxAdjustmentComponent(TaxCreditOnUKDividends, 100),
        TaxAdjustmentComponent(TaxCreditOnForeignInterest, 200),
        TaxAdjustmentComponent(TaxCreditOnForeignIncomeDividends, 300)
      )))
    }

    "return empty list " when {
      "already tax at source components are not present" in {
        val mockTaxAccountRepository = mock[TaxAccountRepository]
        when(mockTaxAccountRepository.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(Json.obj()))
        val sut = createSUT(mockTaxAccountRepository, mock[CodingComponentRepository])

        val result = Await.result(sut.alreadyTaxedAtSourceComponents(nino, TaxYear()), 5.seconds)

        result mustBe None
      }
    }
  }

  private val nino: Nino = new Generator(new Random).nextNino

  private implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("testSession")))

  private val codingComponent = CodingComponent(UnderPaymentFromPreviousYear, Some(1), 100, "", Some(100))

  private val taxAccountSummaryNpsJson = Json.obj(
    "totalLiability" -> Json.obj(
      "totalLiability" -> 1111,
      "reliefsGivingBackTax" -> Json.obj(
        "enterpriseInvestmentSchemeRelief" -> 100,
        "concessionalRelief" -> 100.50,
        "maintenancePayments" -> 200,
        "marriedCouplesAllowance" -> 300,
        "doubleTaxationRelief" -> 400
      ),
      "otherTaxDue" -> Json.obj(
        "excessGiftAidTax" -> 100,
        "excessWidowsAndOrphans" -> 100,
        "pensionPaymentsAdjustment" -> 200,
        "childBenefit" -> 300
      ),
      "alreadyTaxedAtSource" -> Json.obj(
        "taxOnBankBSInterest" -> 100,
        "taxCreditOnUKDividends" -> 100,
        "taxCreditOnForeignInterest" -> 200,
        "taxCreditOnForeignIncomeDividends" -> 300
      )
    )
  )

  private def createSUT(taxAccountRepository: TaxAccountRepository, codingComponentRepository: CodingComponentRepository) =
    new TaxAccountSummaryRepository(taxAccountRepository, codingComponentRepository)
}

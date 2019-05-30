/*
 * Copyright 2019 HM Revenue & Customs
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

import org.mockito.Matchers
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.factory.TaxAccountHistoryFactory
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class CodingComponentRepositorySpec extends PlaySpec
  with MockitoSugar {

  "codingComponents" should {
    "return empty list of coding components" when {
      "iabd connector returns empty list and tax account connector returns json with no NpsComponents of interest" in {
        val mockTaxAccountRepository = mock[TaxAccountRepository]

        when(mockTaxAccountRepository.taxAccount(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
          .thenReturn(Future.successful(emptyJson))

        val sut = testCodingComponentRepository(mockTaxAccountRepository)
        val result = Await.result(sut.codingComponents(nino, TaxYear()), 5 seconds)

        result mustBe Nil
      }
    }

    "return coding components sourced from nps iabd list, nps tax account and tax code income source" when {
      "income source has pension available" in {
        val mockTaxAccountRepository = mock[TaxAccountRepository]

        when(mockTaxAccountRepository.taxAccount(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
          .thenReturn(Future.successful(primaryIncomeDeductionsNpsJson))

        val sut = testCodingComponentRepository(mockTaxAccountRepository)
        val result = Await.result(sut.codingComponents(nino, TaxYear()), 5 seconds)

        result mustBe Seq(
          CodingComponent(EstimatedTaxYouOweThisYear, None, 10, "Estimated Tax You Owe This Year"),
          CodingComponent(UnderPaymentFromPreviousYear, None, 10, "Underpayment form previous year"),
          CodingComponent(OutstandingDebt, None, 10, "Outstanding Debt Restriction")
        )
      }
    }
  }

  "codingComponentsForTaxCodeId" should {
    "returns a Success[Seq[CodingComponent]] for valid json of income sources" in {
      val mockTaxAccountRepository = mock[TaxAccountRepository]

      val taxCodeId = 1

      val expected = List[CodingComponent](
        CodingComponent(PersonalAllowancePA, None, 11850, "Personal Allowance", Some(11850))
      )

      val json = TaxAccountHistoryFactory.basicIncomeSourcesJson(nino)

      when(mockTaxAccountRepository.taxAccountForTaxCodeId(Matchers.eq(nino), Matchers.eq(taxCodeId))(any()))
        .thenReturn(Future.successful(json))

      val repository = testCodingComponentRepository(mockTaxAccountRepository)

      val result = Await.result(repository.codingComponentsForTaxCodeId(nino, taxCodeId), 5 seconds)

      result mustBe expected
    }

    "returns a Success[Seq[CodingComponent]] for valid json of total liabilities" in {
      val mockTaxAccountRepository = mock[TaxAccountRepository]

      val taxCodeId = 1

      val expected = List[CodingComponent](
        CodingComponent(CarBenefit, Some(1), 2000, "Car Benefit", None)
      )

      val json = TaxAccountHistoryFactory.basicTotalLiabilityJson(nino)

      when(mockTaxAccountRepository.taxAccountForTaxCodeId(Matchers.eq(nino), Matchers.eq(taxCodeId))(any()))
        .thenReturn(Future.successful(json))

      val repository = testCodingComponentRepository(mockTaxAccountRepository)

      val result = Await.result(repository.codingComponentsForTaxCodeId(nino, taxCodeId), 5 seconds)

      result mustBe expected
    }

    "returns a Success[Seq[CodingComponent]] for valid json of total liabilities and income sources" in {
      val mockTaxAccountRepository = mock[TaxAccountRepository]

      val taxCodeId = 1

      val expected = List[CodingComponent](
        CodingComponent(PersonalAllowancePA, None, 11850, "Personal Allowance", Some(11850)),
        CodingComponent(CarBenefit, Some(1), 2000, "Car Benefit", None)
      )

      val json = TaxAccountHistoryFactory.combinedIncomeSourcesTotalLiabilityJson(nino)

      when(mockTaxAccountRepository.taxAccountForTaxCodeId(Matchers.eq(nino), Matchers.eq(taxCodeId))(any()))
        .thenReturn(Future.successful(json))

      val repository = testCodingComponentRepository(mockTaxAccountRepository)

      val result = Await.result(repository.codingComponentsForTaxCodeId(nino, taxCodeId), 5 seconds)

      result mustBe expected
    }
  }

  private val nino: Nino = new Generator(new Random).nextNino

  private implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("testSession")))

  private val emptyJson = Json.arr()

  private val primaryIncomeDeductionsNpsJson = Json.obj(
    "taxAccountId" -> JsString("id"),
    "nino" -> JsString(nino.nino),
    "incomeSources" -> JsArray(Seq(Json.obj(
      "employmentId" -> JsNumber(1),
      "employmentType" -> JsNumber(1),
      "taxCode" -> JsString("1150L"),
      "pensionIndicator" -> JsBoolean(true),
      "basisOperation" -> JsNumber(1),
      "employmentStatus" -> JsNumber(1),
      "name" -> JsString("employer"),
      "deductions" -> JsArray(Seq(Json.obj(
        "npsDescription" -> JsString("Estimated Tax You Owe This Year"),
        "amount" -> JsNumber(10),
        "type" -> JsNumber(45)
      ), Json.obj(
        "npsDescription" -> JsString("Underpayment form previous year"),
        "amount" -> JsNumber(10),
        "type" -> JsNumber(35)
      ), Json.obj(
        "npsDescription" -> JsString("Outstanding Debt Restriction"),
        "amount" -> JsNumber(10),
        "type" -> JsNumber(41)
      ), Json.obj(
        "npsDescription" -> JsString("Something we aren't interested in"),
        "amount" -> JsNumber(10),
        "type" -> JsNumber(888)
      )))
    )))
  )

  private def testCodingComponentRepository(taxAccountRepository: TaxAccountRepository) =
    new CodingComponentRepository(taxAccountRepository)
}
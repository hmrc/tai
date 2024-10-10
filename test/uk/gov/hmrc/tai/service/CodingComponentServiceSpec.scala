/*
 * Copyright 2024 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{any, eq => meq}
import play.api.libs.json._
import uk.gov.hmrc.tai.connectors.TaxAccountConnector
import uk.gov.hmrc.tai.factory.TaxAccountHistoryFactory
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class CodingComponentServiceSpec extends BaseSpec {

  private val emptyJson = Json.arr()
  private val mockTaxAccountConnector: TaxAccountConnector = mock[TaxAccountConnector]
  private val taxCodeId = 1

  "codingComponents" must {
    "return empty list of coding components" when {
      "connector returns json with no NpsComponents of interest" in {
        when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(emptyJson))

        val sut: CodingComponentService = new CodingComponentService(mockTaxAccountConnector)

        val result = sut.codingComponents(nino, TaxYear()).futureValue
        result mustBe Nil
      }
    }

    "return a list of coding components" when {
      "connector returns json with more than one tax components" in {
        when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(npsJsonResponse))

        val codingComponentList: Seq[CodingComponent] = Seq(
          CodingComponent(EstimatedTaxYouOweThisYear, None, 10, "Estimated Tax You Owe This Year"),
          CodingComponent(UnderPaymentFromPreviousYear, None, 10, "Underpayment form previous year"),
          CodingComponent(OutstandingDebt, None, 10, "Outstanding Debt Restriction")
        )

        val sut: CodingComponentService = new CodingComponentService(mockTaxAccountConnector)

        val result = sut.codingComponents(nino, TaxYear()).futureValue
        result mustBe codingComponentList
      }
    }
  }

  "codingComponentsForTaxCodeId" must {
    "return empty list of coding components for json with no NpsComponents of interest" in {
      when(mockTaxAccountConnector.taxAccountHistory(meq(nino), meq(taxCodeId))(any()))
        .thenReturn(Future.successful(emptyJson))

      val sut: CodingComponentService = new CodingComponentService(mockTaxAccountConnector)

      val result = sut.codingComponentsForTaxCodeId(nino, taxCodeId).futureValue
      result mustBe Nil
    }

    "return a Success[Seq[CodingComponent]] for valid json of income sources" in {
      val expected = List[CodingComponent](
        CodingComponent(PersonalAllowancePA, None, 11850, "Personal Allowance", Some(11850)),
        CodingComponent(BRDifferenceTaxReduction, None, 10000, "BR Difference Tax Reduction", Some(10000))
      )

      val basicIncomeSourcesJson = TaxAccountHistoryFactory.basicIncomeSourcesJson(nino)
      when(mockTaxAccountConnector.taxAccountHistory(meq(nino), meq(taxCodeId))(any()))
        .thenReturn(Future.successful(basicIncomeSourcesJson))

      val sut: CodingComponentService = new CodingComponentService(mockTaxAccountConnector)

      val result = sut.codingComponentsForTaxCodeId(nino, taxCodeId).futureValue
      result mustBe expected
    }

    "returns a Success[Seq[CodingComponent]] for valid json of total liabilities" in {
      val expected = List[CodingComponent](
        CodingComponent(CarBenefit, Some(1), 2000, "Car Benefit", None)
      )

      val basicTotalLiabilityJson = TaxAccountHistoryFactory.basicTotalLiabilityJson(nino)
      when(mockTaxAccountConnector.taxAccountHistory(meq(nino), meq(taxCodeId))(any()))
        .thenReturn(Future.successful(basicTotalLiabilityJson))

      val sut: CodingComponentService = new CodingComponentService(mockTaxAccountConnector)

      val result = sut.codingComponentsForTaxCodeId(nino, taxCodeId).futureValue
      result mustBe expected
    }

    "returns a Success[Seq[CodingComponent]] for valid json of total liabilities and income sources" in {
      val expected = List[CodingComponent](
        CodingComponent(PersonalAllowancePA, None, 11850, "Personal Allowance", Some(11850)),
        CodingComponent(BRDifferenceTaxReduction, None, 10000, "BR Difference Tax Reduction", Some(10000)),
        CodingComponent(CarBenefit, Some(1), 2000, "Car Benefit", None)
      )

      val combinedJson = TaxAccountHistoryFactory.combinedIncomeSourcesTotalLiabilityJson(nino)
      when(mockTaxAccountConnector.taxAccountHistory(meq(nino), meq(taxCodeId))(any()))
        .thenReturn(Future.successful(combinedJson))

      val sut: CodingComponentService = new CodingComponentService(mockTaxAccountConnector)

      val result = sut.codingComponentsForTaxCodeId(nino, taxCodeId).futureValue
      result mustBe expected
    }
  }

  private val npsJsonResponse: JsObject = Json.obj(
    "taxAccountId" -> JsString("id"),
    "nino"         -> JsString(nino.nino),
    "incomeSources" -> JsArray(
      Seq(
        Json.obj(
          "employmentId"     -> JsNumber(1),
          "employmentType"   -> JsNumber(1),
          "taxCode"          -> JsString("1150L"),
          "pensionIndicator" -> JsBoolean(true),
          "basisOperation"   -> JsNumber(1),
          "employmentStatus" -> JsNumber(1),
          "name"             -> JsString("employer"),
          "deductions" -> JsArray(
            Seq(
              Json.obj(
                "npsDescription" -> JsString("Estimated Tax You Owe This Year"),
                "amount"         -> JsNumber(10),
                "type"           -> JsNumber(45)
              ),
              Json.obj(
                "npsDescription" -> JsString("Underpayment form previous year"),
                "amount"         -> JsNumber(10),
                "type"           -> JsNumber(35)
              ),
              Json.obj(
                "npsDescription" -> JsString("Outstanding Debt Restriction"),
                "amount"         -> JsNumber(10),
                "type"           -> JsNumber(41)
              ),
              Json.obj(
                "npsDescription" -> JsString("Something we aren't interested in"),
                "amount"         -> JsNumber(10),
                "type"           -> JsNumber(888)
              )
            )
          )
        )
      )
    )
  )
}

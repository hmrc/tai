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

import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.{reset, when}
import play.api.libs.json.*
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.connectors.TaxAccountConnector
import uk.gov.hmrc.tai.model.admin.HipTaxAccountHistoryToggle
import uk.gov.hmrc.tai.model.domain.*
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future
import scala.io.Source

class CodingComponentServiceSpec extends BaseSpec {

  private val basePath = "test/resources/data/TaxAccount/CodingComponentService/hip/"
  private val basePathSquid = "test/resources/data/TaxAccount/CodingComponentService/nps/"
  private def readFile(fileName: String, backend: String = "hip"): JsValue = {
    val jsonFilePath = if (backend == "hip") { basePath + fileName }
    else { basePathSquid + fileName }
    val bufferedSource = Source.fromFile(jsonFilePath)
    val source = bufferedSource.mkString("")
    bufferedSource.close()
    Json.parse(source)
  }

  private val emptyJson = Json.arr()
  private val mockTaxAccountConnector: TaxAccountConnector = mock[TaxAccountConnector]
  private val mockFeatureFlagService: FeatureFlagService = mock[FeatureFlagService]
  private val taxCodeId = 1

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockFeatureFlagService)
    reset(mockTaxAccountConnector)
    when(mockFeatureFlagService.get(meq[FeatureFlagName](HipTaxAccountHistoryToggle)))
      .thenReturn(Future.successful(FeatureFlag(HipTaxAccountHistoryToggle, isEnabled = true)))
    ()
  }

  "codingComponents" must {
    "return empty list of coding components" when {
      "connector returns json with no NpsComponents of interest" in {
        when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(emptyJson))

        val sut: CodingComponentService = new CodingComponentService(mockTaxAccountConnector, mockFeatureFlagService)

        val result = sut.codingComponents(nino, TaxYear()).futureValue
        result mustBe Nil
      }
    }

    "return a list of coding components" when {
      "connector returns json with more than one tax components" in {
        when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(readFile("TC01.json")))
        val codingComponentList: Seq[CodingComponent] = Seq(
          CodingComponent(EstimatedTaxYouOweThisYear, None, 10, "Non-qualifying Relocation Expenses", None),
          CodingComponent(UnderPaymentFromPreviousYear, None, 10, "Van Benefit", None),
          CodingComponent(OutstandingDebt, None, 10, "Educational Services", None)
        )

        val sut: CodingComponentService = new CodingComponentService(mockTaxAccountConnector, mockFeatureFlagService)

        val result = sut.codingComponents(nino, TaxYear()).futureValue
        result mustBe codingComponentList

      }
    }
  }

  "codingComponentsForTaxCodeId" must {
    "return empty list of coding components for json with no NpsComponents of interest" in {
      when(mockTaxAccountConnector.taxAccountHistory(meq(nino), meq(taxCodeId))(any()))
        .thenReturn(Future.successful(emptyJson))

      val sut: CodingComponentService = new CodingComponentService(mockTaxAccountConnector, mockFeatureFlagService)

      val result = sut.codingComponentsForTaxCodeId(nino, taxCodeId).futureValue
      result mustBe Nil
    }

    "return a Success[Seq[CodingComponent]] for valid json of income sources" in {
      val expected = List[CodingComponent](
        CodingComponent(PersonalAllowancePA, None, 11850, "Loan Interest Amount", Some(11850)),
        CodingComponent(BRDifferenceTaxReduction, None, 10000, "Taxable Expenses Benefit", Some(10000))
      )
      when(mockTaxAccountConnector.taxAccountHistory(meq(nino), meq(taxCodeId))(any()))
        .thenReturn(Future.successful(readFile("TC02.json")))
      val sut: CodingComponentService = new CodingComponentService(mockTaxAccountConnector, mockFeatureFlagService)

      val result = sut.codingComponentsForTaxCodeId(nino, taxCodeId).futureValue
      result mustBe expected

    }

    "returns a Success[Seq[CodingComponent]] for valid json of total liabilities" in {
      val expected = List[CodingComponent](
        CodingComponent(CarBenefit, Some(1), 2000, "Car Benefit", None)
      )
      when(mockTaxAccountConnector.taxAccountHistory(meq(nino), meq(taxCodeId))(any()))
        .thenReturn(Future.successful(readFile("TC03.json")))
      val sut: CodingComponentService = new CodingComponentService(mockTaxAccountConnector, mockFeatureFlagService)

      val result = sut.codingComponentsForTaxCodeId(nino, taxCodeId).futureValue
      result mustBe expected
    }

    "returns a Success[Seq[CodingComponent]] for valid json of total liabilities and income sources" in {
      val expected = List[CodingComponent](
        CodingComponent(PersonalAllowancePA, None, 11850, "Loan Interest Amount", Some(11850)),
        CodingComponent(BRDifferenceTaxReduction, None, 10000, "Taxable Expenses Benefit", Some(10000)),
        CodingComponent(CarBenefit, Some(1), 2000, "Car Benefit", None)
      )

      when(mockTaxAccountConnector.taxAccountHistory(meq(nino), meq(taxCodeId))(any()))
        .thenReturn(Future.successful(readFile("TC04.json")))
      val sut: CodingComponentService = new CodingComponentService(mockTaxAccountConnector, mockFeatureFlagService)

      val result = sut.codingComponentsForTaxCodeId(nino, taxCodeId).futureValue
      result mustBe expected

    }

    "returns a Success[Seq[CodingComponent]] for valid json of total liabilities and income sources when hip is disabled" in {
      val expected = List[CodingComponent](
        CodingComponent(PersonalAllowancePA, None, 11850, "Personal Allowance", Some(11850)),
        CodingComponent(BRDifferenceTaxReduction, None, 10000, "BR Difference Tax Reduction", Some(10000)),
        CodingComponent(CarBenefit, Some(1), 2000, "Car Benefit", None)
      )
      reset(mockFeatureFlagService)
      when(mockFeatureFlagService.get(meq[FeatureFlagName](HipTaxAccountHistoryToggle)))
        .thenReturn(Future.successful(FeatureFlag(HipTaxAccountHistoryToggle, isEnabled = false)))
      when(mockTaxAccountConnector.taxAccountHistory(meq(nino), meq(taxCodeId))(any()))
        .thenReturn(Future.successful(readFile("TC04.json", "squid")))

      val sut: CodingComponentService = new CodingComponentService(mockTaxAccountConnector, mockFeatureFlagService)

      val result = sut.codingComponentsForTaxCodeId(nino, taxCodeId).futureValue
      result mustBe expected

    }
  }
}

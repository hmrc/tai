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

package uk.gov.hmrc.tai.service.helper

import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.when
import play.api.libs.json.*
import uk.gov.hmrc.tai.connectors.TaxAccountConnector
import uk.gov.hmrc.tai.model.domain.income.*
import uk.gov.hmrc.tai.model.domain.EmploymentIncome
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.BaseSpec
import uk.gov.hmrc.http.NotFoundException

import scala.concurrent.Future

class TaxCodeIncomeHelperSpec extends BaseSpec {
  private val mockTaxAccountConnector = mock[TaxAccountConnector]
  private def createSut() = new TaxCodeIncomeHelper(mockTaxAccountConnector)

  private val taxAccountJson: JsObject = Json
    .parse("""{
             |   "nationalInsuranceNumber":"AA000003",
             |   "taxYear":2023,
             |   "totalLiabilityDetails":{
             |      "untaxedInterest":{
             |         "totalTaxableIncome":123
             |      }
             |   },
             |   "employmentDetailsList":[
             |      {
             |         "employmentSequenceNumber":1,
             |         "employmentStatus":"Live",
             |         "payeSchemeOperatorName":"Employer1",
             |         "taxCode":"1150L",
             |         "basisOfOperation":"Week1/Month1"
             |      },
             |      {
             |         "employmentSequenceNumber":2,
             |         "employmentStatus":"Live",
             |         "payeSchemeOperatorName":"Employer2",
             |         "taxCode":"1100L",
             |         "basisOfOperation":"Cumulative"
             |      }
             |   ]
             |}""".stripMargin)
    .as[JsObject]

  private val taxAccountJsonWithTaxableIncome: JsObject = Json
    .parse("""{
             |   "nationalInsuranceNumber":"AA000003",
             |   "taxYear":2023,
             |   "employmentDetailsList":[
             |      {
             |         "employmentSequenceNumber":1,
             |         "employmentStatus":"Live",
             |         "payeSchemeOperatorName":"Employer1",
             |         "taxCode":"1150L",
             |         "basisOfOperation":"Week1/Month1",
             |         "payAndTax":{
             |            "totalIncomeDetails":{
             |               "amount":2500,
             |               "summaryIABDDetailsList":[
             |                  {
             |                     "amount":2500,
             |                     "type":"New Estimated Pay (027)",
             |                     "employmentSequenceNumber":1
             |                  }
             |               ],
             |               "summaryIABDEstimatedPayDetailsList":[
             |                  {
             |                     "amount":2500,
             |                     "type":"New Estimated Pay (027)",
             |                     "employmentSequenceNumber":1
             |                  }
             |               ]
             |            }
             |         }
             |      }
             |   ]
             |}""".stripMargin)
    .as[JsObject]

  "fetchTaxCodeIncomes" must {
    "return a sequence of taxCodeIncomes" when {
      "provided with valid nino" in {
        when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxAccountJson))

        val result = createSut().fetchTaxCodeIncomes(nino, TaxYear()).futureValue

        result mustBe Seq(
          TaxCodeIncome(
            EmploymentIncome,
            Some(1),
            0,
            "EmploymentIncome",
            "1150L",
            "Employer1",
            Week1Month1BasisOperation,
            Live,
            0,
            0,
            0
          ),
          TaxCodeIncome(
            EmploymentIncome,
            Some(2),
            0,
            "EmploymentIncome",
            "1100L",
            "Employer2",
            OtherBasisOperation,
            Live,
            0,
            0,
            0
          )
        )

      }

      "iabd returns data for different employment" in {
        when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxAccountJson))

        val result = createSut().fetchTaxCodeIncomes(nino, TaxYear()).futureValue

        result mustBe Seq(
          TaxCodeIncome(
            EmploymentIncome,
            Some(1),
            0,
            "EmploymentIncome",
            "1150L",
            "Employer1",
            Week1Month1BasisOperation,
            Live,
            0,
            0,
            0
          ),
          TaxCodeIncome(
            EmploymentIncome,
            Some(2),
            0,
            "EmploymentIncome",
            "1100L",
            "Employer2",
            OtherBasisOperation,
            Live,
            0,
            0,
            0
          )
        )
      }

      "iabd returns data for same employment" in {
        when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxAccountJson))

        val result = createSut().fetchTaxCodeIncomes(nino, TaxYear()).futureValue

        result mustBe Seq(
          TaxCodeIncome(
            EmploymentIncome,
            Some(1),
            0,
            "EmploymentIncome",
            "1150L",
            "Employer1",
            Week1Month1BasisOperation,
            Live,
            0,
            0,
            0
          ),
          TaxCodeIncome(
            EmploymentIncome,
            Some(2),
            0,
            "EmploymentIncome",
            "1100L",
            "Employer2",
            OtherBasisOperation,
            Live,
            0,
            0,
            0
          )
        )
      }

      "iabd returns data for same employment but code not present" in {
        when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxAccountJson))

        val result = createSut().fetchTaxCodeIncomes(nino, TaxYear()).futureValue

        result mustBe Seq(
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
            BigDecimal(0)
          )
        )

      }
    }
  }

  "incomeAmountForEmploymentId" must {
    "return the income amount as a string" when {
      "the employmentId match with income details" in {
        val employmentId = 1
        when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxAccountJsonWithTaxableIncome))

        val result = createSut().incomeAmountForEmploymentId(nino, TaxYear(), employmentId).futureValue
        result mustBe Some("2500")
      }
    }

    "return None" when {
      "the employmentId does not match with any income details" in {
        val employmentId = 123456
        when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxAccountJsonWithTaxableIncome))

        val result = createSut().incomeAmountForEmploymentId(nino, TaxYear(), employmentId).futureValue
        result mustBe None
      }

      "tax account details returns not found" in {
        val employmentId = 123456
        when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.failed(new NotFoundException("Not found")))

        val result = createSut().incomeAmountForEmploymentId(nino, TaxYear(), employmentId).futureValue
        result mustBe None
      }
    }
  }
}

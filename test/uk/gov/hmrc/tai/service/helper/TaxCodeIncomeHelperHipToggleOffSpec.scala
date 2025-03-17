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
import uk.gov.hmrc.tai.model.domain.{EmploymentIncome, IabdDetails}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.IabdService
import uk.gov.hmrc.tai.util.BaseSpec

import java.time.LocalDate
import scala.concurrent.Future

class TaxCodeIncomeHelperHipToggleOffSpec extends BaseSpec {
  private val mockTaxAccountConnector = mock[TaxAccountConnector]
  private val mockIabdService = mock[IabdService]
  private def createSut() = new TaxCodeIncomeHelper(mockTaxAccountConnector, mockIabdService)

  private val taxAccountJson: JsObject = Json.obj(
    "nino"    -> nino,
    "taxYear" -> JsNumber(2022),
    "totalLiability" -> Json.obj(
      "untaxedInterest" -> Json.obj(
        "totalTaxableIncome" -> JsNumber(123)
      )
    ),
    "incomeSources" -> JsArray(
      Seq(
        Json.obj(
          "employmentId"     -> JsNumber(1),
          "taxCode"          -> JsString("1150L"),
          "name"             -> JsString("Employer1"),
          "basisOperation"   -> JsNumber(1),
          "employmentStatus" -> JsNumber(1)
        ),
        Json.obj(
          "employmentId"     -> JsNumber(2),
          "taxCode"          -> JsString("1100L"),
          "name"             -> JsString("Employer2"),
          "basisOperation"   -> JsNumber(2),
          "employmentStatus" -> JsNumber(1)
        )
      )
    )
  )

  private val taxAccountJsonWithTaxableIncome: JsObject = Json.obj(
    "nino"    -> nino,
    "taxYear" -> JsNumber(2022),
    "incomeSources" -> JsArray(
      Seq(
        Json.obj(
          "employmentId"     -> JsNumber(1),
          "taxCode"          -> JsString("1150L"),
          "name"             -> JsString("Employer1"),
          "basisOperation"   -> JsNumber(1),
          "employmentStatus" -> JsNumber(1),
          "payAndTax" -> Json.obj(
            "totalIncome" -> Json.obj(
              "amount" -> JsNumber(2500),
              "iabdSummaries" -> JsArray(
                Seq(
                  Json.obj(
                    "amount"         -> JsNumber(2500),
                    "type"           -> JsNumber(27),
                    "npsDescription" -> JsString("New Estimated Pay"),
                    "employmentId"   -> JsNumber(1)
                  )
                )
              )
            )
          )
        )
      )
    )
  )

  "fetchTaxCodeIncomes" must {
    "return a sequence of taxCodeIncomes" when {
      "provided with valid nino" in {
        val iabdDetailsSeq = Seq.empty[IabdDetails]
        when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxAccountJson))
        when(mockIabdService.retrieveIabdDetails(any(), any())(any()))
          .thenReturn(Future.successful(iabdDetailsSeq))

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
            BigDecimal(0),
            None
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
            BigDecimal(0),
            None
          )
        )
      }

      "iabd returns data for different employment" in {
        val iabdDetailsSeq = Seq(
          IabdDetails(
            Some(nino.withoutSuffix),
            Some(10),
            Some(15),
            Some(27),
            None,
            None
          )
        )

        when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxAccountJson))
        when(mockIabdService.retrieveIabdDetails(any(), any())(any())).thenReturn(Future.successful(iabdDetailsSeq))
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
            BigDecimal(0),
            None
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
            BigDecimal(0),
            None
          )
        )
      }

      "iabd returns data for same employment" in {
        val iabdDetailsSeq = Seq(
          IabdDetails(
            Some(nino.withoutSuffix),
            Some(2),
            Some(18),
            Some(27),
            None,
            None
          )
        )

        when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxAccountJson))

        when(mockIabdService.retrieveIabdDetails(any(), any())(any())).thenReturn(Future.successful(iabdDetailsSeq))

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
            BigDecimal(0),
            Some(AgentContact)
          )
        )
      }

      "iabd returns data for same employment but code not present" in {
        val iabdDetailsSeq = Seq(
          IabdDetails(
            Some(nino.withoutSuffix),
            Some(2),
            Some(418),
            Some(27),
            Some(LocalDate.parse("2017-04-10")),
            Some(LocalDate.parse("2017-04-10"))
          )
        )

        when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxAccountJson))

        when(mockIabdService.retrieveIabdDetails(any(), any())(any())).thenReturn(Future.successful(iabdDetailsSeq))

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
            BigDecimal(0),
            None,
            Some(LocalDate.parse("2017-04-10")),
            Some(LocalDate.parse("2017-04-10"))
          )
        )
      }
    }
  }

  "incomeAmountForEmploymentId" must {
    "return the income amount as a string" when {
      "the employmentId match with income details" in {
        val employmentId = 1
        val iabdDetailsSeq = Seq.empty[IabdDetails]
        when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxAccountJsonWithTaxableIncome))
        when(mockIabdService.retrieveIabdDetails(any(), any())(any()))
          .thenReturn(Future.successful(iabdDetailsSeq))

        val result = createSut().incomeAmountForEmploymentId(nino, TaxYear(), employmentId).futureValue
        result mustBe Some("2500")
      }
    }

    "return None" when {
      "the employmentId does not match with any income details" in {
        val employmentId = 123456
        val iabdDetailsSeq = Seq.empty[IabdDetails]
        when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxAccountJsonWithTaxableIncome))
        when(mockIabdService.retrieveIabdDetails(any(), any())(any()))
          .thenReturn(Future.successful(iabdDetailsSeq))

        val result = createSut().incomeAmountForEmploymentId(nino, TaxYear(), employmentId).futureValue
        result mustBe None
      }
    }
  }

  "fetchIabdDetails" must {
    "return a sequence of IabdIncome when provided with valid nino and tax year" in {
      val iabdDetailsSeq = Seq(
        IabdDetails(
          Some(nino.withoutSuffix),
          Some(1),
          Some(10),
          Some(27),
          Some(LocalDate.parse("2023-04-10")),
          Some(LocalDate.parse("2023-04-15")),
          Some(10)
        ),
        IabdDetails(Some(nino.withoutSuffix), Some(2), Some(20), Some(30), None, None, None)
      )

      when(mockIabdService.retrieveIabdDetails(meq(nino), meq(TaxYear()))(any()))
        .thenReturn(Future.successful(iabdDetailsSeq))

      val result = createSut().fetchIabdDetails(nino, TaxYear()).futureValue

      result mustBe Seq(
        IabdIncome(
          Some(1),
          None,
          Some(LocalDate.parse("2023-04-10")),
          Some(LocalDate.parse("2023-04-15")),
          10
        ),
        IabdIncome(Some(2), None, None, None, null)
      )
    }
  }
}

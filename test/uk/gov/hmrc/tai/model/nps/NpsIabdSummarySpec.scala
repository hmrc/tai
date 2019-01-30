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

package uk.gov.hmrc.tai.model.nps

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.IabdSummary
import uk.gov.hmrc.tai.model.tai.TaxYear

class NpsIabdSummarySpec extends PlaySpec {

  "NpsIabdSummary" should {
    "be identified as unique NpsIabdSummary" when {
      "give a same 'type' with different other parameters" in {

        val mockNpsIabdSummaryOne = NpsIabdSummary(Some(100), Some(1), Some("Desc1"), Some(2), Some(3))
        val mockNpsIabdSummaryTwo = NpsIabdSummary(Some(1000), Some(1), Some("Desc2"), Some(20), Some(30))
        Set(mockNpsIabdSummaryOne, mockNpsIabdSummaryTwo).size mustBe 1
      }

      "give a different 'type' with same other parameters" in {

        val mockNpsIabdSummaryOne = NpsIabdSummary(Some(100), Some(1), Some("Desc"), Some(2), Some(3))
        val mockNpsIabdSummaryTwo = NpsIabdSummary(Some(100), Some(2), Some("Desc"), Some(2), Some(3))
        Set(mockNpsIabdSummaryOne, mockNpsIabdSummaryTwo).size mustBe 2
      }

      "give a none 'type' with different other parameters" in {

        val mockNpsIabdSummary = NpsIabdSummary(Some(100), None, Some("Desc1"), Some(2), Some(3))
        mockNpsIabdSummary.hashCode mustBe 0
      }

      "give a none object to compare" in {

        val mockNpsIabdSummary = NpsIabdSummary(Some(100), None, Some("Desc1"), Some(2), Some(3))
        mockNpsIabdSummary.equals(None) mustBe false
      }
    }

    "return IabdSummary" when {

      val mockIabdSummary = NpsIabdSummary(Some(BigDecimal(350)), Some(1), Some("Description"), Some(2), Some(3))

      "given incomeSource and npsEmployments as none" in {

        mockIabdSummary.toIadbSummary(None, None) mustBe IabdSummary(1,"Description",350,Some(2),Some(3),None)

      }

      "given incomeSource and npsEmployments values" in {

        val mockIncomeSources = Some(List(NpsIncomeSource(name = Some("AAA"), employmentId = Some(2))))
        val mockNpsEmployments = Some(List(NpsEmployment(
          sequenceNumber = 1, startDate = NpsDate(TaxYear().start.minusDays(1)),
          endDate = Some(NpsDate(TaxYear().next.start)),
          taxDistrictNumber = "tax1", payeNumber = "payeno", employerName = Some("AAAA"), employmentType = 1)))

        mockIabdSummary.toIadbSummary(mockIncomeSources, mockNpsEmployments) mustBe
          IabdSummary(1,"Description",350,Some(2),Some(3),Some("AAA"))

      }

      "given none incomeSource and npsEmployments value" in {

        val mockNpsEmployments = Some(List(NpsEmployment(
          sequenceNumber = 2, startDate = NpsDate(TaxYear().start.minusDays(1)),
          endDate = Some(NpsDate(TaxYear().next.start)),
          taxDistrictNumber = "tax1", payeNumber = "payeno", employerName = Some("AAAA"), employmentType = 1)))
        val noneAmountIabdSummary = mockIabdSummary.copy(amount = None)

        noneAmountIabdSummary.toIadbSummary(None, mockNpsEmployments) mustBe
          IabdSummary(1,"Description",0,Some(2),Some(3),Some("AAAA"))

      }
    }
  }

}

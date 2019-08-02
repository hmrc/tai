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
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.{Tax, TaxCodeIncomeSummary}

class NpsEmploymentSpec extends PlaySpec {

  "toNpsIncomeSource" should {
    "return NpsIncomeSource" when {
      "given an estimated pay " in {
        val npsIncomeSource = createSUT.toNpsIncomeSource(BigDecimal(100))
        val mockPayAndTax = Some(NpsTax(totalIncome = Some(new NpsComponent(amount = Some(100)))))

        npsIncomeSource mustBe NpsIncomeSource(
          name = Some("AAA"),
          taxCode = None,
          employmentId = Some(1),
          employmentStatus = Some(1),
          employmentType = Some(1),
          payAndTax = mockPayAndTax,
          pensionIndicator = Some(false),
          otherIncomeSourceIndicator = Some(false),
          jsaIndicator = Some(false)
        )
      }
    }
  }

  "toTaxCodeIncomeSummary" should {
    "return TaxCodeIncomeSummary" when {
      "given an estimated pay" in {
        val taxCodeIncomeSummary = createSUT.toTaxCodeIncomeSummary(BigDecimal(100))
        val mockPayAndTax = Tax(totalIncome = Some(100))

        taxCodeIncomeSummary mustBe TaxCodeIncomeSummary(
          name = "AAA",
          taxCode = "startingTaxCode1",
          employmentId = Some(1),
          tax = mockPayAndTax,
          isLive = true)
      }
    }
  }

  val createSUT = NpsEmployment(
    sequenceNumber = 1,
    startDate = NpsDate(TaxYear().start.minusDays(1)),
    endDate = Some(NpsDate(TaxYear().next.start)),
    taxDistrictNumber = "tax1",
    payeNumber = "payeno",
    employerName = Some("AAA"),
    employmentType = 1,
    employmentStatus = Some(1),
    worksNumber = Some("0000"),
    receivingJobseekersAllowance = Some(false),
    receivingOccupationalPension = Some(false),
    otherIncomeSourceIndicator = Some(false),
    jobTitle = Some("jobTitle1"),
    startingTaxCode = Some("startingTaxCode1")
  )

}

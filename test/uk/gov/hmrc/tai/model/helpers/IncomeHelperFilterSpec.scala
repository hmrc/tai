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

package uk.gov.hmrc.tai.model.helpers

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.nps.{MergedEmployment, NpsIncomeSource}
import uk.gov.hmrc.tai.model.nps2.Income.{Ceased, Live, PotentiallyCeased}
import uk.gov.hmrc.tai.util.TaiConstants

class IncomeHelperFilterSpec extends PlaySpec {

  "filterLiveAndCeased" must {
    "return lists of live and ceased employment" when {
      "no input is provided" in {
        val (live, ceased) = sut.filterLiveAndCeased(Nil)
        live mustBe Nil
        ceased mustBe Nil
      }

      "all live employments" in {
        val listMergedEmployment = List(MergedEmployment(income1), MergedEmployment(pension))
        val (live, ceased) = sut.filterLiveAndCeased(listMergedEmployment)

        live.size mustBe 2
        ceased mustBe Nil
      }

      "employments are ceased and potentially ceased" in {
        val listMergedEmployment = List(MergedEmployment(ceasedIncome), MergedEmployment(potentiallyCeasedIncome))
        val (live, ceased) = sut.filterLiveAndCeased(listMergedEmployment)

        live mustBe Nil
        ceased.size mustBe 2
      }

      "employments are ceased and live" in {
        val listMergedEmployment = List(MergedEmployment(ceasedIncome), MergedEmployment(income1))
        val (live, ceased) = sut.filterLiveAndCeased(listMergedEmployment)

        live.size mustBe 1
        ceased.size mustBe 1
      }
    }
  }

  "filterPensions" must {
    "return lists of live and pension employment" when {
      "no input is provided" in {
        val (pension, live) = sut.filterPensions(Nil)
        pension mustBe Nil
        live mustBe Nil
      }

      "all live employments" in {
        val listMergedEmployment = List(MergedEmployment(income1), MergedEmployment(income1))
        val (pension, live) = sut.filterPensions(listMergedEmployment)

        pension mustBe Nil
        live.size mustBe 2
      }

      "employments are pension" in {
        val listMergedEmployment = List(MergedEmployment(pension), MergedEmployment(pension))
        val (pensions, live) = sut.filterPensions(listMergedEmployment)

        pensions.size mustBe 2
        live mustBe Nil
      }

      "employments are pension and live" in {
        val listMergedEmployment = List(MergedEmployment(income1), MergedEmployment(pension))
        val (pensions, live) = sut.filterPensions(listMergedEmployment)

        pensions.size mustBe 1
        live.size mustBe 1
      }
    }
  }

  "filterTaxableStateBenefits" must {
    "return taxable state benefits" when {
      "no input is provided" in {
        val (benefits, income) = sut.filterTaxableStateBenefits(Nil)

        benefits mustBe Nil
        income mustBe Nil
      }

      "all incomes are benefits" in {
        val listMergedEmployment = List(
          MergedEmployment(modifiableIncome(TaiConstants.ESA_TAX_DISTRICT, TaiConstants.ESA_PAYE_NUMBER)),
          MergedEmployment(
            modifiableIncome(
              TaiConstants.INCAPACITY_BENEFIT_TAX_DISTRICT,
              TaiConstants.INCAPACITY_BENEFIT_PAYE_NUMBER)),
          MergedEmployment(modifiableIncome(TaiConstants.JSA_TAX_DISTRICT, TaiConstants.JSA_PAYE_NUMBER))
        )
        val (benefits, income) = sut.filterTaxableStateBenefits(listMergedEmployment)

        benefits.size mustBe 3
        income mustBe Nil
      }

      "there are no benefits" in {
        val listMergedEmployment = List(MergedEmployment(income1), MergedEmployment(pension))
        val (benefits, income) = sut.filterTaxableStateBenefits(listMergedEmployment)

        benefits mustBe Nil
        income.size mustBe 2
      }

      "there are few benefits" in {
        val listMergedEmployment = List(
          MergedEmployment(modifiableIncome(TaiConstants.ESA_TAX_DISTRICT, TaiConstants.ESA_PAYE_NUMBER)),
          MergedEmployment(
            modifiableIncome(
              TaiConstants.INCAPACITY_BENEFIT_TAX_DISTRICT,
              TaiConstants.INCAPACITY_BENEFIT_PAYE_NUMBER)),
          MergedEmployment(modifiableIncome(TaiConstants.JSA_TAX_DISTRICT, TaiConstants.JSA_PAYE_NUMBER)),
          MergedEmployment(income1),
          MergedEmployment(pension)
        )
        val (benefits, income) = sut.filterTaxableStateBenefits(listMergedEmployment)

        benefits.size mustBe 3
        income.size mustBe 2
      }
    }

  }

  val income1 = NpsIncomeSource(
    name = Some("primary"),
    taxCode = Some("taxCode1"),
    employmentType = Some(1),
    employmentStatus = Some(Live.code),
    employmentId = Some(1))
  val pension = NpsIncomeSource(
    Some("pension1"),
    Some("taxCodePension"),
    Some(3),
    None,
    None,
    None,
    Some(5),
    Some(Live.code),
    None,
    None,
    Some(true),
    Some(true))
  val ceasedIncome = NpsIncomeSource(
    Some("ceasedIncome"),
    Some("taxCodePension"),
    Some(3),
    None,
    None,
    None,
    None,
    employmentStatus = Some(Ceased.code))
  val potentiallyCeasedIncome = NpsIncomeSource(
    Some("potCeased"),
    Some("taxCodePension"),
    Some(3),
    None,
    None,
    None,
    None,
    employmentStatus = Some(PotentiallyCeased.code))
  val modifiableIncome = (taxNum: Int, payeRef: String) =>
    NpsIncomeSource(
      name = Some("value"),
      taxCode = Some("taxCode1"),
      employmentType = Some(1),
      employmentStatus = Some(Live.code),
      employmentId = Some(1),
      employmentTaxDistrictNumber = Some(taxNum),
      employmentPayeRef = Some(payeRef)
  )

  def sut = IncomeHelper

}

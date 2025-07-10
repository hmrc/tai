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

package uk.gov.hmrc.tai.model.hip.reads

import play.api.libs.json.*
import uk.gov.hmrc.tai.model.domain.*
import uk.gov.hmrc.tai.model.domain.income.{OtherBasisOperation, TaxCodeIncome, TaxCodeIncomeStatus, Week1Month1BasisOperation}

object TaxCodeIncomeHipReads {

  private val taxCodeIncomeSourceReads: Reads[TaxCodeIncome] = (json: JsValue) => {
    val incomeSourceType = taxCodeIncomeType(json)
    val employmentId = (json \ "employmentSequenceNumber").asOpt[Int]
    val amount = totalTaxableIncome(json, employmentId)
    val description = incomeSourceType.toString
    val taxCode = (json \ "taxCode").asOpt[String].getOrElse("")
    val name = (json \ "payeSchemeOperatorName").asOpt[String].getOrElse("")
    val basisOperation = (json \ "basisOfOperation").asOpt[String] match {
      case Some("Week1/Month1") => Week1Month1BasisOperation
      case _                    => OtherBasisOperation
    }
    val status = TaxCodeIncomeStatus.employmentStatus(json)
    val iyaCy = (json \ "totalInYearAdjustmentIntoCurrentYear").asOpt[BigDecimal].getOrElse(BigDecimal(0))
    val totalIya = (json \ "totalInYearAdjustment").asOpt[BigDecimal].getOrElse(BigDecimal(0))
    val iyaCyPlusOne = (json \ "totalInYearAdjustmentIntoNextYear").asOpt[BigDecimal].getOrElse(BigDecimal(0))

    JsSuccess(
      TaxCodeIncome(
        incomeSourceType,
        employmentId = employmentId,
        amount = amount,
        description = description,
        taxCode = taxCode,
        name = name,
        basisOperation = basisOperation,
        status = status,
        inYearAdjustmentIntoCY = iyaCy,
        totalInYearAdjustment = totalIya,
        inYearAdjustmentIntoCYPlusOne = iyaCyPlusOne
      )
    )
  }

  val taxCodeIncomeSourcesReads: Reads[Seq[TaxCodeIncome]] = (json: JsValue) => {
    val taxCodeIncomes = (json \ "employmentDetailsList")
      .validateOpt[Seq[TaxCodeIncome]](Reads.seq(taxCodeIncomeSourceReads))
      .getOrElse(None)
      .getOrElse(Seq.empty)

    JsSuccess(taxCodeIncomes)
  }

  private def taxCodeIncomeType(json: JsValue): TaxComponentType = {
    def indicator(key: String): Boolean = (json \ key).asOpt[Boolean].getOrElse(false)

    if (indicator("activePension")) {
      PensionIncome
    } else if (indicator("jobSeekersAllowance")) {
      JobSeekerAllowanceIncome
    } else if (indicator("otherIncome")) {
      OtherIncome
    } else {
      EmploymentIncome
    }
  }

  private def totalTaxableIncome(json: JsValue, employmentId: Option[Int]): BigDecimal = {
    val list1 = (json \ "payAndTax" \ "totalIncomeDetails" \ "summaryIABDEstimatedPayDetailsList")
      .validateOpt[Seq[IabdSummary]](Reads.seq(IabdSummaryHipReads.iabdSummaryReads))
      .getOrElse(None)
      .getOrElse(Seq.empty)

    val list2 = (json \ "payAndTax" \ "totalIncomeDetails" \ "summaryIABDDetailsList")
      .validateOpt[Seq[IabdSummary]](Reads.seq(IabdSummaryHipReads.iabdSummaryReads))
      .getOrElse(None)
      .getOrElse(Seq.empty)

    (list1 ++ list2)
      .find(iabd => newEstimatedPayTypeFilter(iabd) && employmentFilter(iabd, employmentId))
      .map(_.amount)
      .getOrElse(BigDecimal(0))
  }

  def newEstimatedPayTypeFilter(iabdSummary: IabdSummary): Boolean =
    iabdSummary.componentType == 27

  def employmentFilter(iabdSummary: IabdSummary, employmentId: Option[Int]): Boolean =
    (iabdSummary.employmentId, employmentId) match {
      case (Some(jsId), Some(empId)) => jsId == empId
      case _                         => false
    }

  implicit val reads: Reads[TaxCodeIncome] = taxCodeIncomeSourceReads
}

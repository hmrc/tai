/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.domain.income

import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.tai.model.domain._

object TaxCodeIncomeHipReads {

  private val logger: Logger = Logger(getClass.getName)

  private val basisOperationReads = new Reads[BasisOperation] {
    override def reads(json: JsValue): JsResult[BasisOperation] = {
      val result = json.asOpt[String] match {
        case Some("Week1/Month1") => Week1Month1BasisOperation
        case _                    => OtherBasisOperation
      }
      JsSuccess(result)
    }
  }

  private val taxCodeIncomeSourceReads: Reads[TaxCodeIncome] = (json: JsValue) => {
    val incomeSourceType = taxCodeIncomeType(json)
    val employmentId = (json \ "employmentSequenceNumber").asOpt[Int]
    val amount = totalTaxableIncome(json, employmentId).getOrElse(BigDecimal(0))
    val description = incomeSourceType.toString
    val taxCode = (json \ "taxCode").asOpt[String].getOrElse("")
    val name = (json \ "payeSchemeOperatorName").asOpt[String].getOrElse("")
    val basisOperation =
      (json \ "basisOfOperation").asOpt[BasisOperation](basisOperationReads).getOrElse(OtherBasisOperation)
    val status = TaxCodeIncomeStatus.employmentStatus(json)
    val iyaCy = (json \ "totalInYearAdjustmentIntoCurrentYear").asOpt[BigDecimal].getOrElse(BigDecimal(0))
    val totalIya = (json \ "totalInYearAdjustment").asOpt[BigDecimal].getOrElse(BigDecimal(0))
    val iyaCyPlusOne = (json \ "totalInYearAdjustmentIntoNextYear").asOpt[BigDecimal].getOrElse(BigDecimal(0))
    JsSuccess(
      TaxCodeIncome(
        incomeSourceType,
        employmentId,
        amount,
        description,
        taxCode,
        name,
        basisOperation,
        status,
        iyaCy,
        totalIya,
        iyaCyPlusOne
      )
    )
  }

  val taxCodeIncomeSourcesReads: Reads[Seq[TaxCodeIncome]] = (json: JsValue) => {
    val taxCodeIncomes = (json \ "employmentDetailsList")
      .asOpt[Seq[TaxCodeIncome]](Reads.seq(TaxCodeIncomeHipReads.taxCodeIncomeSourceReads))
      .getOrElse(Seq.empty[TaxCodeIncome])
    JsSuccess(taxCodeIncomes)
  }

  private def taxCodeIncomeType(json: JsValue): TaxComponentType = {
    def indicator(indicatorType: String): Boolean = (json \ indicatorType).asOpt[Boolean].getOrElse(false)

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

  private def totalTaxableIncome(json: JsValue, employmentId: Option[Int]): Option[BigDecimal] = {
    val iabdSummaries: Option[Seq[IabdSummary]] =
      (json \ "payAndTax" \ "totalIncomeDetails" \ "summaryIABDEstimatedPayDetailsList")
        .asOpt[Seq[IabdSummary]](Reads.seq(IabdSummaryHipReads.iabdSummaryReads))
    val iabdSummary = iabdSummaries.flatMap {
      _.find(iabd =>
        newEstimatedPayTypeFilter(iabd) &&
          employmentFilter(iabd, employmentId)
      )
    }
    iabdSummary.map(_.amount) match {
      case Some(amount) => Some(amount)
      case _ =>
        logger.warn("TotalTaxableIncome is 0")
        None
    }
  }

  def newEstimatedPayTypeFilter(iabdSummary: IabdSummary): Boolean = {
    val NewEstimatedPay = 27
    iabdSummary.componentType == NewEstimatedPay
  }

  def employmentFilter(iabdSummary: IabdSummary, employmentId: Option[Int]): Boolean = {
    val compare = for {
      jsEmploymentId <- iabdSummary.employmentId
      empId          <- employmentId
    } yield jsEmploymentId == empId

    compare.getOrElse(false)
  }

  implicit val reads: Reads[TaxCodeIncome] = TaxCodeIncomeHipReads.taxCodeIncomeSourceReads
}

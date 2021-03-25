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

package uk.gov.hmrc.tai.model.domain.formatters.income

import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.income._

trait TaxCodeIncomeHodFormatters {

  private val basisOperationReads = new Reads[BasisOperation] {
    override def reads(json: JsValue): JsResult[BasisOperation] = {
      val result = json.asOpt[Int] match {
        case Some(1) => Week1Month1BasisOperation
        case _       => OtherBasisOperation
      }
      JsSuccess(result)
    }
  }

  val totalTaxableIncomeReads: Reads[BigDecimal] = (json: JsValue) => {
    val totalTaxableIncome = (json \ "totalTaxableIncome").asOpt[BigDecimal].getOrElse(BigDecimal(0))
    JsSuccess(totalTaxableIncome)
  }

  val taxCodeIncomeSourceReads: Reads[TaxCodeIncome] = (json: JsValue) => {
    val incomeSourceType = taxCodeIncomeType(json)
    val employmentId = (json \ "employmentId").asOpt[Int]
    val amount = totalTaxableIncome(json, employmentId).getOrElse(BigDecimal(0))
    val description = incomeSourceType.toString
    val taxCode = (json \ "taxCode").asOpt[String].getOrElse("")
    val name = (json \ "name").asOpt[String].getOrElse("")
    val basisOperation =
      (json \ "basisOperation").asOpt[BasisOperation](basisOperationReads).getOrElse(OtherBasisOperation)
    val status = employmentStatus(json)
    val iyaCy = (json \ "inYearAdjustmentIntoCY").asOpt[BigDecimal].getOrElse(BigDecimal(0))
    val totalIya = (json \ "totalInYearAdjustment").asOpt[BigDecimal].getOrElse(BigDecimal(0))
    val iyaCyPlusOne = (json \ "inYearAdjustmentIntoCYPlusOne").asOpt[BigDecimal].getOrElse(BigDecimal(0))
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
        iyaCyPlusOne))
  }

  val taxCodeIncomeSourcesReads: Reads[Seq[TaxCodeIncome]] = (json: JsValue) => {
    val taxCodeIncomes = (json \ "incomeSources")
      .asOpt[Seq[TaxCodeIncome]](Reads.seq(taxCodeIncomeSourceReads))
      .getOrElse(Seq.empty[TaxCodeIncome])
    JsSuccess(taxCodeIncomes)
  }

  private def taxCodeIncomeType(json: JsValue): TaxComponentType = {
    def indicator(indicatorType: String) = (json \ indicatorType).asOpt[Boolean].getOrElse(false)

    if (indicator("pensionIndicator")) {
      PensionIncome
    } else if (indicator("jsaIndicator")) {
      JobSeekerAllowanceIncome
    } else if (indicator("otherIncomeSourceIndicator")) {
      OtherIncome
    } else {
      EmploymentIncome
    }
  }

  private def employmentStatus(json: JsValue): TaxCodeIncomeStatus = {
    val employmentStatus = (json \ "employmentStatus").asOpt[Int]

    employmentStatus match {
      case Some(1) => Live
      case Some(2) => PotentiallyCeased
      case Some(3) => Ceased
      case default =>
        Logger.warn(s"Invalid Employment Status -> $default")
        throw new RuntimeException("Invalid employment status")
    }
  }

  private val iabdSummaryReads = new Reads[IabdSummary] {
    override def reads(json: JsValue): JsResult[IabdSummary] = {
      val componentType = (json \ "type").as[Int]
      val employmentId = (json \ "employmentId").asOpt[Int]
      val amount = (json \ "amount").asOpt[BigDecimal].getOrElse(BigDecimal(0))
      JsSuccess(IabdSummary(componentType, employmentId, amount))
    }
  }

  private def totalTaxableIncome(json: JsValue, employmentId: Option[Int]): Option[BigDecimal] = {
    val iabdSummaries: Option[Seq[IabdSummary]] =
      (json \ "payAndTax" \ "totalIncome" \ "iabdSummaries").asOpt[Seq[IabdSummary]](Reads.seq(iabdSummaryReads))
    val iabdSummary = iabdSummaries.flatMap {
      _.find(
        iabd =>
          newEstimatedPayTypeFilter(iabd) &&
            employmentFilter(iabd, employmentId))
    }
    iabdSummary.map(_.amount) match {
      case Some(amount) => Some(amount)
      case _ =>
        Logger.warn("TotalTaxableIncome is 0")
        None
    }
  }

  private[formatters] def newEstimatedPayTypeFilter(iabdSummary: IabdSummary): Boolean = {
    val NewEstimatedPay = 27
    iabdSummary.componentType == NewEstimatedPay
  }

  private[formatters] def employmentFilter(iabdSummary: IabdSummary, employmentId: Option[Int]): Boolean = {
    val compare = for {
      jsEmploymentId <- iabdSummary.employmentId
      empId          <- employmentId
    } yield jsEmploymentId == empId

    compare.getOrElse(false)
  }

  private[formatters] case class IabdSummary(componentType: Int, employmentId: Option[Int], amount: BigDecimal)

}

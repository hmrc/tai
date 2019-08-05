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

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.tai.model.helpers.{IncomeHelper, TaxHelper}
import uk.gov.hmrc.tai.model.nps2.{AllowanceType, DeductionType}
import uk.gov.hmrc.tai.model.{Tax, TaxCodeIncomeSummary}
import uk.gov.hmrc.tai.model.enums.BasisOperation.BasisOperation
import uk.gov.hmrc.tai.util.TaiConstants

case class NpsIncomeSource(
  name: Option[String] = None,
  taxCode: Option[String] = None,
  employmentType: Option[Int] = Some(TaiConstants.PrimaryEmployment),
  allowances: Option[List[NpsComponent]] = None,
  deductions: Option[List[NpsComponent]] = None,
  payAndTax: Option[NpsTax] = None,
  employmentId: Option[Int] = None,
  employmentStatus: Option[Int] = None,
  employmentTaxDistrictNumber: Option[Int] = None,
  employmentPayeRef: Option[String] = None,
  pensionIndicator: Option[Boolean] = None,
  otherIncomeSourceIndicator: Option[Boolean] = None,
  jsaIndicator: Option[Boolean] = None,
  totalInYearAdjustment: Option[BigDecimal] = None,
  inYearAdjustmentIntoCY: Option[BigDecimal] = None,
  inYearAdjustmentIntoCYPlusOne: Option[BigDecimal] = None,
  inYearAdjustmentFromPreviousYear: Option[BigDecimal] = None,
  basisOperation: Option[BasisOperation] = None) {
  def toTaxCodeIncomeSummary(
    employment: Option[NpsEmployment] = None,
    adjustedNetIncome: Option[BigDecimal] = None): TaxCodeIncomeSummary = {
    val newIncome = if (adjustedNetIncome.isDefined) {
      adjustedNetIncome
    } else {
      payAndTax.flatMap(_.totalIncome.flatMap(_.amount))
    }

    val tax = payAndTax
      .map(
        oldTax =>
          TaxHelper.toAdjustedTax(
            oldTax,
            taxCode = taxCode,
            allowances = allowances,
            deductions = deductions,
            totalInYearAdjustment = totalInYearAdjustment,
            inYearAdjustmentIntoCY = inYearAdjustmentIntoCY,
            inYearAdjustmentIntoCYPlusOne = inYearAdjustmentIntoCYPlusOne,
            inYearAdjustmentFromPreviousYear = inYearAdjustmentFromPreviousYear
        ))
      .getOrElse(new Tax)

    new TaxCodeIncomeSummary(
      name = name.getOrElse(""),
      taxCode = taxCode.getOrElse(""),
      employmentId = employmentId,
      employmentPayeRef = employmentPayeRef,
      employmentType = employmentType,
      incomeType = Some(incomeType),
      employmentStatus = this.employmentStatus,
      tax = tax,
      worksNumber = employment.flatMap(_.worksNumber),
      jobTitle = employment.flatMap(_.jobTitle),
      startDate = employment.map(_.startDate.localDate),
      endDate = employment.flatMap(_.endDate.map(_.localDate)),
      income = newIncome,
      otherIncomeSourceIndicator = otherIncomeSourceIndicator,
      isLive = IncomeHelper.isLive(this.employmentStatus),
      isEditable = IncomeHelper.isEditableByUser(
        otherIncomeSourceIndicator,
        employment.flatMap(_.cessationPayThisEmployment),
        jsaIndicator,
        employment.flatMap(_.employmentStatus)
      ),
      isOccupationalPension =
        IncomeHelper.isOccupationalPension(employmentTaxDistrictNumber, employmentPayeRef, pensionIndicator),
      isPrimary = IncomeHelper.isPrimary(employmentType),
      basisOperation = basisOperation
    )
  }

  lazy val personalAllowanceComponent: Option[NpsComponent] = allowances.flatMap(
    _.find(
      x =>
        List(
          Some(AllowanceType.PersonalAllowanceStandard.id),
          Some(AllowanceType.PersonalAllowanceAged.id),
          Some(AllowanceType.PersonalAllowanceElderly.id)).contains(x.`type`)))

  lazy val underpaymentComponent: Option[NpsComponent] =
    deductions.flatMap(y => y.find(x => List(Some(DeductionType.UnderpaymentAmount.id)).contains(x.`type`)))

  lazy val inYearAdjustmentComponent: Option[NpsComponent] =
    deductions.flatMap(_.find(x => List(Some(DeductionType.InYearAdjustment.id)).contains(x.`type`)))

  lazy val outstandingDebtComponent: Option[NpsComponent] =
    deductions.flatMap(_.find(x => List(Some(DeductionType.OutstandingDebtRestriction.id)).contains(x.`type`)))

  lazy val personalAllowanceTransferred: Option[NpsComponent] =
    deductions.flatMap(_.find(x => List(Some(DeductionType.PersonalAllowanceTransferred.id)).contains(x.`type`)))

  lazy val personalAllowanceReceived: Option[NpsComponent] =
    allowances.flatMap(_.find(x => List(Some(AllowanceType.PersonalAllowanceReceived.id)).contains(x.`type`)))

  lazy val statePension: Option[BigDecimal] =
    deductions.flatMap(_.find(_.`type`.contains(DeductionType.StatePensionOrBenefits.id))).flatMap(_.amount)

  lazy val incomeType: Int = IncomeHelper
    .incomeType(employmentTaxDistrictNumber, employmentPayeRef, employmentType, jsaIndicator, pensionIndicator)
}

object NpsIncomeSource {
  implicit val formats = Json.format[NpsIncomeSource]
}

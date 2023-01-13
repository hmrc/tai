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

package uk.gov.hmrc.tai.model.nps

import play.api.libs.json.Json
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.model.enums.IncomeType.{IncomeTypeESA, IncomeTypeIB, IncomeTypeJSA, IncomeTypeStatePensionLumpSum}
import uk.gov.hmrc.tai.model.helpers.{IncomeHelper, TaxModelFactory}
import uk.gov.hmrc.tai.model.nps2.AllowanceType
import uk.gov.hmrc.tai.model.tai.{AnnualAccount, TaxYear}
import uk.gov.hmrc.tai.model.nps2.IabdType._
import uk.gov.hmrc.tai.model.enums.BasisOperation
import uk.gov.hmrc.tai.model.enums.BasisOperation.BasisOperation
import uk.gov.hmrc.tai.util.TaiConstants

case class NpsTaxAccount(
  nino: Option[String],
  taxYear: Option[Int],
  totalEstPay: Option[NpsComponent] = None,
  date: Option[String] = None,
  adjustedNetIncome: Option[NpsComponent] = None,
  totalEstTax: Option[BigDecimal] = None,
  totalLiability: Option[NpsTotalLiability] = None,
  incomeSources: Option[List[NpsIncomeSource]] = None,
  previousTaxAccountId: Option[Int] = None,
  previousYearTaxAccountId: Option[Int] = None,
  nextTaxAccountId: Option[Int] = None,
  nextYearTaxAccountId: Option[Int] = None,
  taxAccountId: Option[Int] = None) {

  /**
    * Convert the data into a format that the front end requires
    */
  def toTaxSummary(
    version: Int,
    npsEmployments: List[NpsEmployment],
    iabds: List[NpsIabdRoot] = Nil,
    rtiCalc: List[RtiCalc] = Nil,
    accounts: List[AnnualAccount] = Nil): TaxSummaryDetails = {

    val childBenefitAmount =
      iabds.filter(_.`type` == ChildBenefit.code).foldLeft(BigDecimal(0))(_ + _.grossAmount.getOrElse(BigDecimal(0)))

    val mergedIncomes = IncomeHelper.getAllIncomes(npsEmployments, incomeSources, adjustedNetIncome)

    val filteredStateBenefits = IncomeHelper.filterTaxableStateBenefits(mergedIncomes)
    val filteredPensions = IncomeHelper.filterPensions(filteredStateBenefits._2)
    val filteredLive = IncomeHelper.filterLiveAndCeased(filteredPensions._2)

    val taxCodeDetails = getTaxCodeDetails(incomeSources)

    val taxSummaryDetails = TaxModelFactory.create(
      nino = nino.getOrElse(""),
      version,
      employments = toTaxCodeIncomeTotal(filteredLive._1),
      statePension = getStatePension,
      statePensionLumpSum = getStatePensionLumpSum,
      occupationalPensions = toTaxCodeIncomeTotal(filteredPensions._1),
      taxableStateBenefitIncomes = toTaxCodeIncomeTotal(filteredStateBenefits._1),
      taxableStateBenefit = getTaxableStateBenefit(filteredStateBenefits._1).map(_.toTaxComponent(incomeSources)),
      personalAllowanceNps = getPersonalAllowanceComponent,
      ceasedEmployments = toTaxCodeIncomeTotal(filteredLive._2),
      totalLiability = totalLiability,
      incomeSources = incomeSources,
      adjustedNetIncome = adjustedNetIncome,
      underpaymentPreviousYear = getUnderpaymentPreviousYearComponent,
      inYearAdjustment = getInYearAdjustmentComponent,
      outstandingDebt = getOutstandingDebt,
      childBenefitAmount = Some(childBenefitAmount),
      taxCodeDetails = taxCodeDetails,
      npsEmployments = Some(npsEmployments),
      accounts = accounts,
      ceasedEmploymentDetail = Some(getEmploymentCeasedDetail(npsEmployments))
    )
    val hasDuplicateEmploymentNames = taxSummaryDetails.increasesTax
      .flatMap(_.incomes.map(_.taxCodeIncomes.hasDuplicateEmploymentNames))
      .getOrElse(false)

    val value = taxSummaryDetails.copy(
      incomeData = Some(
        getIncomeData(
          toTaxCodeIncomeTotal(filteredLive._1),
          toTaxCodeIncomeTotal(filteredLive._2),
          toTaxCodeIncomeTotal(filteredPensions._1),
          toTaxCodeIncomeTotal(filteredStateBenefits._1),
          rtiCalc,
          npsEmployments,
          iabds,
          hasDuplicateEmploymentNames
        )))
    value
  }

  private[nps] val findCeasedEmploymentDetails = (ced1: CeasedEmploymentDetails, ced2: CeasedEmploymentDetails) => {
    (ced1.endDate, ced2.endDate) match {
      case (None, Some(_))                            => ced1
      case (Some(_), None)                            => ced2
      case (Some(dt1), Some(dt2)) if dt1.isAfter(dt2) => ced1
      case (Some(dt1), Some(dt2)) if dt2.isAfter(dt1) => ced2
      case _                                          => ced1
    }
  }

  private[nps] def getEmploymentCeasedDetail(npsEmployments: List[NpsEmployment]) = {

    val minusOneDay = TaxYear().prev.end
    val minusOneYearAndOneDay = TaxYear().start.minusYears(1).minusDays(1)
    val minusTwoYearsAndOneDay = TaxYear().start.minusYears(2).minusDays(1)

    if (npsEmployments.isEmpty) {
      CeasedEmploymentDetails(None, None, None, None)
    } else {
      val ceasedEmploymentDetails = npsEmployments
        .map(
          employment =>
            CeasedEmploymentDetails(
              toLocalDate(employment.endDate),
              employment.receivingOccupationalPension,
              None,
              employment.employmentStatus))
        .reduceLeft(findCeasedEmploymentDetails)
      ceasedEmploymentDetails match {
        case CeasedEmploymentDetails(None, isPension, _, Some(status)) =>
          CeasedEmploymentDetails(None, isPension, None, Some(status))
        case CeasedEmploymentDetails(Some(date), isPension, _, Some(status)) if date.isAfter(minusOneDay) =>
          CeasedEmploymentDetails(Some(date), isPension, None, Some(status))
        case CeasedEmploymentDetails(Some(date), isPension, _, Some(status)) if date.isAfter(minusOneYearAndOneDay) =>
          CeasedEmploymentDetails(Some(date), isPension, Some(TaiConstants.CEASED_MINUS_ONE), Some(status))
        case CeasedEmploymentDetails(Some(date), isPension, _, Some(status)) if date.isAfter(minusTwoYearsAndOneDay) =>
          CeasedEmploymentDetails(Some(date), isPension, Some(TaiConstants.CEASED_MINUS_TWO), Some(status))
        case CeasedEmploymentDetails(date, isPension, _, Some(status)) =>
          CeasedEmploymentDetails(date, isPension, Some(TaiConstants.CEASED_MINUS_THREE), Some(status))
        case CeasedEmploymentDetails(_, isPension, _, None) => CeasedEmploymentDetails(None, isPension, None, None)
      }
    }
  }

  private[nps] def toLocalDate(date: Option[NpsDate]) =
    date match {
      case None     => None
      case Some(dt) => Some(dt.localDate)
    }

  def getIncomeData(
    employments: Option[TaxCodeIncomeTotal],
    ceasedEmployments: Option[TaxCodeIncomeTotal],
    occupationalPensions: Option[TaxCodeIncomeTotal],
    taxableStateBenefitIncomes: Option[TaxCodeIncomeTotal],
    rtiCalcs: List[RtiCalc],
    npsEmployments: List[NpsEmployment],
    iabds: List[NpsIabdRoot] = Nil,
    hasDuplicateEmploymentNames: Boolean): IncomeData = {

    val allEmps = employments.map(x => x.taxCodeIncomes).getOrElse(List[TaxCodeIncomeSummary]()) :::
      ceasedEmployments.map(x => x.taxCodeIncomes).getOrElse(List[TaxCodeIncomeSummary]()) :::
      occupationalPensions.map(x => x.taxCodeIncomes).getOrElse(List[TaxCodeIncomeSummary]()) :::
      taxableStateBenefitIncomes.map(x => x.taxCodeIncomes).getOrElse(List[TaxCodeIncomeSummary]())

    def getIabdSummaries(emp: TaxCodeIncomeSummary) =
      adjustedNetIncome.flatMap(_.iabdSummaries.map(_.filter(x =>
        x.employmentId == emp.employmentId && x.`type`.contains(NewEstimatedPay.code))))

    def getGrossAmount(iabdSummaries: Option[List[NpsIabdSummary]]) =
      iabdSummaries.flatMap(_.headOption.flatMap(_.amount))

    def getSource(iabdSummaries: Option[List[NpsIabdSummary]]) =
      iabdSummaries.flatMap(_.headOption.flatMap(_.estimatedPaySource))

    val incomeExplanations = allEmps.map { emp =>
      val npsEmp = npsEmployments.find(x => emp.employmentId.contains(x.sequenceNumber))
      val payRollingBiks =
        npsEmp.exists(emp => emp.payrolledTaxYear.getOrElse(false) || emp.payrolledTaxYear1.getOrElse(false))
      val editableDetails = EditableDetails(payRollingBiks = payRollingBiks)
      val pay =
        iabds.find(iabd => iabd.`type` == NewEstimatedPay.code && iabd.employmentSequenceNumber == emp.employmentId)
      val rtiCalc = rtiCalcs.find(rtiEmp => emp.employmentId.contains(rtiEmp.employmentId))
      val grossAmount = getGrossAmount(getIabdSummaries(emp))
      val source = getSource(getIabdSummaries(emp))

      IncomeExplanation(
        employerName = emp.name,
        hasDuplicateEmploymentNames = hasDuplicateEmploymentNames,
        incomeId = emp.employmentId.getOrElse(0),
        worksNumber = emp.worksNumber,
        startDate = emp.startDate,
        endDate = emp.endDate,
        notificationDate = pay.flatMap(_.receiptDate.map(_.localDate)),
        updateActionDate = pay.flatMap(_.captureDate.map(_.localDate)),
        employmentStatus = npsEmp.flatMap(_.employmentStatus),
        employmentType = npsEmp.map(_.employmentType),
        isPension = emp.isOccupationalPension,
        iabdSource = source,
        payToDate = rtiCalc.map(_.totalPayToDate).getOrElse(BigDecimal(0)),
        calcAmount = grossAmount, //rtiCalc.flatMap(_.calculationResult),
        payFrequency = rtiCalc.flatMap(_.payFrequency),
        paymentDate = rtiCalc.flatMap(_.paymentDate),
        grossAmount = grossAmount, //pay.headOption.flatMap(_.grossAmount),
        cessationPay = npsEmp.flatMap(_.cessationPayThisEmployment),
        editableDetails = editableDetails
      )
    }
    IncomeData(incomeExplanations = incomeExplanations)
  }

  /**
    * Retrieve the taxable state benefit. These can either be stored as an income source, or as an adjustment to an income source
    */
  private[nps] def getTaxableStateBenefit(filteredIncomeSource: List[MergedEmployment]): Option[NpsComponent] = {

    //If we don't have an Income Source for this then look it up from the iadbSummaries
    def getIadbSummary(benefitIncomeSources: List[MergedEmployment], iadbType: Int): List[NpsIabdSummary] =
      //Check that the passed has some items
      benefitIncomeSources match {
        case Nil => {
          //Lookup the relevant type from the adjustedNetIncome
          adjustedNetIncome.flatMap(_.iabdSummaries.flatMap(_.find(_.`type`.contains(iadbType)))) match {
            case None    => Nil
            case Some(x) => List(x)
          }
        }
        case incomeList => Nil
      }

    //Get the esa, jsa and ib from either the income source or relevant IADB_TYPE
    //Also add the Other Benefit
    val iabdSummaries =
      getIadbSummary(
        filteredIncomeSource.filter(x => x.incomeSource.incomeType == IncomeTypeESA.code),
        EmploymentAndSupportAllowance.code) :::
        getIadbSummary(
        filteredIncomeSource.filter(x => x.incomeSource.incomeType == IncomeTypeJSA.code),
        JobSeekersAllowance.code) :::
        getIadbSummary(
        filteredIncomeSource.filter(x => x.incomeSource.incomeType == IncomeTypeIB.code),
        IncapacityBenefit.code)

    iabdSummaries match {
      case Nil => None
      case x =>
        Some(
          NpsComponent(
            Some(x.foldLeft(BigDecimal(0))((total, detail) => detail.amount.getOrElse(BigDecimal(0)) + total)),
            None,
            Some(x.sortBy(_.amount).reverse),
            None))
    }

  }

  def getPersonalAllowanceComponent: Option[NpsComponent] = primaryEmployment.flatMap(_.personalAllowanceComponent)
  def getUnderpaymentPreviousYearComponent: Option[NpsComponent] = primaryEmployment.flatMap(_.underpaymentComponent)
  def getInYearAdjustmentComponent: Option[NpsComponent] = primaryEmployment.flatMap(_.inYearAdjustmentComponent)
  def getPersonalAllowanceTransferred: Option[NpsComponent] = primaryEmployment.flatMap(_.personalAllowanceTransferred)
  def getPersonalAllowanceReceived: Option[NpsComponent] = primaryEmployment.flatMap(_.personalAllowanceReceived)
  def getOutstandingDebt: Option[NpsComponent] = primaryEmployment.flatMap(_.outstandingDebtComponent)

  def getTaxCodeDetails(incomeSources: Option[List[NpsIncomeSource]] = None): Option[TaxCodeDetails] = {

    val taxCodeList = incomeSources.map(incomeSource => {

      val employments = incomeSource.map { incomes =>
        Employments(
          incomes.employmentId,
          incomes.name,
          getOperatedTaxCode(incomes.taxCode, incomes.basisOperation),
          incomes.basisOperation)
      }

      val taxCodes = getTaxCodes(incomeSource)
      val taxCodeDescriptions = getFullTaxCodeDescription(incomeSource)

      val deductions = incomeSource.flatMap { incomes =>
        getTaxCodeComponent(incomes.deductions)
      }.flatten

      val uniqueDeductions = deductions
        .groupBy(_.componentType)
        .mapValues { y =>
          val sumAmount: BigDecimal = y.flatMap(_.amount).sum
          y.headOption.map(_.copy(amount = Some(sumAmount)))
        }
        .flatMap(_._2)
        .toList

      val allowances: List[TaxCodeComponent] = incomeSource.flatMap { incomes =>
        getTaxCodeComponent(incomes.allowances)
      }.flatten

      val uniqueAllowances: List[TaxCodeComponent] = allowances
        .groupBy(
          _.componentType
        )
        .values
        .toList
        .flatMap { y =>
          y.headOption.map(_.copy(amount = Some(y.flatMap(_.amount).sum)))
        }

      val codes = taxCodes.distinct.map(code => TaxCode(Some(code), getTaxCodeRate(code)))

      TaxCodeDetails(
        employment = Some(employments),
        taxCode = Some(codes),
        taxCodeDescriptions = Some(taxCodeDescriptions),
        deductions = Some(uniqueDeductions),
        allowances = Some(uniqueAllowances)
      )
    })

    taxCodeList
  }

  def getTaxCodeRate(code: String): Option[BigDecimal] = {
    //Get any income source
    val x = taxCodeRates.flatMap(_.flatMap(rate => {
      code match {
        case rate._1 => rate._2
        case _       => None
      }
    }).headOption)
    x
  }

  def getOperatedTaxCode(taxCode: Option[String], basisOperation: Option[BasisOperation]): Option[String] =
    basisOperation.fold(taxCode) { b =>
      if (b == BasisOperation.Week1Month1) {
        taxCode.map(t => if (t != "NT") t + " X" else t)
      } else {
        taxCode
      }
    }

  lazy val taxCodeRates: Option[List[(String, Option[BigDecimal])]] = {

    val incomeSrc = incomeSources.flatMap(_.headOption)
    val taxBands = incomeSrc.flatMap(_.payAndTax.flatMap(_.taxBands))

    taxBands.map { bands =>
      bands.indices.map {
        case i @ 0 => ("BR", bands(i).rate)
        case i     => ("D" + (i - 1), bands(i).rate)
      }.toList
    }
  }

  def getTaxCodeComponent(npsComponents: Option[List[NpsComponent]]): Option[List[TaxCodeComponent]] =
    npsComponents.map(tc => {
      tc.map(npsComponent => {
        TaxCodeComponent(
          description = npsComponent.npsDescription,
          amount = npsComponent.amount,
          componentType = npsComponent.`type`)
      })
    })

  def getTaxCodes(incomeSource: List[NpsIncomeSource]): List[String] =
    incomeSource.flatMap { incomes =>
      val taxCode = incomes.taxCode.getOrElse("")
      getTaxCodeDescriptors(taxCode)
    }

  private[nps] def getTaxCodeDescriptors(taxCode: String): List[String] = {

    val prefix = "(S|K|D0|D1)".r
    val suffix = "(^0T|^NT|^BR|L$|Y$|M$|N$|T$)".r

    val allPrefixMatches = prefix.findAllMatchIn(taxCode).foldLeft(List[String]()) { (list, m) =>
      list :+ m.toString()
    }
    val filter = taxCode.filterNot(c => allPrefixMatches.contains(c.toString))
    val allSuffixMatches = suffix.findAllMatchIn(filter).foldLeft(List[String]()) { (list, m) =>
      list :+ m.toString()
    }

    allPrefixMatches ++ allSuffixMatches
  }

  private[nps] def getFullTaxCodeDescription(incomeSource: List[NpsIncomeSource]): List[TaxCodeDescription] =
    incomeSource.foldLeft(List[TaxCodeDescription]()) { (col, incomes) =>
      val taxCode = getOperatedTaxCode(incomes.taxCode, incomes.basisOperation).getOrElse("")
      val name = incomes.name.getOrElse("")
      val descriptors = getTaxCodeDescriptors(taxCode).map { d =>
        TaxCode(Some(d), getTaxCodeRate(d))
      }
      col :+ TaxCodeDescription(taxCode, name, descriptors)
    }

  private[nps] def getStatePension: Option[BigDecimal] = {
    val statePension = primaryEmployment.flatMap(_.statePension)
    if (statePension.isDefined) {
      statePension
    } else {
      adjustedNetIncome.flatMap(_.iabdSummaries.flatMap(_.find(_.`type`.contains(StatePension.code)).flatMap(_.amount)))
    }
  }

  //Check the income sources to see if one is of type StatePensionLumpSum
  private[nps] def getStatePensionLumpSum: Option[BigDecimal] =
    incomeSources
      .flatMap(_.find(x => x.incomeType == IncomeTypeStatePensionLumpSum.code))
      .flatMap(_.payAndTax.flatMap(_.totalIncome).flatMap(_.amount))

  private[nps] lazy val primaryEmployment: Option[NpsIncomeSource] = {
    incomeSources.flatMap(_.find(_.employmentType.contains(TaiConstants.PrimaryEmployment)))
  }

  //We need to convert the NpsIncomeSource to the api required by the front end totalling the income tax and taxableIncome
  private[nps] def toTaxCodeIncomeTotal(filteredIncomeSource: List[MergedEmployment]): Option[TaxCodeIncomeTotal] =
    //Only return an object with totals if this list is none empty
    filteredIncomeSource match {
      case Nil => None
      case y => {
        val convertedIncomes = y.sortBy(_.orderField).map(_.toTaxCodeIncomeSummary)
        Some(TaxModelFactory.createTaxCodeIncomeTotal(convertedIncomes))
      }
    }
}

object NpsTaxAccount {
  implicit val formats = Json.format[NpsTaxAccount]
}

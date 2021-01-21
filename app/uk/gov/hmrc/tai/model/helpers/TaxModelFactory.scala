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

import uk.gov.hmrc.tai.calculators.TaxCalculator
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.model.nps._
import uk.gov.hmrc.tai.model.tai.AnnualAccount
import uk.gov.hmrc.tai.model.nps2.{DeductionType, AllowanceType}
import uk.gov.hmrc.tai.model.nps2.IabdType.{PersonalSavingsAllowance, NonCodedIncome}
import uk.gov.hmrc.tai.util.TaiConstants

trait TaxModelFactory {

  def create(nino: String, version: Int,
             employments: Option[TaxCodeIncomeTotal] = None,
             statePension: Option[BigDecimal] = None,
             statePensionLumpSum: Option[BigDecimal] = None,
             occupationalPensions: Option[TaxCodeIncomeTotal] = None,
             taxableStateBenefitIncomes: Option[TaxCodeIncomeTotal] = None,
             taxableStateBenefit: Option[TaxComponent] = None,
             personalAllowanceNps: Option[NpsComponent] = None,
             ceasedEmployments: Option[TaxCodeIncomeTotal] = None,
             totalLiability: Option[NpsTotalLiability] = None,
             incomeSources: Option[List[NpsIncomeSource]] = None,
             adjustedNetIncome: Option[NpsComponent] = None,
             underpaymentPreviousYear: Option[NpsComponent] = None,
             inYearAdjustment: Option[NpsComponent] = None,
             outstandingDebt: Option[NpsComponent] = None,
             childBenefitAmount: Option[BigDecimal] = None,
             npsEmployments: Option[List[NpsEmployment]] = None,
             taxCodeDetails: Option[TaxCodeDetails] = None,
             accounts: List[AnnualAccount] = Nil,
             ceasedEmploymentDetail: Option[CeasedEmploymentDetails] = None) : TaxSummaryDetails = {

    val personalAllowanceTapered = personalAllowanceNps.map(personalAllowance => {
      personalAllowance.amount.getOrElse(BigDecimal(0)) < personalAllowance.sourceAmount.getOrElse(0)
    })
    val personalAllowance = personalAllowanceNps

    val incomes = groupIncomes(employments, statePension, statePensionLumpSum, occupationalPensions,
      taxableStateBenefitIncomes, taxableStateBenefit, ceasedEmployments, totalLiability, incomeSources)

    val marriageAllowance = getMarriageAllowance(totalLiability,taxCodeDetails)

    val npsAni = adjustedNetIncome.map(_.amount.getOrElse(BigDecimal(0)))
    val increasesTax = groupItemsThatIncreaseTax(totalLiability = totalLiability, incomeSources = incomeSources, incomes = incomes,
      npsEmployments = npsEmployments)
    val decreasesTax = groupItemsThatDecreaseTax(totalLiability, incomeSources, personalAllowance, taxCodeDetails, personalAllowanceTapered)
    val underpaymentPrevYr = underpaymentPreviousYear.flatMap(_.sourceAmount).getOrElse(BigDecimal(0))
    val inYearAdjustmentAmount = inYearAdjustment.flatMap(_.sourceAmount).getOrElse(BigDecimal(0))
    val oustandingDebtF = outstandingDebt.flatMap(_.sourceAmount).getOrElse(BigDecimal(0))
    val childBenefitTaxDue = {
      val npsChildBen = totalLiability.flatMap(_.otherTaxDue.map(_.childBenefit.getOrElse(BigDecimal(0)))).getOrElse(BigDecimal(0))
      if (npsChildBen > 0){
        npsChildBen
      }
      else{
        TaxCalculator.calculateChildBenefit(childBenefitAmount.getOrElse(BigDecimal(0)), npsAni.getOrElse(BigDecimal(0)))
      }
    }

    //TODO NPS will now be sending correct extended basic rate band Calculation at R36.4
    val netGiftAid = IncomeHelper.getGiftFromAdjustedNetIncome(adjustedNetIncome)

    val npsPpr = totalLiability.flatMap(_.basicRateExtensions.map(_.personalPensionPayment.getOrElse(BigDecimal(0)))).getOrElse(BigDecimal(0))
    val npsGiftAidRelief = totalLiability.flatMap(_.basicRateExtensions.map(_.giftAidPaymentsRelief.getOrElse(BigDecimal(0)))).getOrElse(BigDecimal(0))
    val npsPprRelief = totalLiability.flatMap(_.basicRateExtensions.map(_.personalPensionPaymentRelief.getOrElse(BigDecimal(0)))).getOrElse(BigDecimal(0))

    //Still use netgift aid amount that has been derived from NPS - but use the relief direct from NPS
    val giftAid = ExtensionRelief(netGiftAid, npsGiftAidRelief)
    val personalPension = ExtensionRelief(npsPpr, npsPprRelief)

    val totalTax = {
      val taxObjects = accounts.flatMap(_.nps).map(_.taxObjects)
      taxObjects.flatMap(_.values).flatMap(_.totalTax).sum
    }

    val taxSummaryDet = {

        //if NPS are sending the values then simply use these figures
        val taxSummaryWithTotals = createTaxSummaryWithTotals(nino, version, increasesTax, decreasesTax,
          totalLiability.map(_.toTotalLiabilitySummary(underpaymentPreviousYear = underpaymentPrevYr,
            inYearAdjustment = inYearAdjustmentAmount, childBenefitAmount = childBenefitAmount.getOrElse(BigDecimal(0)),
            childBenefitTaxDue = childBenefitTaxDue, outstandingDebt = oustandingDebtF,
            marriageAllowance = marriageAllowance, taxCodeDetails = taxCodeDetails, totalTax = totalTax)),
          adjustedNetIncome.flatMap(_.amount), ceasedEmploymentDetail).copy(extensionReliefs = Some(ExtensionReliefs(Some(giftAid), Some(personalPension))))

      //Still need to recalculate to ensure that savings income figures are present
      //TODO This calculation is still required as NPS data does not show all of the allowances in this field
      //It only shows the amount of allowances that have been used. This is an issue with the NPS data.

      //THE METHOD updateTotalLiabilityForIncomeChange is still present and is used to recalculate the tax across the bands
      //This is still required for CY+1
      // as we remove income figures if the employment is ceased
      //This can cause problems when the individual has personal pension, gift aid or personal pension as these are no longer calculated.

      taxSummaryWithTotals.copy(taxCodeDetails = createTaxCodeInfo(taxCodeDetails, taxSummaryWithTotals.decreasesTax))
      //END OF CALC
    }
    taxSummaryDet
  }

  private[helpers] def adjustMarriageAllowance(allowances:  Option[List[TaxCodeComponent]], deductions: Option[List[TaxCodeComponent]]) = {
    val maTypes = List(AllowanceType.MarriedCouplesAllowance.id, AllowanceType.MarriedCouplesAllowance3.id, AllowanceType.MarriedCouplesAllowance2.id,
      AllowanceType.MarriedCouplesAllowance4.id, AllowanceType.MarriedCouplesAllowance5.id)

    val ma = allowances.getOrElse(List()).find(x => maTypes.contains(x.componentType.getOrElse(0)))

    val hpar = deductions.getOrElse(List()).find(x => DeductionType.HigherPersonalAllowanceRestriction.id == x.componentType.getOrElse(0))

    hpar match {
      case Some(hpar) =>
        ma match {
          case Some(ma) =>
            val adjustedAmount = Some(ma.amount.getOrElse(BigDecimal(0)) - hpar.amount.getOrElse(BigDecimal(0)))

            val updated = ma.copy(description = ma.description, amount = adjustedAmount, componentType = ma.componentType)

            Some(allowances.getOrElse(List()).updated(allowances.getOrElse(List()).indexOf(ma), updated))
          case _=> allowances
        }
      case _ => allowances
    }
  }

  private[helpers] def createTaxCodeInfo(taxCodeDetails : Option[TaxCodeDetails], decreasesTax : Option[DecreasesTax]) : Option[TaxCodeDetails] = {

    val allowances = taxCodeDetails.flatMap(_.allowances.map{ allow =>
        List(TaxCodeComponent(description = Some("Tax Free Amount"), componentType = Some(0), amount = decreasesTax.map(_.total))) :::
        allow.filter(x => TaiConstants.ADDITIONAL_TAX_CODE_ALLOWANCE_TYPES.contains(x.componentType.getOrElse(0)))})

    //Take the original deductions list and filter out those Components in TaiConstants.FILTERED_OUT_DEDUCTIONS
    val allDeductions = taxCodeDetails.flatMap(_.deductions)

    val filteredStandardDeductions = allDeductions.map(x => x.filter( n => !TaiConstants.FILTERED_OUT_DEDUCTIONS.contains(n.componentType.getOrElse(0))))

    //Add a further filter on deductions by removing all types that are in TaiConstants.EMP_BEN_DEDUCTIONS
    //All of these types should be grouped in the TaiConstants.DEDUCTION_TYPE_EMP_BEN_BIK_TOTAL component
    val empBens = filteredStandardDeductions.map(_.filter(ded => TaiConstants.EMP_BEN_DEDUCTIONS.contains(ded.componentType.getOrElse(0))))
    val newTotalEmpBen = TaxCodeComponent(componentType = Some(DeductionType.EmployerBenefits.id),
      amount = empBens.map(list => list.foldLeft(BigDecimal(0))(_ + _.amount.getOrElse(BigDecimal(0)))))

    val filteredDeductions = if(newTotalEmpBen.amount.getOrElse(BigDecimal(0)) > BigDecimal(0)) {
      filteredStandardDeductions.map(_.filter(ded => !TaiConstants.EMP_BEN_DEDUCTIONS.contains(ded.componentType.getOrElse(0))) ::: List(newTotalEmpBen))
    }else{
      filteredStandardDeductions.map(_.filter(ded => !TaiConstants.EMP_BEN_DEDUCTIONS.contains(ded.componentType.getOrElse(0))))
    }

    val splitAllowances = allDeductions.map( x => x.exists { n => n.componentType.contains(DeductionType.OtherEarningsOrPension.id) })

    //Now we need to find Married Allowance in allowances
    val allowancesWithMA = adjustMarriageAllowance(allowances, allDeductions)

    val deductionsWithoutHPAR = filteredDeductions.map(x => x.filter(n => n.componentType.getOrElse(0) != DeductionType.HigherPersonalAllowanceRestriction.id))

    val totalAllowances = allowancesWithMA.map(_.foldLeft(BigDecimal(0))(_ + _.amount.getOrElse(0)))
    val totalDeductions = deductionsWithoutHPAR.map(_.foldLeft(BigDecimal(0))(_ + _.amount.getOrElse(0)))

    val total = totalAllowances.getOrElse(BigDecimal(0)) - totalDeductions.getOrElse(BigDecimal(0))
    taxCodeDetails.map(_.copy(deductions = deductionsWithoutHPAR, allowances = allowancesWithMA, total = total,  splitAllowances = splitAllowances))
  }

  private[helpers] def getMarriageAllowance(totalLiability: Option[NpsTotalLiability], taxCodeDetails: Option[TaxCodeDetails]) : Option[MarriageAllowance] ={
    val marriageAllowanceReliefs = totalLiability.flatMap(_.reliefsGivingBackTax.flatMap(_.marriedCouplesAllowance)).getOrElse(BigDecimal(0))

    val maTypes = List(AllowanceType.MarriedCouplesAllowance.id,
      AllowanceType.MarriedCouplesAllowance3.id, AllowanceType.MarriedCouplesAllowance2.id,
      AllowanceType.MarriedCouplesAllowance4.id, AllowanceType.MarriedCouplesAllowance5.id)

    val marriageAllowanceGross = taxCodeDetails.flatMap{
      allow => allow.allowances.flatMap{
        allowances => allowances.find(x => maTypes.contains(x.componentType.getOrElse(0))).flatMap(x => x.amount)
      }
    }.getOrElse(BigDecimal(0))

    if(marriageAllowanceGross == BigDecimal(0) || marriageAllowanceReliefs == BigDecimal(0)) {
      None
    } else {
      Some(MarriageAllowance(marriageAllowanceGross, marriageAllowanceReliefs))
    }
  }

  def convertToNonCodedIncome(npsNonSavings: Option[NpsTax]): Option[Tax] = {
    npsNonSavings.flatMap { npsTax =>
      val iabdSummary = npsTax.totalIncome.flatMap(_.iabdSummaries)
      val nonCoded = iabdSummary.flatMap(_.find(iabd => iabd.`type`.contains(NonCodedIncome.code)))
      nonCoded.map { nonCoded =>
        val newTaiTax = TaxHelper.toTax(npsTax)
        val updatedTax = TaxCalculator.updateTax(newTaiTax, BigDecimal(0) - nonCoded.amount.getOrElse(BigDecimal(0)), BigDecimal(0))
        val nonCodedTax = newTaiTax.totalTax.map(_.-(updatedTax.totalTax.getOrElse(BigDecimal(0))))

        new Tax(totalIncome = nonCoded.amount,
          totalTaxableIncome = nonCoded.amount,
          totalTax = nonCodedTax)
      }
    }
  }

  def groupIncomes(employments: Option[TaxCodeIncomeTotal] = None,
                           statePension: Option[BigDecimal] = None,
                           statePensionLumpSum: Option[BigDecimal] = None,
                           occupationalPensions: Option[TaxCodeIncomeTotal] = None,
                           taxableStateBenefitIncomes: Option[TaxCodeIncomeTotal] = None,
                           taxableStateBenefit: Option[TaxComponent] = None,
                           ceasedEmployments: Option[TaxCodeIncomeTotal] = None,
                           totalLiability: Option[NpsTotalLiability] = None,
                           incomeSources :Option[List[NpsIncomeSource]] = None) :Option[Incomes]= {

    val otherIncome = totalLiability.flatMap(_.otherIncome()).map(_.toTaxComponent(incomeSources))
    val otherPensions = totalLiability.flatMap(_.otherPensions()).map(_.toTaxComponent(incomeSources))
    val dividends = totalLiability.flatMap(_.dividends()).map(_.toTaxComponent(incomeSources))
    val bankInterest = totalLiability.flatMap(_.bankinterest()).map(_.toTaxComponent(incomeSources))
    val unTaxedInterest = totalLiability.flatMap(_.untaxedinterest()).map(_.toTaxComponent(incomeSources))
    val foreignInterest = totalLiability.flatMap(_.foreigninterest()).map(_.toTaxComponent(incomeSources))
    val foreignDividends = totalLiability.flatMap(_.foreigndividends()).map(_.toTaxComponent(incomeSources))

    val incomes: Option[Incomes] = {
      //Add all the values we want to total into a list
      val pensionsTotals = List(statePension,
        statePensionLumpSum,
        occupationalPensions.map(_.totalIncome),
        otherPensions.map(_.amount)
      ).flatten
      val totals = pensionsTotals ::: List(employments.map(_.totalIncome),
        ceasedEmployments.map(_.totalIncome),
        otherIncome.map(_.amount),
        taxableStateBenefitIncomes.map(_.totalIncome),
        taxableStateBenefit.map(_.amount),
        dividends.map(_.amount),
        bankInterest.map(_.amount),
        unTaxedInterest.map(_.amount)
      ).flatten

      totals match {
        case Nil => None
        case x => Some(
          createIncomesWithTotal(employments = employments, statePension = statePension,
            statePensionLumpSum = statePensionLumpSum,
            occupationalPensions = occupationalPensions,
            otherPensions = otherPensions, otherIncome = otherIncome,
            taxableStateBenefitIncomes = taxableStateBenefitIncomes, taxableStateBenefit = taxableStateBenefit,
            ceasedEmployments = ceasedEmployments, dividends = dividends,
            bankBsInterest = bankInterest, untaxedInterest = unTaxedInterest, foreignInterest=foreignInterest, foreignDividends=foreignDividends))
      }
    }
    incomes
  }

  def groupItemsThatIncreaseTax(totalLiability: Option[NpsTotalLiability] = None,
                                        incomeSources :Option[List[NpsIncomeSource]] = None,
                                        incomes: Option[Incomes],
                                        npsEmployments : Option[List[NpsEmployment]] = None) = {

    val benefitsFromEmployment = totalLiability.flatMap(_.benefitsFromEmployment()).map(_.toTaxComponent(incomeSources = incomeSources,
      npsEmployments = npsEmployments))

    val increasesTax :Option[IncreasesTax] =  {
      val totals = List(incomes.map(_.total),
        benefitsFromEmployment.map(_.amount)
      ).flatten

      totals match {
        case Nil => None
        case x => Some(createIncreasesTaxWithTotal(incomes, benefitsFromEmployment))
      }
    }
    increasesTax
  }


  def groupItemsThatDecreaseTax(totalLiability: Option[NpsTotalLiability] = None,
                                        incomeSources :Option[List[NpsIncomeSource]] = None,
                                        personalAllowance : Option[NpsComponent] = None,
                                        taxCodeDetails : Option[TaxCodeDetails] = None,
                                        personalAllowanceTapered : Option[Boolean] = None) : Option[DecreasesTax] = {

    val blindPerson = totalLiability.flatMap(_.blindPerson()).map(_.toTaxComponent(incomeSources))
    val expenses = totalLiability.flatMap(_.expenses()).map(_.toTaxComponent(incomeSources))
    val giftRelated = totalLiability.flatMap(_.giftRelated()).map(_.toTaxComponent(incomeSources))
    val jobExpenses = totalLiability.flatMap(_.jobExpenses()).map(_.toTaxComponent(incomeSources))
    val miscellaneous = totalLiability.flatMap(_.miscellaneous()).map(_.toTaxComponent(incomeSources))
    val pensionContributions = totalLiability.flatMap(_.pensionContributions()).map(_.toTaxComponent(incomeSources))
    val psaCodingComponent = incomeSources.flatMap(_.filter(_.employmentType == Some(TaiConstants.PrimaryEmployment)).headOption.
      flatMap(_.allowances.flatMap(_.filter(_.`type` == Some(AllowanceType.PersonalSavingsAllowance.id)).headOption)))

    val personalSavingsAllowance = psaCodingComponent.map(_.copy(iabdSummaries = Some(List(NpsIabdSummary(amount =
      psaCodingComponent.flatMap(_.amount), `type` = Some(PersonalSavingsAllowance.code)))))).map(_.toTaxComponent(incomeSources))

    val allowanceTAMC = taxCodeDetails.flatMap(taxCodeDetails => taxCodeDetails.allowances).getOrElse(List()).filter{ allowance =>
      allowance.componentType.getOrElse(0) == AllowanceType.PersonalAllowanceReceived.id
    }
    val allowanceTAMCAmount = allowanceTAMC.headOption.flatMap(_.amount)

    val deductionTAMC = taxCodeDetails.flatMap(taxCodeDetails=> taxCodeDetails.deductions).getOrElse(List()).filter { deduction =>
      deduction.componentType.getOrElse(0) == DeductionType.PersonalAllowanceTransferred.id
    }
    val deductionTAMCAmount = deductionTAMC.headOption.flatMap(_.amount.map(x =>  x.unary_-))
    val paTapered = personalAllowanceTapered.getOrElse(false)
    val decreasesTax :Option[DecreasesTax] =  {
      val totals = List(blindPerson.map(_.amount),
        expenses.map(_.amount),
        giftRelated.map(_.amount),
        jobExpenses.map(_.amount),
        miscellaneous.map(_.amount),
        pensionContributions.map(_.amount),
        personalAllowance.flatMap(_.amount),
        personalSavingsAllowance.map(_.amount)
      ).flatten

      totals match {
        case Nil => None
        case x => Some(createDecreasesTaxWithTotal(personalAllowance = personalAllowance,
          blindPerson = blindPerson, expenses = expenses,
          giftRelated = giftRelated, jobExpenses = jobExpenses, miscellaneous = miscellaneous,
          pensionContributions = pensionContributions, paReceivedAmount = allowanceTAMCAmount,
          paTransferredAmount = deductionTAMCAmount, paTapered = paTapered, personalSavingsAllowance = personalSavingsAllowance))
      }
    }
    decreasesTax
  }

  def updateTaxableIncomesTotal(taxableIncomes: TaxCodeIncomes) : TaxCodeIncomes = {
    val totalIncomes = List(
      taxableIncomes.occupationalPensions.map(_.totalIncome),
      taxableIncomes.employments.map(_.totalIncome),
      taxableIncomes.taxableStateBenefitIncomes.map(_.totalIncome),
      taxableIncomes.ceasedEmployments.map(_.totalIncome)
    ).flatten
    val totalIncome = totalIncomes.fold(BigDecimal(0))((totalIncome,amount) => amount + totalIncome)

    val totalTaxableIncomes = List(
      taxableIncomes.occupationalPensions.map(_.totalTaxableIncome),
      taxableIncomes.employments.map(_.totalTaxableIncome),
      taxableIncomes.taxableStateBenefitIncomes.map(_.totalTaxableIncome),
      taxableIncomes.ceasedEmployments.map(_.totalTaxableIncome)
    ).flatten
    val totalTaxableIncome = totalTaxableIncomes.fold(BigDecimal(0))((totalTaxable,amount) => amount + totalTaxable)

    val totalTaxes = List(
      taxableIncomes.occupationalPensions.map(_.totalTax),
      taxableIncomes.employments.map(_.totalTax),
      taxableIncomes.taxableStateBenefitIncomes.map(_.totalTax),
      taxableIncomes.ceasedEmployments.map(_.totalTax)
    ).flatten
    val totalTax = totalTaxes.fold(BigDecimal(0))((totalTax,amount) => amount + totalTax)
    taxableIncomes.copy(totalIncome = totalIncome, totalTaxableIncome= totalTaxableIncome, totalTax= totalTax)
  }

  def updateNoneTaxableIncomesTotal(income: NoneTaxCodeIncomes) : NoneTaxCodeIncomes = {
    val totals = List(
      income.statePension,
      income.statePensionLumpSum,
      income.otherPensions.map(_.amount),
      income.otherIncome.map(_.amount),
      income.taxableStateBenefit.map(_.amount),
      income.dividends.map(_.amount),
      income.untaxedInterest.map(_.amount),
      income.bankBsInterest.map(_.amount)
    ).flatten
    val totalIncome = totals.fold(BigDecimal(0))((incomeVal,amount) => amount + incomeVal)
    income.copy(totalIncome = totalIncome)
  }

  def updateIncomesTotal(income: Incomes) : Incomes = {

    val updatedTaxableIncomes = updateTaxableIncomesTotal(income.taxCodeIncomes)
    val updatedNoneTaxableIncomes = updateNoneTaxableIncomesTotal(income.noneTaxCodeIncomes)

    income.copy(taxCodeIncomes = updatedTaxableIncomes, noneTaxCodeIncomes = updatedNoneTaxableIncomes,
      total = (updatedTaxableIncomes.totalIncome + updatedNoneTaxableIncomes.totalIncome))
  }

  def createIncomesWithTotal(employments: Option[TaxCodeIncomeTotal] = None,
                                     statePension: Option[BigDecimal] = None,
                                     statePensionLumpSum: Option[BigDecimal] = None,
                                     occupationalPensions: Option[TaxCodeIncomeTotal] = None,
                                     otherPensions: Option[TaxComponent] = None,
                                     otherIncome : Option[TaxComponent] = None,
                                     taxableStateBenefitIncomes: Option[TaxCodeIncomeTotal] = None,
                                     taxableStateBenefit: Option[TaxComponent] = None,
                                     ceasedEmployments: Option[TaxCodeIncomeTotal] = None,
                                     dividends: Option[TaxComponent] = None,
                                     untaxedInterest: Option[TaxComponent] = None,
                                     bankBsInterest: Option[TaxComponent] = None,
                                     foreignInterest: Option[TaxComponent] = None,
                                     foreignDividends: Option[TaxComponent] = None) : Incomes = {


    val hasDuplicateEmploymentNames: Boolean = {
      val employmentNames: List[String] = employments.map(_.taxCodeIncomes.map(_.name).toList).getOrElse(Nil)
      val ceasedEmploymentNames: List[String] = ceasedEmployments.map(_.taxCodeIncomes.map(_.name).toList).getOrElse(Nil)
      val pensionNames: List[String] = occupationalPensions.map(_.taxCodeIncomes.map(_.name).toList).getOrElse(Nil)
      val taxableStateBenefitNames: List[String] = taxableStateBenefitIncomes.map(_.taxCodeIncomes.map(_.name).toList).getOrElse(Nil)

      val incomeNames = employmentNames ::: ceasedEmploymentNames ::: pensionNames ::: taxableStateBenefitNames
      (incomeNames.size > incomeNames.distinct.size)
    }

    val taxableIncomes = TaxCodeIncomes(employments = employments,
      occupationalPensions = occupationalPensions,
      taxableStateBenefitIncomes = taxableStateBenefitIncomes,
      ceasedEmployments = ceasedEmployments, hasDuplicateEmploymentNames = hasDuplicateEmploymentNames,
      totalIncome = BigDecimal(0),  totalTaxableIncome = BigDecimal(0), totalTax = BigDecimal(0))

    val noneTaxableIncomes = NoneTaxCodeIncomes(statePension = statePension,
      statePensionLumpSum = statePensionLumpSum, otherPensions=otherPensions, otherIncome=otherIncome,
      taxableStateBenefit=taxableStateBenefit, dividends = dividends,
      untaxedInterest = untaxedInterest, bankBsInterest = bankBsInterest,foreignInterest = foreignInterest,foreignDividends = foreignDividends,
      totalIncome = BigDecimal(0))

    val income = Incomes(taxCodeIncomes = taxableIncomes, noneTaxCodeIncomes=noneTaxableIncomes,
      total=BigDecimal(0))

    updateIncomesTotal(income)

  }

  def createIncreasesTaxWithTotal(incomes: Option[Incomes] = None, benefitsFromEmployment : Option[TaxComponent] = None) : IncreasesTax = {
    new IncreasesTax(incomes, benefitsFromEmployment, getIncreasesTaxTotal(incomes, benefitsFromEmployment))
  }

  def getIncreasesTaxTotal(incomes: Option[Incomes] = None, benefitsFromEmployment : Option[TaxComponent] = None) : BigDecimal = {
    val totals = List(incomes.map(_.total),
      benefitsFromEmployment.map(_.amount)
    ).flatten
    totals.fold(BigDecimal(0))((totalIncome, amount) => amount + totalIncome)
  }

  def createDecreasesTaxWithTotal(personalAllowance : Option[NpsComponent] = None,
                                          blindPerson : Option[TaxComponent] = None,
                                          expenses : Option[TaxComponent] = None,
                                          giftRelated : Option[TaxComponent] = None,
                                          jobExpenses : Option[TaxComponent] = None,
                                          miscellaneous : Option[TaxComponent] = None,
                                          pensionContributions : Option[TaxComponent] = None,
                                          paReceivedAmount : Option[BigDecimal] = None,
                                          paTransferredAmount : Option[BigDecimal] = None, paTapered : Boolean,
                                          personalSavingsAllowance : Option[TaxComponent] = None
                                         ) : DecreasesTax = {

    val decreasesTax = new DecreasesTax(personalAllowance = personalAllowance.flatMap(_.amount),
      personalAllowanceSourceAmount = personalAllowance.flatMap(_.sourceAmount),
      blindPerson = blindPerson, expenses = expenses,
      giftRelated = giftRelated, jobExpenses = jobExpenses, miscellaneous = miscellaneous,
      pensionContributions = pensionContributions, paReceivedAmount = paReceivedAmount, paTransferredAmount = paTransferredAmount,
      paTapered = paTapered, total=BigDecimal(0),
      personalSavingsAllowance = personalSavingsAllowance)

    updateDecreasesTaxTotal(decreasesTax)
  }

  def updateDecreasesTaxTotal(decreasesTax : DecreasesTax) : DecreasesTax = {
    lazy val total :BigDecimal= {
      val totals = List(decreasesTax.blindPerson.map(_.amount),
        decreasesTax.expenses.map(_.amount),
        decreasesTax.giftRelated.map(_.amount),
        decreasesTax.jobExpenses.map(_.amount),
        decreasesTax.miscellaneous.map(_.amount),
        decreasesTax.pensionContributions.map(_.amount),
        decreasesTax.personalAllowance,
        decreasesTax.personalSavingsAllowance.map(_.amount)
      ).flatten
      totals.fold(BigDecimal(0))((totalIncome,amount) => amount + totalIncome)
    }

    val paTransferred = decreasesTax.paTransferredAmount.getOrElse(BigDecimal(0))
    val paRecieved = decreasesTax.paReceivedAmount.getOrElse(BigDecimal(0))


    decreasesTax.copy(total = total + paTransferred + paRecieved)
  }

  def createTaxSummaryWithTotals(nino: String,
                                         version: Int,
                                         increasesTax: Option[IncreasesTax] = None,
                                         decreasesTax: Option[DecreasesTax] = None,
                                         totalLiability : Option[TotalLiability] = None,
                                         adjustedNetIncome : Option[BigDecimal],
                                         ceasedEmploymentDetail: Option[CeasedEmploymentDetails]) : TaxSummaryDetails = {
    val taxSummary = TaxSummaryDetails(nino = nino,
      version = version,
      increasesTax = increasesTax,
      decreasesTax = decreasesTax,
      totalLiability = totalLiability,
      adjustedNetIncome = adjustedNetIncome.getOrElse(BigDecimal(0)),
      ceasedEmploymentDetail = ceasedEmploymentDetail
    )
    taxSummary
  }

  def createTaxCodeIncomeTotal(taxCodeIncomes: List[TaxCodeIncomeSummary]) : TaxCodeIncomeTotal = {
    val totalIncome = taxCodeIncomes.foldLeft(BigDecimal(0))((total,detail) => detail.income.getOrElse(BigDecimal(0))  + total)
    val totalTax = taxCodeIncomes.foldLeft(BigDecimal(0))((total,detail) => detail.tax.totalTax.getOrElse(BigDecimal(0))  + total)
    val totalTaxableIncome = taxCodeIncomes.foldLeft(BigDecimal(0))((total,detail) => detail.tax.totalTaxableIncome.getOrElse(BigDecimal(0))  + total)
    TaxCodeIncomeTotal(taxCodeIncomes, totalIncome, totalTax, totalTaxableIncome)
  }

}

object TaxModelFactory extends TaxModelFactory

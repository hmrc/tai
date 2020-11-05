/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.tai.service

import com.google.inject.{Inject, Singleton}
import org.joda.time.Days
import play.Logger
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.tai.config.{FeatureTogglesConfig, NpsConfig}
import uk.gov.hmrc.tai.connectors.{DesConnector, NpsConnector}
import uk.gov.hmrc.tai.model.{IabdUpdateAmount, RtiCalc}
import uk.gov.hmrc.tai.model.helpers.IncomeHelper
import uk.gov.hmrc.tai.model.nps.{NpsDate, NpsEmployment, NpsIabdRoot}
import uk.gov.hmrc.tai.model.nps2.Income.Live
import uk.gov.hmrc.tai.model.nps2.{IabdType, Income}
import uk.gov.hmrc.tai.model.rti.PayFrequency._
import uk.gov.hmrc.tai.model.rti._
import uk.gov.hmrc.tai.model.tai._
import uk.gov.hmrc.tai.util.TaiConstants
import uk.gov.hmrc.tai.util.TaiConstants._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

@Singleton
class AutoUpdatePayService @Inject()(
  nps: NpsConnector,
  des: DesConnector,
  featureTogglesConfig: FeatureTogglesConfig,
  npsConfig: NpsConfig,
  incomeHelper: IncomeHelper)(
  implicit ec: ExecutionContext
) {

  val autoUpdate: Boolean = npsConfig.autoUpdatePayEnabled.getOrElse(false)
  val IabdUpdateSourceInternetCalculated: Int = if (featureTogglesConfig.desUpdateEnabled) 46 else 1

  def updateIncomes(
    nino: Nino,
    taxYear: Int,
    allEmployments: List[NpsEmployment],
    allNpsEstimatedPays: List[NpsIabdRoot],
    version: Int,
    rtiData: Option[RtiData]
  )(implicit hc: HeaderCarrier): List[RtiCalc] =
    if (autoUpdate) {
      updateCeasedAndRtiIncomes(nino, taxYear, allEmployments, allNpsEstimatedPays, version, rtiData) getOrElse Nil
    } else {
      Nil
    }

  private[service] def updateCeasedAndRtiIncomes(
    nino: Nino,
    taxYear: Int,
    allEmployments: List[NpsEmployment],
    allNpsEstimatedPays: List[NpsIabdRoot],
    version: Int,
    rtiData: Option[RtiData]
  )(implicit hc: HeaderCarrier): Try[List[RtiCalc]] =
    Try {

      val filteredEmploymentsAndOccPensions = allEmployments.filter { employment =>
        incomeHelper.isEditableByAutoUpdateService(
          otherIncomeSourceIndicator = employment.otherIncomeSourceIndicator,
          jsaIndicator = employment.receivingJobseekersAllowance)
      }

      //Split the employments into live and ceased
      val employmentSplitOnStatus = filteredEmploymentsAndOccPensions.partition(_.employmentStatus.contains(Live.code))

      val updateCeasedEmployments = getCeasedIncomeFinalSalaries(employmentSplitOnStatus._2, allNpsEstimatedPays)

      //Filtering employments based on Not to update sources
      val filteredEmploymentsForRTIUpdate = employmentSplitOnStatus._1

      //Get a tuple with the updated values for this and next year
      val (otherEmploymentsCY, otherEmploymentsNY, rtiCalcList) =
        getRtiUpdateAmounts(nino, taxYear, filteredEmploymentsForRTIUpdate, rtiData)
      if (npsConfig.postCalcEnabled.contains(true)) {
        updateEmploymentData(
          nino,
          taxYear,
          updateCeasedEmployments,
          otherEmploymentsCY,
          otherEmploymentsNY,
          rtiCalcList,
          version,
          allNpsEstimatedPays
        )
      }

      rtiCalcList

    }

  private[service] def getCeasedIncomeFinalSalaries(
    employments: List[NpsEmployment],
    estimatedPays: List[NpsIabdRoot]): List[IabdUpdateAmount] = {

    def isCeasedEmploymentWithCessationPay(x: NpsEmployment): Boolean =
      !x.employmentStatus.contains(Live.code) &&
        x.cessationPayThisEmployment.exists(_ > 0) &&
        x.endDate.exists(i => TaxYear().withinTaxYear(i.localDate)) && ceaseEmploymentAmountDifferent(x, estimatedPays)

    employments
      .filter(isCeasedEmploymentWithCessationPay)
      .map { employment =>
        IabdUpdateAmount(
          employmentSequenceNumber = employment.sequenceNumber,
          source = Some(IabdUpdateSourceInternetCalculated),
          grossAmount = employment.cessationPayThisEmployment.map(_.intValue).getOrElse(0)
        )
      }
  }

  private[service] def ceaseEmploymentAmountDifferent(
    employment: NpsEmployment,
    estimatedPays: List[NpsIabdRoot]
  ): Boolean =
    employment.cessationPayThisEmployment
      .flatMap { ceasedPay =>
        estimatedPays
          .find(_.employmentSequenceNumber.contains(employment.sequenceNumber))
          .map(_.grossAmount.getOrElse(BigDecimal(0)).intValue != ceasedPay.intValue)
      }
      .getOrElse(false)

  private[service] def getRtiUpdateAmounts(
    nino: Nino,
    taxYear: Int,
    employments: List[NpsEmployment],
    rtiDataIn: Option[RtiData]
  )(implicit hc: HeaderCarrier): (List[IabdUpdateAmount], List[IabdUpdateAmount], List[RtiCalc]) = {

    val rtiData = if (employments.isEmpty) {
      None
    } else {
      rtiDataIn
    }
    val updateAmounts =
      rtiData
        .map(_.employments.flatMap { rti =>
          //Check to see if this is an employment we want to update
          val foundEmployment = findEmployment(
            rti,
            employments
          )
          foundEmployment.map {
            employment =>
              val lastPaymentDate = rti.payments.lastOption.map(_.paidOn)
              val taxablePayYTD = rti.payments.lastOption.map(_.taxablePayYTD)

              val (calEstPayCY, calEstPayNY) = estimatedPay(rti, employment)

              val (npsUpdateAmountCY, npsUpdateAmountNY) =
                getUpdateAmounts(employment.sequenceNumber, calEstPayCY, calEstPayNY)

              val rtiCalc = new RtiCalc(
                employmentId = employment.sequenceNumber,
                employmentType = employment.employmentType,
                employmentStatus = employment.employmentStatus.getOrElse(0),
                employerName = employment.employerName.getOrElse(""),
                totalPayToDate = taxablePayYTD.getOrElse(BigDecimal(0)),
                calculationResult = calEstPayCY,
                paymentDate = lastPaymentDate,
                payFrequency = rti.payments.lastOption.map(_.payFrequency)
              )

              (npsUpdateAmountCY, npsUpdateAmountNY, Some(rtiCalc))
          }
        })
        .getOrElse(Nil)

    (updateAmounts.flatMap(_._1), updateAmounts.flatMap(_._2), updateAmounts.flatMap(_._3))
  }

  private[service] def getUpdateAmounts(
    sequenceNo: Int,
    calEstPayCY: Option[BigDecimal],
    calcEstPayNY: Option[BigDecimal]): (Option[IabdUpdateAmount], Option[IabdUpdateAmount]) =
    (npsConfig.updateSourceEnabled, calEstPayCY, calcEstPayNY) match {
      case (Some(true), Some(estPayCY), Some(estPayNY)) =>
        (
          Some(
            IabdUpdateAmount(
              employmentSequenceNumber = sequenceNo,
              grossAmount = estPayCY.intValue(),
              source = Some(IabdUpdateSourceInternetCalculated)
            )),
          Some(
            IabdUpdateAmount(
              employmentSequenceNumber = sequenceNo,
              grossAmount = estPayNY.intValue(),
              source = Some(IabdUpdateSourceInternetCalculated)
            )))

      case (_, Some(estPayCY), Some(estPayNY)) =>
        (
          Some(IabdUpdateAmount(employmentSequenceNumber = sequenceNo, grossAmount = estPayCY.intValue())),
          Some(IabdUpdateAmount(employmentSequenceNumber = sequenceNo, grossAmount = estPayNY.intValue())))

      case (_, Some(estPayCY), None) =>
        (Some(IabdUpdateAmount(employmentSequenceNumber = sequenceNo, grossAmount = estPayCY.intValue())), None)

      case _ => (None, None)
    }

  def estimatedPay(rtiEmp: RtiEmployment, npsEmployment: NpsEmployment): (Option[BigDecimal], Option[BigDecimal]) = {

    val startDt = npsEmployment.startDate
    val isMidYear = startDt.localDate.isAfter(TaxYear().start)

    val (estPayCY, estPayNY) = if (isMidYear) {
      getMidYearEstimatedPay(rtiEmp.payFrequency, rtiEmp, startDt, npsEmployment.employmentType)
    } else {
      val estPayContinuousCY = getContinuousEstimatedPay(rtiEmp.payFrequency, rtiEmp, npsEmployment.employmentType)
      (estPayContinuousCY, estPayContinuousCY)
    }
    (getFinalEstPayWithDefault(estPayCY, rtiEmp), getFinalEstPayWithDefault(estPayNY, rtiEmp))
  }

  private[service] def getMidYearEstimatedPay(
    payFrequency: PayFrequency.Value,
    rtiEmp: RtiEmployment,
    start: NpsDate,
    employmentType: Int): (Option[BigDecimal], Option[BigDecimal]) = {

    val remainingDays = Days.daysBetween(start.localDate, TaxYear().end).getDays + 1

    payFrequency match {
      case ((Weekly | Fortnightly | FourWeekly | Monthly)) =>
        val estPay = rtiEmp.payments.lastOption.flatMap { pmt =>
          Some(pmt.taxablePayYTD / (Days.daysBetween(start.localDate, pmt.paidOn).getDays + 1))
        }
        (estPay.map(_ * remainingDays), estPay.map(_ * RegularYear.NoOfDays.id))
      case (Quarterly) =>
        val estPayCY = rtiEmp.payments.headOption.flatMap { firstPmt =>
          rtiEmp.payments.lastOption.map { lastPmt =>
            val daysBetween = Days.daysBetween(lastPmt.paidOn, TaxYear().end).getDays + 1
            lastPmt.taxablePayYTD + (firstPmt.taxablePayYTD * getRemainingQuarter(daysBetween))
          }
        }
        (estPayCY, rtiEmp.payments.headOption.map(_.taxablePayYTD * NoOfMonths.Quarterly.id))
      case (BiAnnually) =>
        val estPayCY = rtiEmp.payments.headOption.flatMap { firstPmt =>
          rtiEmp.payments.lastOption.map { lastPmt =>
            lastPmt.taxablePayYTD + (firstPmt.taxablePayYTD * getRemainingBiAnnual(lastPmt))
          }
        }
        (estPayCY, rtiEmp.payments.headOption.map(_.taxablePayYTD * NoOfMonths.BiAnnually.id))
      case (Annually) =>
        val estPay = rtiEmp.payments.lastOption.map { pmt =>
          pmt.taxablePayYTD
        }
        (estPay, estPay)
      case (OneOff) =>
        val estPay = rtiEmp.payments.lastOption.map { pmt =>
          pmt.taxablePayYTD
        }
        (estPay, None)
      case _ =>
        val estPay = Some {
          rtiEmp.taxablePayYTD.max {
            if (employmentType == Income.Live.code) {
              TaiConstants.DEFAULT_PRIMARY_PAY
            } else {
              TaiConstants.DEFAULT_SECONDARY_PAY
            }
          }
        }
        (estPay, estPay)
    }
  }

  private[service] def getContinuousEstimatedPay(
    payFrequency: PayFrequency.Value,
    rtiEmp: RtiEmployment,
    employmentType: Int): (Option[BigDecimal]) =
    payFrequency match {
      case ((Weekly | Fortnightly | FourWeekly)) =>
        rtiEmp.payments.lastOption.flatMap { pmt =>
          pmt.weekOfTaxYear.map { weekNo =>
            pmt.taxablePayYTD / weekNo * RegularYear.NoOfWeeks.id
          }
        }
      case (Monthly) =>
        rtiEmp.payments.lastOption.flatMap { pmt =>
          getEstPayWithMonthNo(pmt, RegularYear.NoOfMonths.id)
        }
      case (Quarterly) =>
        rtiEmp.payments.headOption.flatMap { pmt =>
          getEstPayWithMonthNo(pmt, NoOfMonths.Quarterly.id)
        }
      case (BiAnnually) =>
        rtiEmp.payments.headOption.flatMap { pmt =>
          getEstPayWithMonthNo(pmt, NoOfMonths.BiAnnually.id)
        }
      case (Annually | OneOff) =>
        rtiEmp.payments.lastOption.map { pmt =>
          pmt.taxablePayYTD
        }
      case _ =>
        Some {
          rtiEmp.taxablePayYTD.max {
            if (employmentType == Income.Live.code) {
              TaiConstants.DEFAULT_PRIMARY_PAY
            } else {
              TaiConstants.DEFAULT_SECONDARY_PAY
            }
          }
        }
    }

  private[service] def getEstPayWithMonthNo(pmt: RtiPayment, noOfMonths: Int): Option[BigDecimal] =
    pmt.monthOfTaxYear.map { monthNo =>
      pmt.taxablePayYTD / monthNo * noOfMonths
    }

  private[service] def getRemainingBiAnnual(lastPmt: RtiPayment): Int = {
    val daysBetween = Days.daysBetween(lastPmt.paidOn, TaxYear().end).getDays + 1

    if (daysBetween > SIX_MONTHS.days) SIX_MONTHS.remainingBiAnnual else 0
  }

  private[service] def getRemainingQuarter(daysBetween: Int): Int =
    daysBetween match {
      case _ if daysBetween > NINE_MONTHS.days =>
        NINE_MONTHS.remainingQuarter
      case _ if daysBetween > SIX_MONTHS.days && daysBetween < NINE_MONTHS.days =>
        SIX_MONTHS.remainingQuarter
      case _ if daysBetween > THREE_MONTHS.days && daysBetween < SIX_MONTHS.days =>
        THREE_MONTHS.remainingQuarter
      case _ => 0
    }

  private[service] def getFinalEstPayWithDefault(
    estPay: Option[BigDecimal],
    rtiEmp: RtiEmployment): Option[BigDecimal] =
    for {
      estimatedPay <- estPay
      payment      <- rtiEmp.payments.lastOption
      largestAmount = estimatedPay.max(payment.taxablePayYTD)
    } yield largestAmount

  private[service] def compareTaxDistrictNos(employmentTaxDistrictNo: String, officeNo: String): Boolean =
    if (employmentTaxDistrictNo == officeNo) {
      true
    } else {
      try {
        val empNo = employmentTaxDistrictNo.toInt
        val offNo = officeNo.toInt
        empNo == offNo
      } catch {
        case _: Exception => false
      }
    }

  private[service] def findEmployment(
    rtiEmp: RtiEmployment,
    npsEmployments: List[NpsEmployment]
  ): Option[NpsEmployment] =
    npsEmployments.filter(e =>
      e.payeNumber == rtiEmp.payeRef && compareTaxDistrictNos(e.taxDistrictNumber, rtiEmp.officeRefNo)) match {
      case x if x.size > 1 => x.find(_.worksNumber == rtiEmp.currentPayId)
      case x               => x.headOption
    }

  def updateEmploymentData(
    nino: Nino,
    taxYear: Int,
    ceasedUpdated: List[IabdUpdateAmount],
    currentYearUpdated: List[IabdUpdateAmount],
    nextYearUpdated: List[IabdUpdateAmount],
    rtiCalcs: List[RtiCalc],
    version: Int,
    iabdPays: List[NpsIabdRoot])(implicit hc: HeaderCarrier): Future[Seq[HttpResponse]] = {

    val filteredOther = currentYearUpdated.filter { iabd =>
      val totalPayToDate = rtiCalcs.find(_.employmentId == iabd.employmentSequenceNumber).map(_.totalPayToDate)

      val payRecord = iabdPays.find(
        x =>
          x.`type` == IabdType.NewEstimatedPay.code &&
            x.employmentSequenceNumber.contains(iabd.employmentSequenceNumber))
      val originalAmount = payRecord.flatMap(_.grossAmount)
      val newAmount = Some(iabd.grossAmount)
      val source = payRecord.flatMap(_.source)

      forceUpdateEmploymentData(totalPayToDate, originalAmount) || (!IABD_TYPES_DO_NOT_OVERWRITE.contains(source) &&
      newAmount != originalAmount)
    }

    //Only update Next Year if current year is being updated
    val filteredNY = nextYearUpdated.filter { iabd =>
      filteredOther.exists(x => x.employmentSequenceNumber == iabd.employmentSequenceNumber)
    }
    val updateThisYear = ceasedUpdated ::: filteredOther
    if (updateThisYear.nonEmpty) {
      Logger.info("Auto Update for User: " + nino.nino)

      val updateEmpData: Future[HttpResponse] =
        if (featureTogglesConfig.desUpdateEnabled)
          des.updateEmploymentDataToDes(nino, taxYear, IabdType.NewEstimatedPay.code, version, updateThisYear)
        else
          nps.updateEmploymentData(nino, taxYear, IabdType.NewEstimatedPay.code, version, updateThisYear)

      updateEmpData.map(Seq.apply(_)).flatMap { r1 =>
        val updateEmpDataNextYear: Future[HttpResponse] =
          if (featureTogglesConfig.desUpdateEnabled)
            des.updateEmploymentDataToDes(
              nino = nino,
              year = taxYear + 1,
              iabdType = IabdType.NewEstimatedPay.code,
              version = version + 1,
              updateAmounts = filteredNY)
          else
            nps.updateEmploymentData(
              nino = nino,
              year = taxYear + 1,
              iabdType = IabdType.NewEstimatedPay.code,
              version = version + 1,
              updateAmounts = filteredNY)

        updateEmpDataNextYear
          .map(Seq.apply(_))
          .recover { case NonFatal(_) => Seq.empty }
          .map(r2 => r1 ++ r2)
      }

    } else {
      Future.successful(Seq.empty)
    }
  }

  def forceUpdateEmploymentData(totalPayToDate: Option[BigDecimal], originalAmount: Option[BigDecimal]): Boolean = {
    val result: Option[Boolean] = for {
      t <- totalPayToDate
      o <- originalAmount
    } yield {
      t > o
    }
    result.getOrElse(false)
  }

}

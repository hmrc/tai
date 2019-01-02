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

package uk.gov.hmrc.tai.calculators

import org.joda.time.{Days, LocalDate}
import uk.gov.hmrc.tai.model.enums.PayFreq
import uk.gov.hmrc.tai.model.enums.PayFreq.PayFreq
import uk.gov.hmrc.tai.model.{CalculatedPay, PayDetails}
import uk.gov.hmrc.tai.util.TaiConstants

import scala.math.BigDecimal.RoundingMode

object EstimatedPayCalculator {


  def calculate(payDetails : PayDetails) : CalculatedPay = {

    val net = payDetails.taxablePay.map(amount => calculatePay(frequency = payDetails.paymentFrequency, pay = amount,
        days = payDetails.days.getOrElse(0)) + payDetails.bonus.getOrElse(BigDecimal(0)))
    val gross = payDetails.pay.map(amount => calculatePay(frequency = payDetails.paymentFrequency, pay = amount,
        days = payDetails.days.getOrElse(0)) + payDetails.bonus.getOrElse(BigDecimal(0)))
    val inYear = payDetails.startDate.exists(uk.gov.hmrc.time.TaxYearResolver.fallsInThisTaxYear)


    if(payDetails.startDate.isDefined && inYear){
      val apportioned = payDetails.startDate.map { startDate =>
        (apportion(net.getOrElse(BigDecimal(0)), startDate),
         apportion(gross.getOrElse(BigDecimal(0)), startDate))
      }
      val appNet = apportioned.map(_._1)
      val appGross = apportioned.map(_._2)
      CalculatedPay(annualAmount = gross, netAnnualPay = if(net.isEmpty) appGross else appNet, grossAnnualPay = appGross, startDate = payDetails.startDate)
    }
    else{
      CalculatedPay(annualAmount = gross, netAnnualPay = if(net.isEmpty) gross else net, grossAnnualPay = gross)
    }
  }

  def calculatePay(pay : BigDecimal, frequency : PayFreq, days : Int): BigDecimal = {
    val freqMonthly = 12
    val freqWeekly = 52
    val freqBiWeekly = 26
    val daysInYear = 365

    frequency match {
      case PayFreq.monthly => {pay * freqMonthly}
      case PayFreq.weekly => {pay * freqWeekly}
      case PayFreq.fortnightly => {pay * freqBiWeekly}
      case PayFreq.other => {
        (if(days > 0 && days <= daysInYear) pay * (BigDecimal(daysInYear) / days) else BigDecimal(0)).setScale(0,RoundingMode.DOWN)}
    }
  }

  def apportion(annualAmount : BigDecimal, startDate : LocalDate): BigDecimal = {
    val startDateCY = uk.gov.hmrc.time.TaxYearResolver.startOfCurrentTaxYear
    val startDateNY = startDateCY.plusDays(TaiConstants.DAYS_IN_YEAR)
    val workingStartDate = TaxCalculator.getStartDateInCurrentFinancialYear(startDate)

    val daysToBePaidFor: Int = Days.daysBetween(workingStartDate, startDateNY).getDays

    if (daysToBePaidFor > 0) {
        if (annualAmount > 0 && daysToBePaidFor < TaiConstants.DAYS_IN_YEAR) {
          (annualAmount / TaiConstants.DAYS_IN_YEAR) * daysToBePaidFor
        } else {
          annualAmount
        }.setScale(2, RoundingMode.FLOOR)
    } else {
      BigDecimal(0)
    }.setScale(0,RoundingMode.DOWN)
  }

}

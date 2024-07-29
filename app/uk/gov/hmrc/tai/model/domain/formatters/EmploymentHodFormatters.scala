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

package uk.gov.hmrc.tai.model.domain.formatters

import play.api.libs.json.Reads.localDateReads
import play.api.libs.json._
import uk.gov.hmrc.tai.model.api.EmploymentCollection
import uk.gov.hmrc.tai.model.domain.income.TaxCodeIncomeStatus
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.tai.{JsonExtra, TaxYear}

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.util.matching.Regex

trait EmploymentHodFormatters {

  implicit val stringMapFormat: Format[Map[String, BigDecimal]] =
    JsonExtra.mapFormat[String, BigDecimal]("type", "amount")

  implicit val employmentHodReads: Reads[Employment] = new Reads[Employment] {
    private val dateReadsFromHod: Reads[LocalDate] = localDateReads("dd/MM/yyyy")

    override def reads(json: JsValue): JsResult[Employment] = {
      val name = (json \ "employerName").as[String]
      val payrollNumber = (json \ "worksNumber").asOpt[String]
      val startDate = (json \ "startDate").as[LocalDate](dateReadsFromHod)
      val endDate = (json \ "endDate").asOpt[LocalDate](dateReadsFromHod)
      val taxDistrictNumber = numberChecked((json \ "taxDistrictNumber").as[String])
      val payeNumber = (json \ "payeNumber").as[String]
      val sequenceNumber = (json \ "sequenceNumber").as[Int]
      val cessationPay = (json \ "cessationPayThisEmployment").asOpt[BigDecimal]
      val payrolledBenefit = (json \ "payrolledTaxYear").asOpt[Boolean].getOrElse(false) || (json \ "payrolledTaxYear1")
        .asOpt[Boolean]
        .getOrElse(false)
      val receivingOccupationalPension = (json \ "receivingOccupationalPension").as[Boolean]
      val status = TaxCodeIncomeStatus.employmentStatusFromNps(json)
      JsSuccess(
        Employment(
          name,
          status,
          payrollNumber,
          startDate,
          endDate,
          Nil,
          taxDistrictNumber,
          payeNumber,
          sequenceNumber,
          cessationPay,
          payrolledBenefit,
          receivingOccupationalPension
        )
      )
    }
  }

  implicit val employmentCollectionHodReads: Reads[EmploymentCollection] = (json: JsValue) =>
    JsSuccess(EmploymentCollection(json.as[Seq[Employment]], None))

  private def niFigure(json: JsValue): Option[Map[String, BigDecimal]] = (json \ "niLettersAndValues")
    .asOpt[JsArray]
    .map(x => x \\ "niFigure")
    .flatMap(_.headOption)
    .map(_.asOpt[Map[String, BigDecimal]].getOrElse(Map()))

  val paymentHodReads: Reads[Payment] = (json: JsValue) => {

    val mandatoryMoneyAmount = (json \ "mandatoryMonetaryAmount").as[Map[String, BigDecimal]]

    val payment = Payment(
      date = (json \ "pmtDate").as[LocalDate],
      amountYearToDate = mandatoryMoneyAmount("TaxablePayYTD"),
      taxAmountYearToDate = mandatoryMoneyAmount("TotalTaxYTD"),
      nationalInsuranceAmountYearToDate = niFigure(json).flatMap(_.get("EmpeeContribnsYTD")).getOrElse(0),
      amount = mandatoryMoneyAmount("TaxablePay"),
      taxAmount = mandatoryMoneyAmount("TaxDeductedOrRefunded"),
      nationalInsuranceAmount = niFigure(json).flatMap(_.get("EmpeeContribnsInPd")).getOrElse(0),
      payFrequency = (json \ "payFreq").as[PaymentFrequency](paymentFrequencyFormatFromHod),
      duplicate = (json \ "duplicate").asOpt[Boolean]
    )

    JsSuccess(payment)
  }

  val paymentFrequencyFormatFromHod: Format[PaymentFrequency] = new Format[PaymentFrequency] {
    override def reads(json: JsValue): JsResult[PaymentFrequency] = json.as[String] match {
      case Weekly.value      => JsSuccess(Weekly)
      case FortNightly.value => JsSuccess(FortNightly)
      case FourWeekly.value  => JsSuccess(FourWeekly)
      case Monthly.value     => JsSuccess(Monthly)
      case Quarterly.value   => JsSuccess(Quarterly)
      case BiAnnually.value  => JsSuccess(BiAnnually)
      case Annually.value    => JsSuccess(Annually)
      case OneOff.value      => JsSuccess(OneOff)
      case Irregular.value   => JsSuccess(Irregular)
      case _                 => throw new IllegalArgumentException("Invalid payment frequency")
    }

    override def writes(paymentFrequency: PaymentFrequency): JsValue = JsString(paymentFrequency.toString)

  }

  private[domain] val endOfTaxYearUpdateHodReads: Reads[EndOfTaxYearUpdate] = new Reads[EndOfTaxYearUpdate] {

    override def reads(json: JsValue): JsResult[EndOfTaxYearUpdate] = {

      val optionalAdjustmentAmountMap =
        (json \ "optionalAdjustmentAmount").asOpt[Map[String, BigDecimal]].getOrElse(Map())

      val rcvdDate = (json \ "rcvdDate").as[LocalDate]

      val adjusts = Seq(
        optionalAdjustmentAmountMap.get("TotalTaxDelta").map(Adjustment(TaxAdjustment, _)),
        optionalAdjustmentAmountMap.get("TaxablePayDelta").map(Adjustment(IncomeAdjustment, _)),
        niFigure(json).flatMap(_.get("EmpeeContribnsDelta")).map(Adjustment(NationalInsuranceAdjustment, _))
      ).flatten.filter(_.amount != 0)

      JsSuccess(EndOfTaxYearUpdate(rcvdDate, adjusts))
    }
  }

  val annualAccountHodReads: Reads[Seq[AnnualAccount]] = new Reads[Seq[AnnualAccount]] {

    override def reads(json: JsValue): JsResult[Seq[AnnualAccount]] = {

      val employments: Seq[JsValue] = (json \ "individual" \ "employments" \ "employment").validate[JsArray] match {
        case JsSuccess(arr, _) => arr.value.toSeq
        case _                 => Nil
      }

      JsSuccess(employments.map { emp =>
        val sequenceNumber = (emp \ "sequenceNumber").as[Int]
        val payments =
          (emp \ "payments" \ "inYear").validate[JsArray] match {
            case JsSuccess(arr, _) =>
              arr.value
                .map { payment =>
                  payment.as[Payment](paymentHodReads)
                }
                .toList
                .sorted
            case _ => Nil
          }

        val eyus =
          (emp \ "payments" \ "eyu").validate[JsArray] match {
            case JsSuccess(arr, _) =>
              arr.value
                .map { payment =>
                  payment.as[EndOfTaxYearUpdate](endOfTaxYearUpdateHodReads)
                }
                .toList
                .sorted
            case _ => Nil
          }

        val taxYear = (json \ "individual" \ "relatedTaxYear").as[TaxYear](taxYearHodReads)

        AnnualAccount(sequenceNumber, taxYear, Available, payments, eyus)
      })
    }
  }

  private[domain] val taxYearHodReads: Reads[TaxYear] = new Reads[TaxYear] {
    override def reads(json: JsValue): JsSuccess[TaxYear] =
      JsSuccess(TaxYear(json.as[String]))
  }

  private val numericWithLeadingZeros: Regex = """^([0]+)([1-9][0-9]*)""".r
  def numberChecked(stringVal: String): String =
    stringVal match {
      case numericWithLeadingZeros(_, numeric) => numeric
      case _                                   => stringVal
    }

}

object EmploymentHodFormatters extends EmploymentHodFormatters

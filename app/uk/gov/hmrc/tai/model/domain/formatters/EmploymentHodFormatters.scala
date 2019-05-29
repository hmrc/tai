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

package uk.gov.hmrc.tai.model.domain.formatters

import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import play.api.data.validation.ValidationError
import play.api.libs.json.JodaReads._
import play.api.libs.json.JodaWrites._
import play.api.libs.json._
import uk.gov.hmrc.tai.model.api.EmploymentCollection
import uk.gov.hmrc.tai.model.domain.{EndOfTaxYearUpdate, _}
import uk.gov.hmrc.tai.model.tai.{JsonExtra, TaxYear}

import scala.util.matching.Regex

trait EmploymentHodFormatters {

  implicit val stringMapFormat = JsonExtra.mapFormat[String,BigDecimal]("type", "amount")

  implicit val employmentHodReads: Reads[Employment] = new Reads[Employment] {

    val formatEmploymentLocalDate: Format[LocalDate] = Format(
      new Reads[LocalDate] {
        val dateRegex = """^(\d\d)/(\d\d)/(\d\d\d\d)$""".r

        override def reads(json: JsValue): JsResult[LocalDate] = json match {
          case JsString(dateRegex(d, m, y)) =>
            JsSuccess(new LocalDate(y.toInt, m.toInt, d.toInt))
          case invalid => JsError(JsonValidationError(
            s"Invalid date format [dd/MM/yyyy]: $invalid"))
        }
      },
      new Writes[LocalDate] {
        val dateFormat = DateTimeFormat.forPattern("dd/MM/yyyy")

        override def writes(date: LocalDate): JsValue =
          JsString(dateFormat.print(date))
      }
    )

    override def reads(json: JsValue): JsResult[Employment] = {
      val name = (json \ "employerName").as[String]
      val payrollNumber = (json \ "worksNumber").asOpt[String]
      val startDate = (json \ "startDate").as[LocalDate](formatEmploymentLocalDate)
      val endDate = (json \ "endDate").asOpt[LocalDate](formatEmploymentLocalDate)
      val taxDistrictNumber = numberChecked( (json \ "taxDistrictNumber").as[String] )
      val payeNumber = (json \ "payeNumber").as[String]
      val sequenceNumber = (json \ "sequenceNumber").as[Int]
      val cessationPay = (json \ "cessationPayThisEmployment").asOpt[BigDecimal]
      val payrolledBenefit = (json \ "payrolledTaxYear").asOpt[Boolean].getOrElse(false) || (json \ "payrolledTaxYear1").asOpt[Boolean].getOrElse(false)
      val receivingOccupationalPension = (json \ "receivingOccupationalPension").as[Boolean]
      JsSuccess(Employment(name, payrollNumber, startDate, endDate, Nil, taxDistrictNumber, payeNumber, sequenceNumber, cessationPay, payrolledBenefit, receivingOccupationalPension))
    }
  }

  implicit val employmentCollectionHodReads = new Reads[EmploymentCollection] {
    override def reads(json: JsValue): JsResult[EmploymentCollection] = {
      val a = json.as[Seq[Employment]]
      JsSuccess(EmploymentCollection(a))
    }
  }

  val paymentHodReads: Reads[Payment] = new Reads[Payment] {
    override def reads(json: JsValue): JsResult[Payment] = {

      val mandatoryMoneyAmount = (json \ "mandatoryMonetaryAmount").
        as[Map[String, BigDecimal]]

      val niFigure = ((json \ "niLettersAndValues").asOpt[JsArray].map(x => x \\ "niFigure")).
        flatMap(_.headOption).map(_.asOpt[Map[String, BigDecimal]].getOrElse(Map()))

      val payment = Payment(
        date = (json \ "pmtDate").as[LocalDate],
        amountYearToDate = mandatoryMoneyAmount("TaxablePayYTD"),
        taxAmountYearToDate = mandatoryMoneyAmount("TotalTaxYTD"),
        nationalInsuranceAmountYearToDate = niFigure.flatMap(_.get("EmpeeContribnsYTD")).getOrElse(0),
        amount = mandatoryMoneyAmount("TaxablePay"),
        taxAmount = mandatoryMoneyAmount("TaxDeductedOrRefunded"),
        nationalInsuranceAmount = niFigure.flatMap(_.get("EmpeeContribnsInPd")).getOrElse(0),
        payFrequency = (json \ "payFreq").as[PaymentFrequency](paymentFrequencyFormat)
      )

      JsSuccess(payment)
    }
  }

  val paymentFrequencyFormat = new Format[PaymentFrequency] {
    override def reads(json: JsValue): JsResult[PaymentFrequency] = json.as[String] match {
      case Weekly.value => JsSuccess(Weekly)
      case FortNightly.value => JsSuccess(FortNightly)
      case FourWeekly.value => JsSuccess(FourWeekly)
      case Monthly.value => JsSuccess(Monthly)
      case Quarterly.value => JsSuccess(Quarterly)
      case BiAnnually.value => JsSuccess(BiAnnually)
      case Annually.value => JsSuccess(Annually)
      case OneOff.value => JsSuccess(OneOff)
      case Irregular.value => JsSuccess(Irregular)
      case _ => throw new IllegalArgumentException("Invalid payment frequency")
    }

    override def writes(paymentFrequency: PaymentFrequency): JsValue = JsString(paymentFrequency.toString)

  }

  val endOfTaxYearUpdateHodReads = new Reads[EndOfTaxYearUpdate] {

    override def reads(json: JsValue): JsResult[EndOfTaxYearUpdate] = {

      val optionalAdjustmentAmountMap =
        ((json \ "optionalAdjustmentAmount").asOpt[Map[String, BigDecimal]]).getOrElse(Map())

      val niFigure = ((json \ "niLettersAndValues").asOpt[JsArray].map(x => x \\ "niFigure")).
        flatMap(_.headOption).map(_.asOpt[Map[String, BigDecimal]].getOrElse(Map()))

      val rcvdDate = (json \ "rcvdDate").as[LocalDate]

      val adjusts = Seq(
        optionalAdjustmentAmountMap.get("TotalTaxDelta").map( Adjustment(TaxAdjustment, _) ),
        optionalAdjustmentAmountMap.get("TaxablePayDelta").map( Adjustment(IncomeAdjustment, _) ),
        niFigure.flatMap(_.get("EmpeeContribnsDelta")).map( Adjustment(NationalInsuranceAdjustment, _) )
      ).flatten.filter(_.amount!=0)

      JsSuccess(EndOfTaxYearUpdate(rcvdDate, adjusts))
    }
  }

  val annualAccountHodReads = new Reads[Seq[AnnualAccount]] {

    override def reads(json: JsValue): JsResult[Seq[AnnualAccount]] = {

      val employments: Seq[JsValue] = (json \ "individual" \ "employments" \ "employment").validate[JsArray] match {
        case JsSuccess(arr, path) => arr.value
        case _ => Nil
      }

      JsSuccess(employments.map {emp =>
        val officeNo = numberChecked( (emp \ "empRefs" \ "officeNo").as[String] )
        val payeRef = (emp \ "empRefs" \ "payeRef").as[String]
        val currentPayId = (emp \ "currentPayId").asOpt[String].map(pr => if(pr == "") "" else "-" + pr).getOrElse("")
        val key = officeNo + "-" + payeRef + currentPayId

        val payments =
          (emp \ "payments" \ "inYear").validate[JsArray] match {
            case JsSuccess(arr, path) => arr.value.map {  payment =>
              payment.as[Payment](paymentHodReads)
            }.toList.sorted
            case _ => Nil
          }

        val eyus =
          (emp \ "payments" \ "eyu").validate[JsArray] match {
            case JsSuccess(arr, path) => arr.value.map {  payment =>
              payment.as[EndOfTaxYearUpdate](endOfTaxYearUpdateHodReads)
            }.toList.sorted
            case _ => Nil
          }

        val taxYear = (json \ "individual" \ "relatedTaxYear").as[TaxYear](taxYearHodReads)

        AnnualAccount(key, taxYear, Available, payments, eyus)
      })
    }
  }

  val taxYearHodReads: Reads[TaxYear] = new Reads[TaxYear] {
    override def reads(json: JsValue): JsSuccess[TaxYear] =
      JsSuccess(TaxYear(json.as[String]))
  }

  val numericWithLeadingZeros: Regex = """^([0]+)([1-9][0-9]*)""".r
  def numberChecked(stringVal: String): String = {
    stringVal match {
      case numericWithLeadingZeros(zeros, numeric) => numeric
      case _ => stringVal
    }
  }

}

object EmploymentHodFormatters extends EmploymentHodFormatters




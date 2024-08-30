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

package uk.gov.hmrc.tai.model.api

import play.api.libs.json.Reads.localDateReads
import play.api.libs.json._
import uk.gov.hmrc.tai.model.domain.Employment
import uk.gov.hmrc.tai.model.domain.Employment.numberChecked
import uk.gov.hmrc.tai.model.domain.income.TaxCodeIncomeStatus

import java.time.LocalDate
import scala.util.{Failure, Success, Try}

case class EmploymentCollection(employments: Seq[Employment], etag: Option[Int])

object EmploymentCollection {
  implicit val employmentCollectionFormat: Format[EmploymentCollection] = Json.format[EmploymentCollection]
  val employmentHodNpsReads: Reads[Employment] = new Reads[Employment] {
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

  val employmentHodReads: Reads[Employment] = new Reads[Employment] {
    private val dateReadsFromHod: Reads[LocalDate] = localDateReads("yyyy-MM-dd")

    override def reads(json: JsValue): JsResult[Employment] = {

      val employerReference = numberChecked((json \ "employerReference").as[String])

      val name = (json \ "payeSchemeOperatorName").as[String]
      val payrollNumber = (json \ "worksNumber").asOpt[String]
      val startDate = (json \ "startDate").as[LocalDate](dateReadsFromHod)
      val endDate = (json \ "endDate").asOpt[LocalDate](dateReadsFromHod)
      val taxDistrictNumber = employerReference.take(3)
      val payeNumber = employerReference.substring(3)
      val sequenceNumber = (json \ "employmentSequenceNumber").as[Int]
      val cessationPay = (json \ "cessationPayForEmployment").asOpt[BigDecimal]
      val payrolledBenefit =
        (json \ "payrolledCurrentYear").asOpt[Boolean].getOrElse(false) || (json \ "payrolledNextYear")
          .asOpt[Boolean]
          .getOrElse(false)
      val receivingOccupationalPension = (json \ "activeOccupationalPension").as[Boolean]
      val status = TaxCodeIncomeStatus.employmentStatus(json)
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

  private val readsNps: Reads[EmploymentCollection] = { (json: JsValue) =>
    implicit val rds: Reads[Employment] = employmentHodNpsReads
    JsSuccess(EmploymentCollection(json.as[Seq[Employment]], None))
  }

  private val readsHip: Reads[EmploymentCollection] = { (json: JsValue) =>
    val readsSeqEmployment =
      (__ \ "individualsEmploymentDetails").read[Seq[Employment]](Reads.seq(employmentHodReads))
    readsSeqEmployment.reads(json).map { seqEmployment =>
      EmploymentCollection(seqEmployment, None)
    }
  }

  def employmentCollectionHodReads(hipToggle: Boolean): Reads[EmploymentCollection] =
    if (hipToggle) {
      Reads { jsValue =>
        readsHip.reads(jsValue) match {
          case value @ JsSuccess(_, _) => value
          case hipErrors @ JsError(_) =>
            Try(readsNps.reads(jsValue)) match {
              case Success(value @ JsSuccess(_, _)) => value
              /*
                If the NPS reads fails then return the HIP errors instead of the NPS errors.
               */
              case Success(JsError(_))           => hipErrors
              case Failure(_: JsResultException) => hipErrors
              case Failure(exception)            => throw exception
            }
        }
      }
    } else {
      readsNps
    }
}

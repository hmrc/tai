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
import uk.gov.hmrc.tai.util.JsonHelper._

import java.time.LocalDate

case class EmploymentCollection(employments: Seq[Employment], etag: Option[Int])

object EmploymentCollection {
  implicit val employmentCollectionFormat: Format[EmploymentCollection] = Json.format[EmploymentCollection]
  def employmentHodNpsReads: Reads[Employment] = new Reads[Employment] {
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

  def employmentHodReads: Reads[Employment] = new Reads[Employment] {
    private def splitEmpRef(empRef: String): JsResult[(String, String)] =
      empRef.split("/").toSeq match {
        case Seq(taxDistrictNumber, payeNumber) => JsSuccess((taxDistrictNumber, payeNumber))
        case _                                  => JsError(s"Invalid employerReference $empRef")
      }

    private val dateReadsFromHod: Reads[LocalDate] = localDateReads("yyyy-MM-dd")

    override def reads(json: JsValue): JsResult[Employment] = {

      val employerReference = numberChecked((json \ "employerReference").as[String])

      val name = (json \ "payeSchemeOperatorName").as[String]
      val payrollNumber = (json \ "worksNumber").asOpt[String]
      val startDate = (json \ "startDate").as[LocalDate](dateReadsFromHod)
      val endDate = (json \ "endDate").asOpt[LocalDate](dateReadsFromHod)

      splitEmpRef(employerReference) match {
        case JsSuccess(Tuple2(taxDistrictNumber, payeNumber), _) =>
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
        case errors @ JsError(_) => errors
      }

    }
  }

  private def readsNps: Reads[EmploymentCollection] = { (json: JsValue) =>
    implicit val rds: Reads[Employment] = employmentHodNpsReads
    JsSuccess(EmploymentCollection(json.as[Seq[Employment]], None))
  }

  private def readsHip: Reads[EmploymentCollection] = { (json: JsValue) =>
    val readsSeqEmployment = json match {
      case _: JsArray => Reads[Seq[Employment]](_ => JsError("Unexpected array - Squid payload?"))
      case _ =>
        (__ \ "individualsEmploymentDetails").readNullable[Seq[Employment]](Reads.seq(employmentHodReads)).map {
          case None    => Nil
          case Some(e) => e
        }
    }

    readsSeqEmployment.reads(json).map { seqEmployment =>
      EmploymentCollection(seqEmployment, None)
    }
  }

  /*
    If toggle switched on then we still need to cater for payloads in NPS format which may still be
    present in the cache.
   */
  lazy val employmentCollectionHodReadsHIP: Reads[EmploymentCollection] = readsHip orElseTry readsNps

  /*
    If toggle switched on then for some reason has to be switched off then we have to cater
    for payloads in HIP format even though toggle is off.
   */
  lazy val employmentCollectionHodReadsNPS: Reads[EmploymentCollection] = readsNps orElseTry readsHip
}

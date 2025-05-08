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

import play.api.libs.json.*
import play.api.libs.json.Reads.localDateReads
import uk.gov.hmrc.tai.model.domain.*
import uk.gov.hmrc.tai.model.domain.Employment.numberChecked
import uk.gov.hmrc.tai.model.domain.income.TaxCodeIncomeStatus
import uk.gov.hmrc.tai.util.JsonHelper.*

import java.time.LocalDate

//todo this is a duplicate of uk.gov.hmrc.tai.model.domain.Employments
case class EmploymentCollection(employments: Seq[Employment], etag: Option[Int]) {
  def employmentById(id: Int): Option[Employment] = employments.find(_.sequenceNumber == id)
}

object EmploymentCollection {
  implicit val writes: Writes[EmploymentCollection] = Json.writes[EmploymentCollection]

  private def determineComponentType(
    activeOccupationalPension: Boolean,
    jobSeekersAllowance: Option[Boolean],
    otherIncomeSource: Option[Boolean]
  ): TaxCodeIncomeComponentType =
    (activeOccupationalPension, jobSeekersAllowance.getOrElse(false), otherIncomeSource.getOrElse(false)) match {
      case (true, _, _) => PensionIncome
      case (_, true, _) => JobSeekerAllowanceIncome
      case (_, _, true) => OtherIncome
      case _            => EmploymentIncome
    }

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
      val jobSeekersAllowance = (json \ "receivingJobseekersAllowance").asOpt[Boolean]
      val otherIncomeSource = (json \ "otherIncomeSourceIndicator").asOpt[Boolean]
      val incomeType = determineComponentType(receivingOccupationalPension, jobSeekersAllowance, otherIncomeSource)
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
          receivingOccupationalPension,
          incomeType
        )
      )
    }
  }

  // scalastyle:off method.length
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
          val jobSeekersAllowance = (json \ "jobSeekersAllowance").asOpt[Boolean]
          val otherIncomeSource = (json \ "otherIncomeSource").asOpt[Boolean]
          val incomeType =
            determineComponentType(receivingOccupationalPension, jobSeekersAllowance, otherIncomeSource)
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
              receivingOccupationalPension,
              incomeType
            )
          )
        case errors @ JsError(_) => errors
      }
    }
  }

  lazy val employmentCollectionHodReadsHIP: Reads[EmploymentCollection] = { (json: JsValue) =>
    val readsSeqEmployment =
      (__ \ "individualsEmploymentDetails").readNullable[Seq[Employment]](Reads.seq(employmentHodReads)).map {
        case None    => Nil
        case Some(e) => e
      }

    readsSeqEmployment.reads(json).map { seqEmployment =>
      EmploymentCollection(seqEmployment, None)
    }
  }

}

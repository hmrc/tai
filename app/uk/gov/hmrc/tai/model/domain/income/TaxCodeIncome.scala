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

package uk.gov.hmrc.tai.model.domain.income

import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.tai.model.domain.IabdSummary.iabdSummaryReads
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.util.{TaiConstants, TaxCodeHistoryConstants}

import java.time.LocalDate

sealed trait BasisOperation
case object Week1Month1BasisOperation extends BasisOperation
case object OtherBasisOperation extends BasisOperation

object BasisOperation extends BasisOperation with TaxCodeHistoryConstants {
  def apply(constant: String): BasisOperation =
    if (constant == Week1Month1)
      Week1Month1BasisOperation
    else
      OtherBasisOperation

  implicit val formatBasisOperationType: Format[BasisOperation] = new Format[BasisOperation] {
    override def reads(json: JsValue): JsSuccess[BasisOperation] = JsSuccess(BasisOperation)

    override def writes(basisOperation: BasisOperation): JsValue = JsString(basisOperation.toString)
  }
}

sealed trait TaxCodeIncomeStatus
case object Live extends TaxCodeIncomeStatus
case object NotLive extends TaxCodeIncomeStatus
case object PotentiallyCeased extends TaxCodeIncomeStatus
case object Ceased extends TaxCodeIncomeStatus

object TaxCodeIncomeStatus {

  private val logger: Logger = Logger(getClass.getName)

  def apply(value: String): TaxCodeIncomeStatus = value match {
    case "Live"              => Live
    case "NotLive"           => NotLive
    case "PotentiallyCeased" => PotentiallyCeased
    case "Ceased"            => Ceased
    case _                   => throw new IllegalArgumentException("Invalid TaxCodeIncomeStatus")
  }

  implicit val employmentStatus: Format[TaxCodeIncomeStatus] = new Format[TaxCodeIncomeStatus] {
    override def reads(json: JsValue): JsResult[TaxCodeIncomeStatus] = json.as[String] match {
      case "Live"              => JsSuccess(Live)
      case "PotentiallyCeased" => JsSuccess(PotentiallyCeased)
      case "Ceased"            => JsSuccess(Ceased)
      case default =>
        logger.warn(s"Invalid Employment Status Reads -> $default")
        throw new RuntimeException("Invalid employment status reads")
    }

    override def writes(taxCodeIncomeStatus: TaxCodeIncomeStatus): JsValue = JsString(taxCodeIncomeStatus.toString)
  }

  def employmentStatusFromNps(json: JsValue): TaxCodeIncomeStatus = {
    val employmentStatus = (json \ "employmentStatus").asOpt[Int]

    employmentStatus match {
      case Some(1) => Live
      case Some(2) => PotentiallyCeased
      case Some(3) => Ceased
      case default =>
        logger.warn(s"Invalid Employment Status -> $default")
        throw new RuntimeException("Invalid employment status")
    }
  }

  def employmentStatus(json: JsValue): TaxCodeIncomeStatus = {
    val employmentStatus = (json \ "employmentStatus").asOpt[String]

    employmentStatus match {
      case Some("Live")               => Live
      case Some("Potentially Ceased") => PotentiallyCeased
      case Some("Permanently Ceased") => Ceased
      case Some("Ceased")             => Ceased
      case default =>
        logger.warn(s"Invalid Employment Status -> $default")
        throw new RuntimeException("Invalid employment status")
    }
  }
}

sealed trait IabdUpdateSource
case object ManualTelephone extends IabdUpdateSource
case object Letter extends IabdUpdateSource
case object Email extends IabdUpdateSource
case object AgentContact extends IabdUpdateSource
case object OtherForm extends IabdUpdateSource
case object Internet extends IabdUpdateSource
case object InformationLetter extends IabdUpdateSource

object IabdUpdateSource extends IabdUpdateSource {
  private val iabdUpdateSourceMap = Map(
    15 -> ManualTelephone,
    16 -> Letter,
    17 -> Email,
    18 -> AgentContact,
    24 -> OtherForm,
    39 -> Internet,
    40 -> InformationLetter
  )
  implicit val formatIabdUpdateSource: Format[IabdUpdateSource] = new Format[IabdUpdateSource] {
    override def reads(json: JsValue): JsSuccess[IabdUpdateSource] = throw new RuntimeException("Not Implemented")

    override def writes(iabdUpdateSource: IabdUpdateSource): JsValue = JsString(iabdUpdateSource.toString)
  }
  def fromCode(code: Int): Option[IabdUpdateSource] = iabdUpdateSourceMap.get(code)
}

case class TaxCodeIncome(
  componentType: TaxComponentType,
  employmentId: Option[Int],
  amount: BigDecimal,
  description: String,
  taxCode: String,
  name: String,
  basisOperation: BasisOperation,
  status: TaxCodeIncomeStatus,
  inYearAdjustmentIntoCY: BigDecimal,
  totalInYearAdjustment: BigDecimal,
  inYearAdjustmentIntoCYPlusOne: BigDecimal,
  iabdUpdateSource: Option[IabdUpdateSource] = None,
  updateNotificationDate: Option[LocalDate] = None,
  updateActionDate: Option[LocalDate] = None
) {

  lazy val taxCodeWithEmergencySuffix: String = basisOperation match {
    case Week1Month1BasisOperation => taxCode + TaiConstants.EmergencyTaxCode
    case _                         => taxCode
  }
}

object TaxCodeIncome {

  implicit val writes: Writes[TaxCodeIncome] = (o: TaxCodeIncome) =>
    JsObject(
      List(
        "componentType"                 -> Json.toJson(o.componentType),
        "employmentId"                  -> Json.toJson(o.employmentId),
        "amount"                        -> Json.toJson(o.amount),
        "description"                   -> Json.toJson(o.description),
        "taxCode"                       -> Json.toJson(o.taxCodeWithEmergencySuffix),
        "name"                          -> Json.toJson(o.name),
        "basisOperation"                -> Json.toJson(o.basisOperation),
        "status"                        -> Json.toJson(o.status),
        "inYearAdjustmentIntoCY"        -> Json.toJson(o.inYearAdjustmentIntoCY),
        "totalInYearAdjustment"         -> Json.toJson(o.totalInYearAdjustment),
        "inYearAdjustmentIntoCYPlusOne" -> Json.toJson(o.inYearAdjustmentIntoCYPlusOne),
        "iabdUpdateSource"              -> Json.toJson(o.iabdUpdateSource),
        "updateNotificationDate"        -> Json.toJson(o.updateNotificationDate),
        "updateActionDate"              -> Json.toJson(o.updateActionDate)
      ).filter {
        case (_, JsNull) => false
        case _           => true
      }
    )

  private val logger: Logger = Logger(getClass.getName)

  private val basisOperationReads = new Reads[BasisOperation] {
    override def reads(json: JsValue): JsResult[BasisOperation] = {
      val result = json.asOpt[Int] match {
        case Some(1) => Week1Month1BasisOperation
        case _       => OtherBasisOperation
      }
      JsSuccess(result)
    }
  }

  private val taxCodeIncomeSourceReads: Reads[TaxCodeIncome] = (json: JsValue) => {
    val incomeSourceType = taxCodeIncomeType(json)
    val employmentId = (json \ "employmentId").asOpt[Int]
    val amount = totalTaxableIncome(json, employmentId).getOrElse(BigDecimal(0))
    val description = incomeSourceType.toString
    val taxCode = (json \ "taxCode").asOpt[String].getOrElse("")
    val name = (json \ "name").asOpt[String].getOrElse("")
    val basisOperation =
      (json \ "basisOperation").asOpt[BasisOperation](basisOperationReads).getOrElse(OtherBasisOperation)
    val status = TaxCodeIncomeStatus.employmentStatusFromNps(json)
    val iyaCy = (json \ "inYearAdjustmentIntoCY").asOpt[BigDecimal].getOrElse(BigDecimal(0))
    val totalIya = (json \ "totalInYearAdjustment").asOpt[BigDecimal].getOrElse(BigDecimal(0))
    val iyaCyPlusOne = (json \ "inYearAdjustmentIntoCYPlusOne").asOpt[BigDecimal].getOrElse(BigDecimal(0))
    JsSuccess(
      TaxCodeIncome(
        incomeSourceType,
        employmentId,
        amount,
        description,
        taxCode,
        name,
        basisOperation,
        status,
        iyaCy,
        totalIya,
        iyaCyPlusOne
      )
    )
  }

  // TODO: DDCNL-9376 Duplicate reads
  val taxCodeIncomeSourcesReads: Reads[Seq[TaxCodeIncome]] = (json: JsValue) => {
    val taxCodeIncomes = (json \ "incomeSources")
      .asOpt[Seq[TaxCodeIncome]](Reads.seq(taxCodeIncomeSourceReads))
      .getOrElse(Seq.empty[TaxCodeIncome])
    JsSuccess(taxCodeIncomes)
  }

  private def taxCodeIncomeType(json: JsValue): TaxComponentType = {
    def indicator(indicatorType: String): Boolean = (json \ indicatorType).asOpt[Boolean].getOrElse(false)

    if (indicator("pensionIndicator")) {
      PensionIncome
    } else if (indicator("jsaIndicator")) {
      JobSeekerAllowanceIncome
    } else if (indicator("otherIncomeSourceIndicator")) {
      OtherIncome
    } else {
      EmploymentIncome
    }
  }

  private def totalTaxableIncome(json: JsValue, employmentId: Option[Int]): Option[BigDecimal] = {
    val iabdSummaries: Option[Seq[IabdSummary]] =
      (json \ "payAndTax" \ "totalIncome" \ "iabdSummaries").asOpt[Seq[IabdSummary]](Reads.seq(iabdSummaryReads))
    val iabdSummary = iabdSummaries.flatMap {
      _.find(iabd =>
        newEstimatedPayTypeFilter(iabd) &&
          employmentFilter(iabd, employmentId)
      )
    }
    iabdSummary.map(_.amount) match {
      case Some(amount) => Some(amount)
      case _ =>
        logger.warn("TotalTaxableIncome is 0")
        None
    }
  }

  def newEstimatedPayTypeFilter(iabdSummary: IabdSummary): Boolean = {
    val NewEstimatedPay = 27
    iabdSummary.componentType == NewEstimatedPay
  }

  def employmentFilter(iabdSummary: IabdSummary, employmentId: Option[Int]): Boolean = {
    val compare = for {
      jsEmploymentId <- iabdSummary.employmentId
      empId          <- employmentId
    } yield jsEmploymentId == empId

    compare.getOrElse(false)
  }

  implicit val reads: Reads[TaxCodeIncome] = taxCodeIncomeSourceReads
}

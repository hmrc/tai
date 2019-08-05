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

package uk.gov.hmrc.tai.model.domain.income

import org.joda.time.LocalDate
import uk.gov.hmrc.tai.model.domain._
import play.api.libs.json._
import uk.gov.hmrc.tai.util.{TaiConstants, TaxCodeHistoryConstants}

sealed trait BasisOperation
case object Week1Month1BasisOperation extends BasisOperation
case object OtherBasisOperation extends BasisOperation

object BasisOperation extends BasisOperation with TaxCodeHistoryConstants {
  def apply(constant: String): BasisOperation = {
    if (constant == Week1Month1)
      Week1Month1BasisOperation
    else
      OtherBasisOperation
  }

  implicit val formatBasisOperationType = new Format[BasisOperation] {
    override def reads(json: JsValue): JsSuccess[BasisOperation] = ???

    override def writes(basisOperation: BasisOperation) = JsString(basisOperation.toString)
  }
}

sealed trait TaxCodeIncomeStatus
case object Live extends TaxCodeIncomeStatus
case object NotLive extends TaxCodeIncomeStatus
case object PotentiallyCeased extends TaxCodeIncomeStatus
case object Ceased extends TaxCodeIncomeStatus


object TaxCodeIncomeStatus {

  def apply(value: String): TaxCodeIncomeStatus = value match {
    case "Live" => Live
    case "NotLive" => NotLive
    case "PotentiallyCeased" => PotentiallyCeased
    case "Ceased" => Ceased
    case _ => throw new IllegalArgumentException("Invalid TaxCodeIncomeStatus")
  }

  implicit val formatTaxCodeIncomeSourceStatusType: Format[TaxCodeIncomeStatus] = new Format[TaxCodeIncomeStatus] {
    override def reads(json: JsValue): JsSuccess[TaxCodeIncomeStatus] = ???

    override def writes(taxCodeIncomeStatus: TaxCodeIncomeStatus) = JsString(taxCodeIncomeStatus.toString)
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
  implicit val formatIabdUpdateSource = new Format[IabdUpdateSource] {
    override def reads(json: JsValue): JsSuccess[IabdUpdateSource] = throw new RuntimeException("Not Implemented")

    override def writes(iabdUpdateSource: IabdUpdateSource) = JsString(iabdUpdateSource.toString)
  }
  def fromCode(code: Int): Option[IabdUpdateSource] = iabdUpdateSourceMap.get(code)
}

case class TaxCodeIncome(componentType:TaxComponentType,
                         employmentId:Option[Int],
                         amount:BigDecimal,
                         description:String,
                         taxCode:String,
                         name: String,
                         basisOperation: BasisOperation,
                         status: TaxCodeIncomeStatus,
                         inYearAdjustmentIntoCY:BigDecimal,
                         totalInYearAdjustment:BigDecimal,
                         inYearAdjustmentIntoCYPlusOne:BigDecimal,
                         iabdUpdateSource: Option[IabdUpdateSource] = None,
                         updateNotificationDate: Option[LocalDate] = None,
                         updateActionDate: Option[LocalDate] = None) {

  lazy val taxCodeWithEmergencySuffix: String = basisOperation match {
    case Week1Month1BasisOperation => taxCode + TaiConstants.EmergencyTaxCode
    case _ => taxCode
  }
}

object TaxCodeIncome {
  implicit val format: Format[TaxCodeIncome] = Json.format[TaxCodeIncome]
}
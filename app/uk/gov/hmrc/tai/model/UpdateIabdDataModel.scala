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

package uk.gov.hmrc.tai.model

import org.joda.time.LocalDate
import play.api.libs.json._
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

case class TransactionId(oid: String)

object TransactionId {
  implicit val formats = Json.format[TransactionId]
}

case class IabdEditDataRequest(version: Int, newAmount: Int)

object IabdEditDataRequest {
  implicit val formats = Json.format[IabdEditDataRequest]
}

case class IabdUpdateResponse(transaction: TransactionId, version: Int, iabdType: Int, newAmount: Int)

object IabdUpdateResponse {
  implicit val format = Json.format[IabdUpdateResponse]
}

case class EmploymentAmount(
  name: String,
  description: String,
  employmentId: Int,
  newAmount: Int,
  oldAmount: Int,
  worksNumber: Option[String] = None,
  jobTitle: Option[String] = None,
  startDate: Option[LocalDate] = None,
  endDate: Option[LocalDate] = None,
  isLive: Boolean = true,
  isOccupationalPension: Boolean = false)

object EmploymentAmount {
  implicit val formats = Json.format[EmploymentAmount]
}

case class IabdUpdateEmploymentsRequest(version: Int, newAmounts: List[EmploymentAmount])

object IabdUpdateEmploymentsRequest {
  implicit val formats = Json.format[IabdUpdateEmploymentsRequest]
}

case class IabdUpdateEmploymentsResponse(
  transaction: TransactionId,
  version: Int,
  iabdType: Int,
  newAmounts: List[EmploymentAmount])

object IabdUpdateEmploymentsResponse {
  implicit val format = Json.format[IabdUpdateEmploymentsResponse]
}

case class PayAnnualisationRequest(amountYearToDate: BigDecimal, employmentStartDate: LocalDate, paymentDate: LocalDate)

object PayAnnualisationRequest {
  implicit val format = Json.format[PayAnnualisationRequest]
}

case class PayAnnualisationResponse(annualisedAmount: BigDecimal)

object PayAnnualisationResponse {
  implicit val format = Json.format[PayAnnualisationResponse]
}

case class IabdUpdateExpensesRequest(version: Int, grossAmount: Int)

object IabdUpdateExpensesRequest {
  implicit val format = Json.format[IabdUpdateExpensesRequest]
}

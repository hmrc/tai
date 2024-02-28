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

package uk.gov.hmrc.tai.model.nps

import play.api.libs.json._

case class NpsEmployment(
  sequenceNumber: Int,
  startDate: NpsDate,
  endDate: Option[NpsDate],
  taxDistrictNumber: String,
  payeNumber: String,
  employerName: Option[String],
  employmentType: Int,
  employmentStatus: Option[Int] = None,
  worksNumber: Option[String] = None,
  jobTitle: Option[String] = None,
  startingTaxCode: Option[String] = None,
  receivingJobseekersAllowance: Option[Boolean] = None,
  receivingOccupationalPension: Option[Boolean] = None,
  otherIncomeSourceIndicator: Option[Boolean] = None,
  payrolledTaxYear: Option[Boolean] = None,
  payrolledTaxYear1: Option[Boolean] = None,
  cessationPayThisEmployment: Option[BigDecimal] = None)

object NpsEmployment {
  implicit val formats = Json.format[NpsEmployment]
}

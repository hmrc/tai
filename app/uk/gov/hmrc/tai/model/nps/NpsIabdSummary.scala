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

import uk.gov.hmrc.tai.model.IabdSummary
import play.api.libs.json.{Json, OFormat}

case class NpsIabdSummary(
  amount: Option[BigDecimal] = None,
  `type`: Option[Int],
  npsDescription: Option[String] = None,
  employmentId: Option[Int] = None,
  estimatedPaySource: Option[Int] = None
) {

  override def equals(o: Any): Boolean = o match {
    case that: NpsIabdSummary => that.`type`.equals(this.`type`)
    case _                    => false
  }
  override def hashCode: Int = `type`.getOrElse(0)

  def toIadbSummary(
    incomeSources: Option[List[NpsIncomeSource]],
    npsEmployments: Option[List[NpsEmployment]] = None): IabdSummary = {

    val namefromIncomeSource = for {
      sources         <- incomeSources
      npsIncomeSource <- sources.find(_.employmentId == employmentId)
      name            <- npsIncomeSource.name
    } yield name

    def nameFromEmployments =
      for {
        employments <- npsEmployments
        employer    <- employments.find(_.sequenceNumber == employmentId.getOrElse(-1))
        name        <- employer.employerName
      } yield name

    val employerName = if (namefromIncomeSource.isDefined) namefromIncomeSource else nameFromEmployments

    IabdSummary(
      `type`.getOrElse(0),
      npsDescription.getOrElse(""),
      amount.getOrElse(BigDecimal(0)),
      employmentId,
      estimatedPaySource,
      employerName)
  }
}

object NpsIabdSummary {
  implicit val formats: OFormat[NpsIabdSummary] = Json.format[NpsIabdSummary]
}

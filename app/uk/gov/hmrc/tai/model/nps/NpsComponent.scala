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

package uk.gov.hmrc.tai.model.nps
import play.api.libs.json.Json
import uk.gov.hmrc.tai.model._

case class NpsComponent(
  amount: Option[BigDecimal] = None,
  `type`: Option[Int] = None,
  iabdSummaries: Option[List[NpsIabdSummary]] = None,
  npsDescription: Option[String] = None,
  sourceAmount: Option[BigDecimal] = None) {
  def toTaxComponent(
    incomeSources: Option[List[NpsIncomeSource]],
    npsEmployments: Option[List[NpsEmployment]] = None): TaxComponent =
    new TaxComponent(
      amount.getOrElse(BigDecimal(0)),
      `type`.getOrElse(0),
      npsDescription.getOrElse(""),
      iabdSummaries match {
        case None => Nil
        case Some(x) =>
          x.sortBy(_.amount)
            .reverse
            .map(_.toIadbSummary(incomeSources = incomeSources, npsEmployments = npsEmployments))
      }
    )
}
object NpsComponent {
  implicit val formats = Json.format[NpsComponent]
}

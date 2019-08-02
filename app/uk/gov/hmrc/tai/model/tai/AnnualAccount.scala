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

package uk.gov.hmrc.tai.model.tai

import uk.gov.hmrc.tai.model.nps2.TaxAccount
import uk.gov.hmrc.tai.model.rti.{RtiData, RtiStatus}

case class AnnualAccount(
  year: TaxYear,
  nps: Option[TaxAccount] = None,
  rti: Option[RtiData] = None,
  rtiStatus: Option[RtiStatus] = None
) {
  def employments: Seq[Employment] = {
    val rtiEmps = rti.map { _.employments }.getOrElse(Nil)
    val npsIncomes = nps.map(_.incomes.filter(_.employmentRecord.isDefined)).getOrElse(Seq())
    rtiEmps.flatMap { r =>
      npsIncomes.filter { n =>
        n.payeRef == r.payeRef &&
        n.taxDistrict.contains(r.officeRefNo.toInt)
      } match {
        case Seq(one) => Some(Employment(one, Some(r)))
        case Nil      => None
        case m =>
          m.find(
              _.worksNumber == r.currentPayId &&
                r.currentPayId.isDefined)
            .map(Employment(_, Some(r)))
      }
    }
  }
}

/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.nps2

import org.joda.time.LocalDate
import uk.gov.hmrc.tai.model.enums.BasisOperation
import uk.gov.hmrc.tai.model.enums.BasisOperation.BasisOperation

/**
  * The root object for the NPS tax account, typically this is read in
  * from the HoD. It is considered less authoritative than the RTI Tax
  * Account data but is always available.
  */
case class TaxAccount(
  /**
    * A unique ID to identify a tax account. You cannot really use this for
    * anything practical as we don't know if NPS will populate it or not.
    */
  id: Option[Long],
  /**
    * Date the tax position was confirmed (will be the tax code
    * date). May be absent, though we don't know what this means.
    */
  date: Option[LocalDate],
  /**
    * Total tax across all income sources
    */
  tax: BigDecimal,
  taxObjects: Map[TaxObject.Type.Value, TaxDetail] = Map.empty,
  incomes: Seq[Income] = Nil,
  freeIabds: List[Iabd] = Nil
) {
  def withEmployments(emps: Seq[NpsEmployment]): TaxAccount = {

    val newIncomes = incomes.map { i =>
      emps.find(x => i.employmentId.contains(x.sequenceNumber)) match {
        case Some(e) => i.copy(worksNumber = e.worksNumber, employmentRecord = Some(e))
        case None    => i
      }
    }
    val updateIncomes = newIncomes.map(i => i.copy(taxCode = getOperatedTaxCode(i.taxCode, i.basisOperation)))
    this.copy(incomes = updateIncomes)
  }

  def getOperatedTaxCode(taxCode: String, basisOperation: Option[BasisOperation]): String =
    basisOperation.fold(taxCode) { b =>
      if (b == BasisOperation.Week1Month1) {
        if (taxCode != "NT") taxCode + " X" else taxCode
      } else {
        taxCode
      }
    }
}

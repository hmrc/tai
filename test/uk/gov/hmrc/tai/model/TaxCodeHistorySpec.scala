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

package uk.gov.hmrc.tai.model

import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.factory.TaxCodeRecordFactory
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.TaxCodeHistoryConstants

import scala.util.Random

class TaxCodeHistorySpec extends PlaySpec with BeforeAndAfterEach with TaxCodeHistoryConstants {
  private val nino: Nino = new Generator(new Random).nextNino

  "TaxCodeHistory applicableTaxCodeRecords" must {
    "filter out operated tax code records" in {
      val nonOperatedRecord = TaxCodeRecordFactory.createNonOperatedEmployment()
      val primaryEmployment = TaxCodeRecordFactory.createPrimaryEmployment()
      val taxCodeHistory = TaxCodeHistory(nino.nino, Seq(nonOperatedRecord, primaryEmployment))

      taxCodeHistory.applicableTaxCodeRecords mustBe Seq(primaryEmployment)
    }

    "filter out tax code that are not in current year" in {
      val primaryEmployment = TaxCodeRecordFactory.createPrimaryEmployment()
      val nextYearTaxCodeRecord = TaxCodeRecordFactory.createPrimaryEmployment(taxYear = TaxYear().next)
      val taxCodeHistory = TaxCodeHistory(nino.nino, Seq(primaryEmployment, nextYearTaxCodeRecord))

      taxCodeHistory.applicableTaxCodeRecords mustBe Seq(primaryEmployment)
    }
  }

}

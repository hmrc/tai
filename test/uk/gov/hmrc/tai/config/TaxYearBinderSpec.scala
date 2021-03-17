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

package uk.gov.hmrc.tai.config

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.binders.TaxYearBinder
import uk.gov.hmrc.tai.model.tai.TaxYear

class TaxYearBinderSpec extends PlaySpec {

  "TaxYearBinder" must {
    "correctly parse the taxyear type" in {
      TaxYearBinder.bind("year", "2017") mustBe Right(TaxYear(2017))
      TaxYearBinder.bind("year", "17") mustBe Right(TaxYear(2017))

    }
    "return an error when the date type is not valid" in {
      TaxYearBinder.bind("year", "AAA") mustBe Left("Cannot parse parameter 'year' with value 'AAA' as 'TaxYear'")
    }
  }
}

/*
 * Copyright 2022 HM Revenue & Customs
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

import java.time.LocalDate
import org.scalatestplus.play.PlaySpec

class IncomeSpec extends PlaySpec {

  "Income Status" must {
    "return the Live status" when {
      "the given code is live" in {
        Income.Status(Some(Income.Live.code), None) mustBe Income.Live
      }
      "the given code is anything other than the live code or the potentially ceased code and there is no end date" in {
        Income.Status(Some(2300), None) mustBe Income.Live
        Income.Status(Some(ceasedStatus.code), None) mustBe Income.Live
      }
    }
    "return the ceased status" when {
      "the given code is ceased and there is an end date" in {
        Income.Status(Some(ceasedStatus.code), Some(ceasedStatus.on)) mustBe ceasedStatus
      }
      "the given code is open but there is an end date" in {
        Income.Status(Some(Income.Live.code), Some(ceasedStatus.on)) mustBe ceasedStatus
      }
      "the given code is potentially ceased and there is an end date" in {
        Income.Status(Some(Income.PotentiallyCeased.code), Some(ceasedStatus.on)) mustBe ceasedStatus
      }
    }
    "return the potentially ceased status" when {
      "the given code is potentially ceased " in {
        Income.Status(Some(Income.PotentiallyCeased.code), None) mustBe Income.PotentiallyCeased
      }
    }
  }

  val ceasedStatus = Income.Ceased(new LocalDate())
}

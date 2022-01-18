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

package uk.gov.hmrc.tai.model

import org.scalatestplus.play.PlaySpec

class TaxCodeMismatchSpec extends PlaySpec {
  "apply" must {
    "sort inputs" in {
      val input = Seq("222", "111")
      val sortedList = Seq("111", "222")

      val expected = TaxCodeMismatch(false, sortedList, sortedList)
      TaxCodeMismatch(input, input) mustBe expected
    }

    "not be a mismatch if lists are different" in {
      val input1 = Seq("123", "456")
      val input2 = Seq("123", "789")

      val expected = TaxCodeMismatch(true, input1.sorted, input2.sorted)
      TaxCodeMismatch(input1, input2) mustBe expected
    }
  }

}

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

package uk.gov.hmrc.tai.model.rti

import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec

class RtiEyuSpec extends PlaySpec {

  "Rti eyu" should {
    "sort a list of Rti Eyu objects" when {
      "given in random order" in {
        val rtiEyu1: RtiEyu = RtiEyu(None, None, None, new LocalDate(2015, 1, 1))
        val rtiEyu2: RtiEyu = RtiEyu(None, None, None, new LocalDate(2015, 2, 1))
        val rtiEyu3: RtiEyu = RtiEyu(None, None, None, new LocalDate(2015, 3, 1))

        val rtiEyuList: List[RtiEyu] = List(rtiEyu2, rtiEyu3, rtiEyu1)
        val sortedList = rtiEyuList.sorted
        sortedList.head.rcvdDate mustBe (new LocalDate(2015, 1, 1))
        sortedList.last.rcvdDate mustBe (new LocalDate(2015, 3, 1))
      }
    }
  }
}

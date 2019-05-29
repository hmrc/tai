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

package uk.gov.hmrc.tai.util

import org.joda.time.LocalDate
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec

class DateTimeHelperSpec extends UnitSpec with MockitoSugar with ScalaFutures {

  "convertToLocalDate " should {

    "convert to local date with the provided format (dd/MM/yyyy) " in {

      val localDate = DateTimeHelper.convertToLocalDate("dd/MM/yyyy", "01/01/2016")
      val dt = new LocalDate(2016, 1, 1)
      localDate shouldBe dt
    }

    "convert to local date with the provided format (dd-MM-yyyy) " in {

      val localDate = DateTimeHelper.convertToLocalDate("dd-MM-yyyy", "01-01-2016")
      val dt = new LocalDate(2016, 1, 1)
      localDate shouldBe dt
    }
  }
}



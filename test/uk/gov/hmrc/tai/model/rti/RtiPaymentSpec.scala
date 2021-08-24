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

package uk.gov.hmrc.tai.model.rti

import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec

class RtiPaymentSpec extends PlaySpec {

  "RtiPayment compare method" must {
    "implement comparison behaviour" in {
      rtiPayment1.compare(rtiPayment2) mustBe -1
      rtiPayment1.compare(rtiPayment2.copy(paidOn = LocalDate.now().minusDays(7))) mustBe 0
      rtiPayment1.compare(rtiPayment2.copy(paidOn = LocalDate.now().minusDays(9))) mustBe 1
    }
  }

  "RtiPayment isIrregular method" when {
    "pay frequency is irregular" must {
      "return true" in {
        val result = rtiPayment1.isIrregular
        result mustBe true
      }
    }

    "pay frequency is not irregular" must {
      "return false" in {
        val result = rtiPayment2.isIrregular
        result mustBe false
      }
    }
  }

  val rtiPayment1 = RtiPayment(
    payFrequency = PayFrequency.Irregular,
    paidOn = LocalDate.now().minusDays(7),
    submittedOn = LocalDate.now(),
    taxablePay = 2000,
    taxablePayYTD = 12000,
    taxed = 200,
    taxedYTD = 800
  )

  val rtiPayment2 = RtiPayment(
    payFrequency = PayFrequency.Monthly,
    paidOn = LocalDate.now().minusDays(5),
    submittedOn = LocalDate.now(),
    taxablePay = 2000,
    taxablePayYTD = 12000,
    taxed = 200,
    taxedYTD = 800
  )
}

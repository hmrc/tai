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

import org.joda.time.LocalDate
import play.api.libs.json.Json
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.tai.model.enums.PayFreq

class PayDetailsSpec extends UnitSpec {

  "PayDetails Json conversion " should {
    "successfully convert json paymentFrequency into PaymentDetails when a 'weekly' payment frequency is supplied." in {
      val payDetailsJson = Json.parse("""
                                        | {
                                        |   "paymentFrequency": "weekly"
                                        | }
        """.stripMargin)

      val payDetails = payDetailsJson.as[PayDetails]
      payDetails.paymentFrequency shouldBe PayFreq.weekly
    }

    "successfully convert json paymentFrequency into PaymentDetails when a 'fortnightly' payment frequency is supplied." in {
      val payDetailsJson = Json.parse("""
                                        | {
                                        |   "paymentFrequency": "fortnightly"
                                        | }
        """.stripMargin)

      val payDetails = payDetailsJson.as[PayDetails]
      payDetails.paymentFrequency shouldBe PayFreq.fortnightly
    }

    "successfully convert json paymentFrequency into PaymentDetails when a 'monthly' payment frequency is supplied." in {
      val payDetailsJson = Json.parse("""
                                        | {
                                        |   "paymentFrequency": "monthly"
                                        | }
        """.stripMargin)

      val payDetails = payDetailsJson.as[PayDetails]
      payDetails.paymentFrequency shouldBe PayFreq.monthly
    }

    "successfully convert json paymentFrequency into PaymentDetails when an 'other' payment frequency is supplied." in {
      val payDetailsJson = Json.parse("""
                                        | {
                                        |   "paymentFrequency": "other"
                                        | }
        """.stripMargin)

      val payDetails = payDetailsJson.as[PayDetails]
      payDetails.paymentFrequency shouldBe PayFreq.other
    }

    "successfully convert a full json representation of PaymentDetails into a PaymentDetails object." in {
      val payDetailsJson = Json.parse("""
                                        | {
                                        |   "paymentFrequency": "monthly",
                                        |   "pay": 5.12,
                                        |   "taxablePay": 3.01,
                                        |   "days": 23,
                                        |   "bonus": 1.23,
                                        |   "startDate": "2017-01-01"
                                        | }
        """.stripMargin)

      val payDetails = payDetailsJson.as[PayDetails]

      payDetails.paymentFrequency shouldBe PayFreq.monthly
      payDetails.pay shouldBe Some(5.12)
      payDetails.taxablePay shouldBe Some(3.01)
      payDetails.days shouldBe Some(23)
      payDetails.bonus shouldBe Some(1.23)
      payDetails.startDate shouldBe Some(LocalDate.parse("2017-01-01"))
    }

    "fail to convert json paymentFrequency into PaymentDetails when an unknown payment frequency is supplied." in {
      val payDetailsJson = Json.parse("""
                                        | {
                                        |   "paymentFrequency": "shouldfail"
                                        | }
        """.stripMargin)

      intercept[NoSuchElementException] {
        payDetailsJson.as[PayDetails]
      }
    }

    "successfully convert PaymentDetails to json and verify by successfully converting back into PaymentDetails." in {
      val pd = PayDetails(paymentFrequency = PayFreq.fortnightly)
      val payDetailsJson = Json.toJson(pd)
      val resultPayDetails = payDetailsJson.as[PayDetails]
      resultPayDetails.paymentFrequency shouldBe PayFreq.fortnightly
    }
  }

}

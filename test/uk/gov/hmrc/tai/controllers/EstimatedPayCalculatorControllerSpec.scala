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

package uk.gov.hmrc.tai.controllers

import java.time.LocalDate
import play.api.libs.json.{Json, Writes}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.tai.calculators.EstimatedPayCalculator
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.model.enums.PayFreq
import uk.gov.hmrc.tai.model.PayDetails
import uk.gov.hmrc.tai.util.BaseSpec

class EstimatedPayCalculatorControllerSpec extends BaseSpec {

  "Estimated pay calculator controller" must {
    "return an OK response with CalculatedPay json" when {
      "given a valid request" in {
        val sut = createSUT()
        val response = sut.calculateFullYearEstimatedPay().apply(createRequest(payDetails))

        status(response) mustBe OK
        contentAsJson(response) mustBe Json.toJson(testCalculation)
      }
    }
    "return a bad request response" when {
      "the given request has an invalid json body" in {

        val sut = createSUT()
        val response = sut.calculateFullYearEstimatedPay().apply(createRequest("a simple String"))

        status(response) mustBe BAD_REQUEST
      }
    }
  }

  private def createSUT(authentication: AuthenticationPredicate = loggedInAuthenticationPredicate) =
    new EstimatedPayCalculatorController(authentication, cc)

  val date = LocalDate.of(2017, 4, 14)

  private val payDetails = PayDetails(
    PayFreq.monthly,
    Some(1000),
    Some(500),
    Some(4),
    Some(10000),
    Some(date)
  )

  private val testCalculation = EstimatedPayCalculator.calculate(payDetails)

  private def createRequest[T](payload: T)(implicit w: Writes[T]) = FakeRequest(
    "POST",
    "/",
    FakeHeaders(Seq("Content-type" -> "application/json")),
    Json.toJson(payload)
  )
}

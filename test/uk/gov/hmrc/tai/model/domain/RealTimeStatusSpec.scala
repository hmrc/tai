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

package uk.gov.hmrc.tai.model.domain

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsString, Json}

class RealTimeStatusSpec extends PlaySpec {

  "RealTimeFormat" must {
    "create a valid object" when {
      "given a valid json value" in {

        JsString("Available").as[RealTimeStatus] mustBe Available
        JsString("TemporarilyUnavailable").as[RealTimeStatus] mustBe TemporarilyUnavailable
        JsString("Unavailable").as[RealTimeStatus] mustBe Unavailable
      }
    }
    "throw an exception" when {
      "given an invalid json value" in {

        val exception = the[IllegalArgumentException] thrownBy JsString("Wrong").as[RealTimeStatus]
        exception.getMessage mustBe "Invalid real time status value"
      }
    }
    "create a valid json value" when {
      "given a RealTimeStats object" in {

        Json.toJson(Available)(RealTimeStatus.realTimeStatusFormat.writes) mustBe JsString("Available")
        Json.toJson(TemporarilyUnavailable)(RealTimeStatus.realTimeStatusFormat.writes) mustBe JsString(
          "TemporarilyUnavailable")
        Json.toJson(Unavailable)(RealTimeStatus.realTimeStatusFormat.writes) mustBe JsString("Unavailable")
      }
    }
  }
}

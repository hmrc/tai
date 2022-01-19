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
import play.api.libs.json.{JsResultException, Json}

class ETagSpec extends PlaySpec {

  "Etag" must {
    "successfully bind when passed valid JSON" in {
      val validJson = Json.parse("""
                                   |{
                                   |   "etag":"123"
                                   |}
        """.stripMargin)

      val etag = validJson.as[ETag]
      etag mustBe ETag("123")
    }

    "fail to bind when passed invalid JSON" in {
      val invalidJson = Json.parse("""{}""")

      val parseError = intercept[JsResultException] {
        invalidJson.as[ETag]
      }

      parseError mustBe an[JsResultException]
    }
  }

}

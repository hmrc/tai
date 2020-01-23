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

package uk.gov.hmrc.tai.model.fileupload.formatters

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsArray, JsError, Json}
import uk.gov.hmrc.tai.model.fileupload.{EnvelopeFile, EnvelopeSummary}

class FileUploadFormattersSpec extends PlaySpec {

  "File Upload Formatter" should {

    "read the envelope summary" when {
      "Json is valid and files are empty" in {
        val json = Json.obj("id" -> "123", "status" -> "OPEN", "files" -> "[]")

        val result = json.as[EnvelopeSummary](FileUploadFormatters.envelopeSummaryReads)

        result mustBe EnvelopeSummary("123", "OPEN", Nil)
      }

      "Json is valid and files tag is not present" in {
        val json = Json.obj("id" -> "123", "status" -> "OPEN")

        val result = json.as[EnvelopeSummary](FileUploadFormatters.envelopeSummaryReads)

        result mustBe EnvelopeSummary("123", "OPEN", Nil)
      }

      "Json is valid and files are available" in {
        val file1 = Json.obj("id" -> "abc", "status" -> "AVAILABLE")
        val file2 = Json.obj("id" -> "pqr", "status" -> "AVAILABLE")
        val json = Json.obj("id"  -> "123", "status" -> "OPEN", "files" -> JsArray(Seq(file1, file2)))

        val result = json.as[EnvelopeSummary](FileUploadFormatters.envelopeSummaryReads)

        result mustBe EnvelopeSummary(
          "123",
          "OPEN",
          Seq(EnvelopeFile("abc", "AVAILABLE"), EnvelopeFile("pqr", "AVAILABLE")))
      }
    }

    "return none" when {
      "Json is invalid" in {
        val json = Json.obj("" -> "")

        val result = json.asOpt[EnvelopeSummary](FileUploadFormatters.envelopeSummaryReads)

        result mustBe None
      }
    }

  }
}

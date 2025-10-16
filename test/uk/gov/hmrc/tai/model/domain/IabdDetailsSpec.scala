/*
 * Copyright 2025 HM Revenue & Customs
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
import play.api.libs.json.*

class IabdDetailsSpec extends PlaySpec {

  "IabdDetails" must {

    "deserialize JSON correctly when unknown source type" in {
      val sampleJson = Json.obj(
        "iabdDetails" -> Json.arr(
          Json.obj(
            "nationalInsuranceNumber"  -> "AB123456C",
            "employmentSequenceNumber" -> 12345,
            "source"                   -> "unknown source type",
            "type"                     -> "Non-Coded Income (019)"
          )
        )
      )
      val expectedModel: IabdDetails = IabdDetails(
        employmentSequenceNumber = Some(12345),
        source = None,
        `type` = Some(19),
        receiptDate = None,
        captureDate = None
      )
      sampleJson.validate[Seq[IabdDetails]](IabdDetails.reads) mustBe JsSuccess(
        Seq(expectedModel),
        JsPath \ "iabdDetails"
      )
    }

    "deserialize JSON correctly when hicbc source type" in {
      val sampleJson = Json.obj(
        "iabdDetails" -> Json.arr(
          Json.obj(
            "nationalInsuranceNumber"  -> "AB123456C",
            "employmentSequenceNumber" -> 12345,
            "source"                   -> "HICBC PAYE",
            "type"                     -> "Non-Coded Income (019)"
          )
        )
      )
      val expectedModel: IabdDetails = IabdDetails(
        employmentSequenceNumber = Some(12345),
        source = Some(53),
        `type` = Some(19),
        receiptDate = None,
        captureDate = None
      )
      sampleJson.validate[Seq[IabdDetails]](IabdDetails.reads) mustBe JsSuccess(
        Seq(expectedModel),
        JsPath \ "iabdDetails"
      )
    }

    "deserialize JSON correctly when source type is missing" in {
      val sampleJson = Json.obj(
        "iabdDetails" -> Json.arr(
          Json.obj(
            "nationalInsuranceNumber"  -> "AB123456C",
            "employmentSequenceNumber" -> 12345,
            "type"                     -> "Non-Coded Income (019)"
          )
        )
      )
      val expectedModel: IabdDetails = IabdDetails(
        employmentSequenceNumber = Some(12345),
        source = None,
        `type` = Some(19),
        receiptDate = None,
        captureDate = None
      )
      sampleJson.validate[Seq[IabdDetails]](IabdDetails.reads) mustBe JsSuccess(
        Seq(expectedModel),
        JsPath \ "iabdDetails"
      )
    }
  }
}

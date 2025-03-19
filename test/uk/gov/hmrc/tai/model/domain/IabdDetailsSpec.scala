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

import java.time.LocalDate

class IabdDetailsSpec extends PlaySpec {

  val sampleJsonToggleOff: JsValue = Json.parse(
    """
      |[
      |  {
      |    "nino": "AB123456C",
      |    "employmentSequenceNumber": 12345,
      |    "source": 1,
      |    "type": 100,
      |    "receiptDate": "01/02/2024",
      |    "captureDate": "10/02/2024",
      |    "grossAmount": 5000.75
      |  }
      |]
      |""".stripMargin
  )

  val expectedModelToggleOff: IabdDetails = IabdDetails(
    Some("AB123456C"),
    Some(12345),
    Some(1),
    Some(100),
    Some(LocalDate.of(2024, 2, 1)),
    Some(LocalDate.of(2024, 2, 10)),
    Some(BigDecimal(5000.75))
  )

  "IabdDetailsToggleOff" must {

    "deserialize JSON correctly" in {
      sampleJsonToggleOff.validate[Seq[IabdDetails]](IabdDetailsToggleOff.reads) mustBe JsSuccess(
        Seq(expectedModelToggleOff)
      )
    }

    "serialize model to JSON correctly" in {
      val expectedJson = Json.parse(
        """
          |{
          |  "nino": "AB123456C",
          |  "employmentSequenceNumber": 12345,
          |  "source": 1,
          |  "type": 100,
          |  "receiptDate": "01/02/2024",
          |  "captureDate": "10/02/2024",
          |  "grossAmount": 5000.75
          |}
          |""".stripMargin
      )

      Json.toJson(expectedModelToggleOff)(IabdDetailsToggleOff.format) mustBe expectedJson
    }
  }

  "IabdDetailsToggleOn" must {

    "deserialize JSON correctly when unknown source type" in {
      val sampleJsonToggleOn = Json.obj(
        "iabdDetails" -> Json.arr(
          Json.obj(
            "nationalInsuranceNumber"  -> "AB123456C",
            "employmentSequenceNumber" -> 12345,
            "source"                   -> "unknown source type",
            "type"                     -> "Non-Coded Income (019)"
          )
        )
      )
      val expectedModelToggleOn: IabdDetails = IabdDetails(
        nino = Some("AB123456C"),
        employmentSequenceNumber = Some(12345),
        source = None,
        `type` = Some(19),
        receiptDate = None,
        captureDate = None
      )
      sampleJsonToggleOn.validate[Seq[IabdDetails]](IabdDetailsToggleOn.reads) mustBe JsSuccess(
        Seq(expectedModelToggleOn),
        JsPath \ "iabdDetails"
      )
    }

    "deserialize JSON correctly when source type is missing" in {
      val sampleJsonToggleOn = Json.obj(
        "iabdDetails" -> Json.arr(
          Json.obj(
            "nationalInsuranceNumber"  -> "AB123456C",
            "employmentSequenceNumber" -> 12345,
            "type"                     -> "Non-Coded Income (019)"
          )
        )
      )
      val expectedModelToggleOn: IabdDetails = IabdDetails(
        nino = Some("AB123456C"),
        employmentSequenceNumber = Some(12345),
        source = None,
        `type` = Some(19),
        receiptDate = None,
        captureDate = None
      )
      sampleJsonToggleOn.validate[Seq[IabdDetails]](IabdDetailsToggleOn.reads) mustBe JsSuccess(
        Seq(expectedModelToggleOn),
        JsPath \ "iabdDetails"
      )
    }
  }
}

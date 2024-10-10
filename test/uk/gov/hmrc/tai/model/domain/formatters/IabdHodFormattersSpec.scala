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

package uk.gov.hmrc.tai.model.domain.formatters

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNull, JsValue, Json}
import uk.gov.hmrc.domain.Generator

import scala.util.Random

class IabdHodFormattersSpec extends PlaySpec with IabdHodFormatters {

  "Iabd Formatter" must {

    "return empty json" when {
      "iabds are empty" in {
        val json = Json.arr()

        json.as[JsValue](iabdEstimatedPayReads) mustBe json
      }

      "type 27 Iabd is not present" in {
        val json = Json.arr(
          Json.obj(
            "nino"            -> nino.withoutSuffix,
            "taxYear"         -> 2017,
            "type"            -> 10,
            "source"          -> 15,
            "grossAmount"     -> JsNull,
            "receiptDate"     -> JsNull,
            "captureDate"     -> "10/04/2017",
            "typeDescription" -> "Total gift aid Payments",
            "netAmount"       -> 100
          )
        )

        json.as[JsValue](iabdEstimatedPayReads) mustBe Json.arr()
      }
    }

    "return filtered json with iabd type as 27" when {
      "only user have one employment" in {
        val json = Json.arr(
          Json.obj(
            "nino"            -> nino.withoutSuffix,
            "taxYear"         -> 2017,
            "type"            -> 10,
            "source"          -> 15,
            "grossAmount"     -> JsNull,
            "receiptDate"     -> JsNull,
            "captureDate"     -> "10/04/2017",
            "typeDescription" -> "Total gift aid Payments",
            "netAmount"       -> 100
          ),
          Json.obj(
            "nino"                     -> nino.withoutSuffix,
            "employmentSequenceNumber" -> 1,
            "taxYear"                  -> 2017,
            "type"                     -> 27,
            "source"                   -> 15,
            "grossAmount"              -> JsNull,
            "receiptDate"              -> JsNull,
            "captureDate"              -> "10/04/2017",
            "typeDescription"          -> "Total gift aid Payments",
            "netAmount"                -> 100
          )
        )

        val expectedJson = Json.arr(
          Json.obj(
            "nino"                     -> nino.withoutSuffix,
            "employmentSequenceNumber" -> 1,
            "source"                   -> 15,
            "type"                     -> 27,
            "captureDate"              -> "10/04/2017"
          )
        )

        json.as[JsValue](iabdEstimatedPayReads) mustBe expectedJson
      }

      "user have multiple employment" in {
        val json = Json.arr(
          Json.obj(
            "nino"            -> nino.withoutSuffix,
            "taxYear"         -> 2017,
            "type"            -> 10,
            "source"          -> 15,
            "grossAmount"     -> JsNull,
            "receiptDate"     -> JsNull,
            "captureDate"     -> "10/04/2017",
            "typeDescription" -> "Total gift aid Payments",
            "netAmount"       -> 100
          ),
          Json.obj(
            "nino"                     -> nino.withoutSuffix,
            "employmentSequenceNumber" -> 1,
            "taxYear"                  -> 2017,
            "type"                     -> 27,
            "source"                   -> 15,
            "grossAmount"              -> JsNull,
            "receiptDate"              -> "10/04/2017",
            "captureDate"              -> "10/04/2017",
            "typeDescription"          -> "Total gift aid Payments",
            "netAmount"                -> 100
          ),
          Json.obj(
            "nino"                     -> nino.withoutSuffix,
            "employmentSequenceNumber" -> 2,
            "taxYear"                  -> 2017,
            "type"                     -> 27,
            "source"                   -> 12,
            "grossAmount"              -> JsNull,
            "receiptDate"              -> JsNull,
            "captureDate"              -> JsNull,
            "typeDescription"          -> "Total gift aid Payments",
            "netAmount"                -> 100
          )
        )

        val expectedJson = Json.arr(
          Json.obj(
            "nino"                     -> nino.withoutSuffix,
            "employmentSequenceNumber" -> 1,
            "source"                   -> 15,
            "type"                     -> 27,
            "receiptDate"              -> "10/04/2017",
            "captureDate"              -> "10/04/2017"
          ),
          Json.obj(
            "nino"                     -> nino.withoutSuffix,
            "employmentSequenceNumber" -> 2,
            "source"                   -> 12,
            "type"                     -> 27
          )
        )

        json.as[JsValue](iabdEstimatedPayReads) mustBe expectedJson
      }
    }
  }

  val nino = new Generator(new Random).nextNino

}

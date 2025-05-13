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

package uk.gov.hmrc.tai.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{Format, JsObject, Json}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.tai.model.UpstreamErrorResponseFormat.format

import java.time.LocalDateTime

class UpstreamErrorResponseFormatSpec extends PlaySpec {
  private val upstreamErrorResponse = UpstreamErrorResponse("error", 500, 500)

  private case class Dummy(first: String, second: String)
  private object Dummy {
    implicit val formats: Format[Dummy] = Json.format[Dummy]
  }

  private val dummy = Dummy("one", "two")

  private val leftUpstreamErrorResponse: Either[UpstreamErrorResponse, Dummy] = Left(
    upstreamErrorResponse
  )
  private val rightUpstreamErrorResponse: Either[UpstreamErrorResponse, Dummy] = Right(dummy)

  private val jsonRight = Json.obj(
    UpstreamErrorResponseFormat.rightNode -> Json.obj(
      "first"  -> "one",
      "second" -> "two"
    )
  )

  private val jsonLeft = Json.obj(
    UpstreamErrorResponseFormat.leftNode -> Json.obj(
      "statusCode" -> 500,
      "reportAs"   -> 500,
      "message"    -> "error"
    )
  )

  "format" must {
    "serialise a right" in {
      val fmt: Format[Either[UpstreamErrorResponse, Dummy]] = format[Dummy](implicitly)
      val result = Json.toJson(rightUpstreamErrorResponse)(fmt)
      result mustBe jsonRight
    }

    "serialise a left" in {
      val fmt: Format[Either[UpstreamErrorResponse, Dummy]] = format[Dummy](implicitly)
      val result = Json.toJson(leftUpstreamErrorResponse)(fmt)
      result mustBe jsonLeft
    }

    "de-serialise a right" in {
      val fmt: Format[Either[UpstreamErrorResponse, Dummy]] = format[Dummy](implicitly)
      val result: Either[UpstreamErrorResponse, Dummy] =
        jsonRight.as[Either[UpstreamErrorResponse, Dummy]](fmt)
      result mustBe rightUpstreamErrorResponse
    }
    "de-serialise json assuming it is a right" in {
      val fmt: Format[Either[UpstreamErrorResponse, Dummy]] = format[Dummy](implicitly)
      val jsonInsideSuccessNode = (jsonRight \ UpstreamErrorResponseFormat.rightNode).as[JsObject]
      val result: Either[UpstreamErrorResponse, Dummy] =
        jsonInsideSuccessNode.as[Either[UpstreamErrorResponse, Dummy]](fmt)
      result mustBe rightUpstreamErrorResponse
    }

    "de-serialise a left" in {
      val fmt: Format[Either[UpstreamErrorResponse, Dummy]] = format[Dummy](implicitly)
      val result: Either[UpstreamErrorResponse, Dummy] =
        jsonLeft.as[Either[UpstreamErrorResponse, Dummy]](fmt)
      result mustBe leftUpstreamErrorResponse
    }
  }
}

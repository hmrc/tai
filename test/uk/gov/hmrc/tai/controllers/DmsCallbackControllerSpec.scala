/*
 * Copyright 2026 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.{never, reset, verify, when}
import play.api.libs.json.{JsValue, Json}
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.tai.model.dms.DmsCallback
import uk.gov.hmrc.tai.service.DmsSubmissionService
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class DmsCallbackControllerSpec extends BaseSpec {

  private val mockDmsSubmissionService = mock[DmsSubmissionService]

  private val sut = new DmsCallbackController(mockDmsSubmissionService, cc)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockDmsSubmissionService)
  }

  private def request(body: JsValue) =
    FakeRequest(
      method = "POST",
      uri = "",
      headers = FakeHeaders(Seq("Content-type" -> "application/json")),
      body = body
    )

  private val processedJson = Json.obj(
    "id"            -> "dms-1",
    "status"        -> "Processed",
    "objectSummary" -> Json.obj(
      "location"      -> "dms-submission/tai/dms-1",
      "contentLength" -> 1337,
      "contentMd5"    -> "hash",
      "lastModified"  -> "2026-10-05T12:00:00"
    )
  )

  "callback" must {

    "pass the callback to the service and return 200" in {
      when(mockDmsSubmissionService.handleCallback(any())(any())).thenReturn(Future.unit)

      val result = sut.callback()(request(processedJson)).futureValue

      result.header.status mustBe 200
      verify(mockDmsSubmissionService).handleCallback(meq(DmsCallback("dms-1", "Processed")))(any())
    }

    "pass on the failure details of a failed submission" in {
      when(mockDmsSubmissionService.handleCallback(any())(any())).thenReturn(Future.unit)
      val json = Json.obj("id" -> "dms-1", "status" -> "Failed", "failureReason" -> "bad", "failureType" -> "Unknown")

      sut.callback()(request(json)).futureValue.header.status mustBe 200
      verify(mockDmsSubmissionService)
        .handleCallback(meq(DmsCallback("dms-1", "Failed", Some("bad"), Some("Unknown"))))(any())
    }

    "still return 200 when the service fails" in {
      when(mockDmsSubmissionService.handleCallback(any())(any()))
        .thenReturn(Future.failed(new RuntimeException("boom")))

      sut.callback()(request(processedJson)).futureValue.header.status mustBe 200
    }

    "return 400 for a body that is not a valid callback" in {
      val result = sut.callback()(request(Json.obj("unexpected" -> "value"))).futureValue

      result.header.status mustBe 400
      verify(mockDmsSubmissionService, never).handleCallback(any())(any())
    }
  }
}

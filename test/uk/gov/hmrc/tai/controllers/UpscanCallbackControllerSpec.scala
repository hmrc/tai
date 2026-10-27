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
import uk.gov.hmrc.tai.model.upscan.{UpscanCallback, UpscanFileStatus, UpscanUploadDetails}
import uk.gov.hmrc.tai.service.UpscanSubmissionService
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class UpscanCallbackControllerSpec extends BaseSpec {

  private val mockUpscanSubmissionService = mock[UpscanSubmissionService]

  private val sut = new UpscanCallbackController(mockUpscanSubmissionService, cc)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUpscanSubmissionService)
  }

  private def request(body: JsValue) =
    FakeRequest(
      method = "POST",
      uri = "",
      headers = FakeHeaders(Seq("Content-type" -> "application/json")),
      body = body
    )

  private val readyJson = Json.obj(
    "reference"     -> "11370e18-6e24-453e-b45a-76d3e32ea33d",
    "fileStatus"    -> "READY",
    "downloadUrl"   -> "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
    "uploadDetails" -> Json.obj(
      "uploadTimestamp" -> "2018-04-24T09:30:00Z",
      "checksum"        -> "396f101dd52e8b2ace0dcf5ed09b1d1f030e603c",
      "fileName"        -> "test.pdf",
      "fileMimeType"    -> "application/pdf",
      "size"            -> 987
    )
  )

  private val readyCallback = UpscanCallback(
    reference = "11370e18-6e24-453e-b45a-76d3e32ea33d",
    fileStatus = UpscanFileStatus.Ready,
    downloadUrl = Some("https://bucketName.s3.eu-west-2.amazonaws.com?1235676"),
    uploadDetails = Some(
      UpscanUploadDetails(
        fileName = "test.pdf",
        fileMimeType = "application/pdf",
        uploadTimestamp = "2018-04-24T09:30:00Z",
        checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e603c",
        size = 987L
      )
    )
  )

  "callback" must {

    "pass the parsed callback to the service and return 200" in {
      when(mockUpscanSubmissionService.handleCallback(any())(any())).thenReturn(Future.successful(()))

      val result = sut.callback()(request(readyJson)).futureValue

      result.header.status mustBe 200
      verify(mockUpscanSubmissionService).handleCallback(meq(readyCallback))(any())
    }

    "still return 200 when the service fails, so that Upscan does not keep retrying" in {
      when(mockUpscanSubmissionService.handleCallback(any())(any()))
        .thenReturn(Future.failed(new RuntimeException("boom")))

      val result = sut.callback()(request(readyJson)).futureValue

      result.header.status mustBe 200
    }

    "return 400 and not call the service when the body is not a valid Upscan callback" in {
      val result = sut.callback()(request(Json.obj("unexpected" -> "payload"))).futureValue

      result.header.status mustBe 400
      verify(mockUpscanSubmissionService, never).handleCallback(any())(any())
    }
  }
}

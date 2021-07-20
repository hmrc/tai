/*
 * Copyright 2021 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.tai.model.FileUploadCallback
import uk.gov.hmrc.tai.service.{FileUploadService, Open}
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class FileUploadControllerSpec extends BaseSpec {

  "fileUploadCallback" must {

    "always return success" in {
      val json = Json.obj(
        "envelopeId" -> "0b215ey97-11d4-4006-91db-c067e74fc653",
        "fileId"     -> "file-id-1",
        "status"     -> "ERROR",
        "reason"     -> "VirusDetected")

      val fakeRequest = FakeRequest(
        method = "POST",
        uri = "",
        headers = FakeHeaders(Seq("Content-type" -> "application/json")),
        body = json)

      val mockFileUploadService = mock[FileUploadService]
      when(mockFileUploadService.fileUploadCallback(meq(json.as[FileUploadCallback]))(any()))
        .thenReturn(Future.successful(Open))

      val sut = createSUT(mockFileUploadService)
      val result = sut.fileUploadCallback()(fakeRequest).futureValue

      result.header.status mustBe 200
    }
  }

  private def createSUT(fileUploadService: FileUploadService) =
    new FileUploadController(fileUploadService, cc)
}

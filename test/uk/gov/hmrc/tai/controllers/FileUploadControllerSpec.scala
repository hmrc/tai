/*
 * Copyright 2018 HM Revenue & Customs
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

import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.tai.model.FileUploadCallback
import uk.gov.hmrc.tai.service.{FileUploadService, Open}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class FileUploadControllerSpec extends PlaySpec with MockitoSugar {

  "fileUploadCallback" must {

    "always return success" in {
      val json = Json.obj(
        "envelopeId" -> "0b215ey97-11d4-4006-91db-c067e74fc653",
        "fileId" -> "file-id-1",
        "status" -> "ERROR",
        "reason" -> "VirusDetected")

      val fakeRequest = FakeRequest(method = "POST", uri = "",
        headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = json)

      val mockFileUploadService = mock[FileUploadService]
      when(mockFileUploadService.fileUploadCallback(Matchers.eq(json.as[FileUploadCallback]))(any()))
        .thenReturn(Future.successful(Open))

      val sut = createSUT(mockFileUploadService)
      val result = Await.result(sut.fileUploadCallback()(fakeRequest), 5.seconds)

      result.header.status mustBe 200
    }
  }

  private def createSUT(fileUploadService: FileUploadService) =
    new FileUploadController(fileUploadService)
}

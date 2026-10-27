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

package uk.gov.hmrc.tai.connectors

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import play.api.http.Status.*
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.tai.config.UpscanConfig
import uk.gov.hmrc.tai.model.domain.MimeContentType
import uk.gov.hmrc.tai.model.upscan.{UpscanInitiateResponse, UpscanUploadRequest}

class UpscanConnectorSpec extends ConnectorBaseSpec {

  lazy val sut: UpscanConnector = inject[UpscanConnector]
  lazy val config: UpscanConfig = inject[UpscanConfig]

  private val reference = "11370e18-6e24-453e-b45a-76d3e32ea33d"
  private val initiateUrl = "/upscan/v2/initiate"
  private val uploadPath = "/upscan-upload"

  private lazy val uploadRequest =
    UpscanUploadRequest(s"${server.baseUrl()}$uploadPath", Map("acl" -> "private", "key" -> reference))

  private lazy val initiateJson = Json.obj(
    "reference"     -> reference,
    "uploadRequest" -> Json.obj(
      "href"   -> uploadRequest.href,
      "fields" -> Json.obj("acl" -> "private", "key" -> reference)
    )
  )

  def verifyOutgoingHeaders(requestPattern: RequestPatternBuilder): Unit =
    server.verify(
      requestPattern
        .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
        .withHeader(HeaderNames.xRequestId, equalTo(requestId))
    )

  "initiate" must {

    "return the reference and upload request from Upscan" in {
      server.stubFor(
        post(urlEqualTo(initiateUrl)).willReturn(aResponse().withStatus(OK).withBody(initiateJson.toString))
      )

      sut.initiate.futureValue mustBe UpscanInitiateResponse(reference, uploadRequest)

      verifyOutgoingHeaders(postRequestedFor(urlEqualTo(initiateUrl)))
    }

    "send the configured callback url, file size limits and consuming service" in {
      server.stubFor(
        post(urlEqualTo(initiateUrl)).willReturn(aResponse().withStatus(OK).withBody(initiateJson.toString))
      )

      sut.initiate.futureValue

      server.verify(
        postRequestedFor(urlEqualTo(initiateUrl))
          .withRequestBody(matchingJsonPath("$.callbackUrl", equalTo(config.callbackUrl)))
          .withRequestBody(matchingJsonPath("$.minimumFileSize", equalTo(config.minFileSizeBytes.toString)))
          .withRequestBody(matchingJsonPath("$.maximumFileSize", equalTo(config.maxFileSizeBytes.toString)))
          .withRequestBody(matchingJsonPath("$.consumingService", equalTo(config.consumingService)))
      )
    }

    "throw a RuntimeException" when {
      List(BAD_REQUEST, FORBIDDEN, NOT_FOUND, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { httpStatus =>
        s"Upscan responds with $httpStatus" in {
          server.stubFor(post(urlEqualTo(initiateUrl)).willReturn(aResponse().withStatus(httpStatus)))

          val result = sut.initiate.failed.futureValue

          result mustBe a[RuntimeException]
          result.getMessage mustBe s"Upscan initiate failed with status: $httpStatus"
        }
      }

      "Upscan responds with a successful status other than 200" in {
        server.stubFor(post(urlEqualTo(initiateUrl)).willReturn(aResponse().withStatus(CREATED).withBody("{}")))

        val result = sut.initiate.failed.futureValue

        result mustBe a[RuntimeException]
        result.getMessage mustBe s"Upscan initiate failed with status: $CREATED"
      }
    }
  }

  "upload" must {

    "post the form fields and the file as multipart to the url Upscan returned" in {
      server.stubFor(post(urlEqualTo(uploadPath)).willReturn(aResponse().withStatus(NO_CONTENT)))

      val response =
        sut.upload(uploadRequest, "file-content".getBytes, "file.pdf", MimeContentType.ApplicationPdf).futureValue

      response.status mustBe NO_CONTENT

      server.verify(
        postRequestedFor(urlEqualTo(uploadPath))
          .withHeader("Content-Type", containing("multipart/form-data"))
          .withRequestBodyPart(aMultipart().withName("acl").withBody(equalTo("private")).build())
          .withRequestBodyPart(aMultipart().withName("key").withBody(equalTo(reference)).build())
          .withRequestBodyPart(aMultipart().withName("file").withBody(equalTo("file-content")).build())
      )
    }

    "accept the other success statuses S3 may return" in {
      List(OK, CREATED).foreach { httpStatus =>
        server.stubFor(post(urlEqualTo(uploadPath)).willReturn(aResponse().withStatus(httpStatus)))

        sut
          .upload(uploadRequest, Array.emptyByteArray, "file.pdf", MimeContentType.ApplicationPdf)
          .futureValue
          .status mustBe httpStatus
      }
    }

    "throw a RuntimeException" when {
      List(BAD_REQUEST, FORBIDDEN, INTERNAL_SERVER_ERROR).foreach { httpStatus =>
        s"the upload endpoint responds with $httpStatus" in {
          server.stubFor(post(urlEqualTo(uploadPath)).willReturn(aResponse().withStatus(httpStatus)))

          val result = sut
            .upload(uploadRequest, Array.emptyByteArray, "file.pdf", MimeContentType.ApplicationPdf)
            .failed
            .futureValue

          result mustBe a[RuntimeException]
          result.getMessage mustBe s"Upscan file upload failed with status: $httpStatus"
        }
      }
    }
  }

  "download" must {

    "return the bytes of the file at the download url" in {
      val bytes = "pdf content".getBytes
      server.stubFor(get(urlEqualTo("/upscan-download")).willReturn(aResponse().withStatus(OK).withBody(bytes)))

      sut.download(s"${server.baseUrl()}/upscan-download").futureValue mustBe bytes
    }

    "throw a RuntimeException" when {
      List(NOT_FOUND, FORBIDDEN, INTERNAL_SERVER_ERROR).foreach { httpStatus =>
        s"the download endpoint responds with $httpStatus" in {
          server.stubFor(get(urlEqualTo("/upscan-download")).willReturn(aResponse().withStatus(httpStatus)))

          val result = sut.download(s"${server.baseUrl()}/upscan-download").failed.futureValue

          result mustBe a[RuntimeException]
          result.getMessage mustBe s"Upscan file download failed with status: $httpStatus"
        }
      }
    }
  }
}

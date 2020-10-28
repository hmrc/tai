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

package uk.gov.hmrc.tai.connectors

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.codahale.metrics.Timer
import org.junit.After
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import play.api.http.Status.{BAD_REQUEST, CREATED, OK}
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.libs.ws.ahc.AhcWSClient
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.mvc.MultipartFormData
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.config.FileUploadConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.domain.MimeContentType
import uk.gov.hmrc.tai.model.fileupload.{EnvelopeFile, EnvelopeSummary}
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class FileUploadConnectorSpec extends BaseSpec {

  "createEnvelope" must {
    "return an envelope id" in {
      val mockHttpClient = mock[HttpClient]
      when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(
          HttpResponse(CREATED, None, Map("Location" -> Seq(s"localhost:8898/file-upload/envelopes/$envelopeId")))))

      val sut = createSut(mockHttpClient)
      Await.result(sut.createEnvelope, 5 seconds) mustBe "4142477f-9242-4a98-9c8b-73295cfb170c"
    }
    "call the file upload service create envelope endpoint" in {
      val callbackUrl = "theCallBackURL"
      val envelopeBody = Json.obj("callbackUrl" -> callbackUrl)

      val mockConfig = mock[FileUploadConfig]
      when(mockConfig.callbackUrl)
        .thenReturn(callbackUrl)

      val mockUrls = mock[FileUploadUrls]
      when(mockUrls.envelopesUrl)
        .thenReturn("createEnvelopeURL")

      val mockHttpClient = mock[HttpClient]
      when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(
          HttpResponse(CREATED, None, Map("Location" -> Seq(s"localhost:8898/file-upload/envelopes/$envelopeId")))))

      val sut = createSut(mockHttpClient, urls = mockUrls, config = mockConfig)
      Await.result(sut.createEnvelope, 5.seconds)

      verify(mockHttpClient, times(1))
        .POST(meq("createEnvelopeURL"), meq(envelopeBody), any())(any(), any(), any(), any())
    }
    "throw a runtime exception" when {
      "the success response does not contain a location header" in {
        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(CREATED)))

        val sut = createSut(mockHttpClient)
        val ex = the[RuntimeException] thrownBy Await.result(sut.createEnvelope, 5 seconds)

        ex.getMessage mustBe "File upload envelope creation failed"
      }
      "the call to the file upload service create envelope endpoint fails" in {
        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.failed(new RuntimeException("call failed")))

        val sut = createSut(HttpClient = mockHttpClient)
        val ex = the[RuntimeException] thrownBy Await.result(sut.createEnvelope, 5 seconds)

        ex.getMessage mustBe "File upload envelope creation failed"

      }
      "the call to the file upload service returns a failure response" in {
        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(400)))

        val sut = createSut(mockHttpClient)
        val ex = the[RuntimeException] thrownBy Await.result(sut.createEnvelope, 5 seconds)

        ex.getMessage mustBe "File upload envelope creation failed"
      }
    }
  }

  "uploadFile" must {

    "return Success" when {
      "File upload service successfully upload the file" in {
        val mockUrls = mock[FileUploadUrls]
        when(mockUrls.envelopesUrl).thenReturn("envelope")
        when(mockUrls.fileUrl(any(), any())).thenReturn("file")

        val mockOkClient = setupWSClient(createMockResponse(OK, ""))
        val mockOpenTestClient =
          setupWSClient(createMockResponse(200, Json.obj("id" -> envelopeId, "status" -> "OPEN", "TEST" -> "Data")))

        val sut = createSut(wsClient = mockOpenTestClient, urls = mockUrls)
        val result = Await.result(
          sut.uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, mockOkClient),
          5 seconds)

        result.status mustBe OK
      }
    }
    "throw runtime exception" when {

      "file upload service return status other than OK on uploadfile" in {
        val ahcWSClient = setupWSClient(createMockResponse(BAD_REQUEST, ""))
        val wsClient =
          setupWSClient(createMockResponse(200, Json.obj("id" -> envelopeId, "status" -> "OPEN", "TEST" -> "Data")))

        val sut = createSut(wsClient = wsClient)

        the[RuntimeException] thrownBy Await
          .result(sut.uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, ahcWSClient), 5 seconds)
      }

      "file upload service returns non-open summary" in {
        val ahcWSClient = setupWSClient(createMockResponse(BAD_REQUEST, ""))
        val wsClient =
          setupWSClient(createMockResponse(200, Json.obj("id" -> envelopeId, "status" -> "CLOSED", "TEST" -> "Data")))
        val sut = createSut(wsClient = wsClient)

        the[RuntimeException] thrownBy Await
          .result(sut.uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, ahcWSClient), 5 seconds)
      }

      "file upload service returns none" in {
        val ahcWSClient = setupWSClient(createMockResponse(BAD_REQUEST, ""))
        val wsClient = setupWSClient(createMockResponse(400, envelopeStatusResponse("AVAILABLE", "AVAILABLE")))
        val sut = createSut(wsClient = wsClient)

        the[RuntimeException] thrownBy Await
          .result(sut.uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, ahcWSClient), 5 seconds)
      }

      "any error occurred" in {
        val mockWsClient = setupWSClient(failed = true)
        val sut = createSut(wsClient = mockWsClient)

        the[RuntimeException] thrownBy Await
          .result(
            sut.uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, mock[AhcWSClient]),
            5 seconds)
      }
    }

  }

  "closeEnvelope" must {
    "return an envelope id" in {
      val mockHttpClient = mock[HttpClient]
      when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
        .thenReturn(
          Future.successful(HttpResponse(CREATED, None, Map("Location" -> Seq(s"/file-routing/requests/$envelopeId")))))

      val sut = createSut(mockHttpClient)

      Await.result(sut.closeEnvelope(envelopeId), 5 seconds) mustBe envelopeId
    }

    "call the file upload service routing request endpoint" in {
      val uploadEndpoint = "uploadEndpointURL"

      val mockHttpClient = mock[HttpClient]
      when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
        .thenReturn(
          Future.successful(HttpResponse(CREATED, None, Map("Location" -> Seq(s"/file-routing/requests/$envelopeId")))))

      val mockUrls = mock[FileUploadUrls]
      when(mockUrls.fileUrl(any(), any()))
        .thenReturn(uploadEndpoint)
      when(mockUrls.routingUrl)
        .thenReturn(uploadEndpoint)

      val sut = createSut(mockHttpClient, urls = mockUrls)
      Await.result(sut.closeEnvelope(envelopeId), 5.seconds)

      verify(mockHttpClient, times(1))
        .POST(meq(uploadEndpoint), any(), any())(any(), any(), any(), any())
    }
    "throw a runtime exception" when {
      "the success response does not contain a location header" in {
        val mockHttpClient = mock[HttpClient]

        when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(CREATED)))

        val sut = createSut(mockHttpClient)

        val ex = the[RuntimeException] thrownBy Await.result(sut.closeEnvelope(envelopeId), 5 seconds)

        ex.getMessage mustBe "File upload envelope routing request failed"
      }
      "the call to the file upload service routing request endpoint fails" in {
        val mockHttpClient = mock[HttpClient]

        when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.failed(new RuntimeException("call failed")))

        val sut = createSut(mockHttpClient)

        val ex = the[RuntimeException] thrownBy Await.result(sut.closeEnvelope(envelopeId), 5 seconds)

        ex.getMessage mustBe "File upload envelope routing request failed"

      }
      "the call to the file upload service returns a failure response" in {
        val mockHttpClient = mock[HttpClient]

        when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(400)))

        val sut = createSut(mockHttpClient)

        val ex = the[RuntimeException] thrownBy Await.result(sut.closeEnvelope(envelopeId), 5 seconds)

        ex.getMessage mustBe "File upload envelope routing request failed"

      }
    }
  }

  "envelopeStatus" must {
    "return envelope summary" when {
      "both files are available" in {
        val mockWsClient =
          setupWSClient(createMockResponse(200, envelopeStatusResponse("AVAILABLE", "AVAILABLE", "CLOSED")))

        val sut = createSut(wsClient = mockWsClient)

        val result = Await.result(sut.envelope(envelopeId), 5.seconds)

        result.get mustBe EnvelopeSummary(
          envelopeId,
          "CLOSED",
          Seq(
            EnvelopeFile("4142477f-9242-4a98-9c8b-73295cfb170c-EndEmployment-20171009-iform", "AVAILABLE"),
            EnvelopeFile("4142477f-9242-4a98-9c8b-73295cfb170c-EndEmployment-20171009-metadata", "AVAILABLE")
          )
        )

      }

      "there are no files" in {
        val mockWsClient = setupWSClient(
          createMockResponse(200, Json.obj("id" -> envelopeId, "status" -> "OPEN", "TEST" -> "Data"))
        )
        val sut = createSut(wsClient = mockWsClient)

        val result = Await.result(sut.envelope(envelopeId), 5.seconds)

        result.get mustBe EnvelopeSummary(envelopeId, "OPEN", Nil)

      }
    }

    "return None" when {
      "status is not OK" in {
        val mockWsClient = setupWSClient(
          createMockResponse(400, envelopeStatusResponse("AVAILABLE", "AVAILABLE"))
        )
        val sut = createSut(wsClient = mockWsClient)
        val result = Await.result(sut.envelope(envelopeId), 5.seconds)

        result mustBe None
      }

      "response is incorrect" in {
        val mockWsClient = setupWSClient(createMockResponse(200, Json.obj("asdas" -> "asdas")))
        val sut = createSut(wsClient = mockWsClient)

        val result = Await.result(sut.envelope(envelopeId), 5.seconds)

        result mustBe None
      }
    }

    "Retry 5 times when no good result and maxAttempts configured to 5 retries" in {
      val mockWsClient = setupWSClient(createMockResponse(404, envelopeStatusResponse("AVAILABLE", "AVAILABLE")))

      val mockConfig = mock[FileUploadConfig]
      when(mockConfig.maxAttempts).thenReturn(5)
      when(mockConfig.intervalMs).thenReturn(20)

      val sut = createSut(wsClient = mockWsClient, config = mockConfig)

      assertThrows[RuntimeException] {
        Await.result(sut.envelope(envelopeId), Duration.Inf)
      }

      verify(mockWsClient, times(5)).url(any())

    }
  }

  private def createMockResponse(status: Int, body: String): WSResponse = {
    val wsResponseMock = mock[WSResponse]
    when(wsResponseMock.status).thenReturn(status)
    when(wsResponseMock.body).thenReturn(body)
    wsResponseMock
  }

  private def createMockResponse(status: Int, json: JsValue): WSResponse = {
    val wsResponseMock = mock[WSResponse]
    when(wsResponseMock.status).thenReturn(status)
    when(wsResponseMock.json).thenReturn(json)
    wsResponseMock
  }

  private val envelopeId: String = "4142477f-9242-4a98-9c8b-73295cfb170c"

  private def envelopeStatusResponse(file1Status: String, file2Status: String, status: String = "OPEN") = {
    val file1 =
      Json.obj("id" -> "4142477f-9242-4a98-9c8b-73295cfb170c-EndEmployment-20171009-iform", "status" -> file1Status)
    val file2 =
      Json.obj("id" -> "4142477f-9242-4a98-9c8b-73295cfb170c-EndEmployment-20171009-metadata", "status" -> file2Status)
    Json.obj("id" -> envelopeId, "status" -> status, "files" -> JsArray(Seq(file1, file2)))
  }

  private val fileId = "fileId"
  private val fileName = "fileName.pdf"
  private val contentType = MimeContentType.ApplicationPdf

  private def setupWSClient(mockResponse: WSResponse = mock[WSResponse], failed: Boolean = false): AhcWSClient = {
    val mockRequest = mock[WSRequest]
    val mockClient = mock[AhcWSClient]

    if (!failed) {
      when(mockRequest.post(any[Source[MultipartFormData.Part[Source[ByteString, _]], _]]()))
        .thenReturn(Future.successful(mockResponse))
      when(mockRequest.get())
        .thenReturn(Future.successful(mockResponse))
    } else {
      when(mockRequest.post(any[Source[MultipartFormData.Part[Source[ByteString, _]], _]]()))
        .thenReturn(Future.failed(new RuntimeException("Error")))
      when(mockRequest.get())
        .thenReturn(Future.failed(new RuntimeException("Error")))
    }
    when(mockRequest.withHeaders(any())).thenReturn(mockRequest)
    when(mockClient.url(any())).thenReturn(mockRequest)

    mockClient
  }

  private def setupMetrics: Metrics = {
    val m = mock[Metrics]
    val tc = mock[Timer.Context]
    when(m.startTimer(any())).thenReturn(tc)
    m
  }

  private def createSut(
    HttpClient: HttpClient = mock[HttpClient],
    wsClient: WSClient = mock[WSClient],
    urls: FileUploadUrls = mock[FileUploadUrls],
    config: FileUploadConfig = mock[FileUploadConfig]) =
    new FileUploadConnector(setupMetrics, HttpClient, wsClient, urls, config)

  @After
  def validate() = validateMockitoUsage()
}

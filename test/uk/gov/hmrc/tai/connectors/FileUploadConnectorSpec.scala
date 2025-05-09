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

package uk.gov.hmrc.tai.connectors

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.StubImport.stubImport
import org.apache.pekko.stream.Materializer
import play.api.http.Status.*
import play.api.libs.json.{JsArray, Json}
import play.api.libs.ws.ahc.AhcWSClient
import play.shaded.ahc.org.asynchttpclient.exception.RemotelyClosedException
import uk.gov.hmrc.http.{HeaderNames, UpstreamErrorResponse}
import uk.gov.hmrc.tai.model.domain.MimeContentType
import uk.gov.hmrc.tai.model.fileupload.{EnvelopeFile, EnvelopeSummary}

class FileUploadConnectorSpec extends ConnectorBaseSpec {

  lazy val sut: FileUploadConnector = inject[FileUploadConnector]

  val envelopeId: String = "4142477f-9242-4a98-9c8b-73295cfb170c"
  val fileId = "fileId"
  val fileName = "fileName.pdf"
  val contentType: MimeContentType = MimeContentType.ApplicationPdf

  lazy val envelopesHeader = s"${server.baseUrl()}/file-upload/envelopes/$envelopeId"

  val envelopesUrl = s"/file-upload/envelopes"
  val envelopeWithIdUrl = s"/file-upload/envelopes/$envelopeId"
  val fileUrl = s"/file-upload/upload/envelopes/$envelopeId/files/$fileId"
  val closeEnvelopeUrl = s"/file-routing/requests"

  def verifyOutgoingUpdateHeaders(requestPattern: RequestPatternBuilder): Unit =
    server.verify(
      requestPattern
        .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
        .withHeader(HeaderNames.xRequestId, equalTo(requestId))
    )

  "createEnvelope" must {
    "return an envelope id" in {

      server.stubFor(
        post(urlEqualTo(envelopesUrl)).willReturn(
          aResponse()
            .withStatus(CREATED)
            .withBody("")
            .withHeader("Location", s"${server.baseUrl()}$envelopeWithIdUrl")
        )
      )

      sut.createEnvelope.futureValue mustBe envelopeId

      verifyOutgoingUpdateHeaders(postRequestedFor(urlEqualTo(envelopesUrl)))
    }

    "throw a runtime exception" when {
      List(
        BAD_REQUEST,
        NOT_FOUND,
        IM_A_TEAPOT,
        INTERNAL_SERVER_ERROR,
        SERVICE_UNAVAILABLE
      ).foreach { httpStatus =>
        s" RuntimeException occurred for $httpStatus  response" in {

          server.stubFor(
            post(urlEqualTo(envelopesUrl)).willReturn(
              aResponse()
                .withStatus(httpStatus)
            )
          )

          val result = sut.createEnvelope.failed.futureValue

          result mustBe a[RuntimeException]

          result.getMessage mustBe s"File upload envelope creation failed with status: $httpStatus"
        }
      }

      "the success response does not contain a location header" in {

        server.stubFor(
          post(urlEqualTo(envelopesUrl)).willReturn(
            aResponse()
              .withStatus(CREATED)
          )
        )

        val result = sut.createEnvelope.failed.futureValue

        result mustBe a[RuntimeException]

        result.getMessage mustBe "No envelope id returned by file upload service"
      }

      "the call to the file upload service create envelope endpoint fails" in {

        server.stubFor(
          post(urlEqualTo(envelopesUrl)).willReturn(
            aResponse()
              .withFault(Fault.MALFORMED_RESPONSE_CHUNK)
          )
        )

        val result = sut.createEnvelope.failed.futureValue

        result mustBe a[RemotelyClosedException]
      }
      "the call to the file upload service returns a failure response" in {

        server.stubFor(
          post(urlEqualTo(envelopesUrl)).willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
          )
        )

        val result = sut.createEnvelope.failed.futureValue

        result mustBe a[RuntimeException]

        result.getMessage mustBe s"File upload envelope creation failed with status: 400"
      }
    }
  }

  "uploadFile" must {

    lazy val ahcWSClient = AhcWSClient(cache = None)(inject[Materializer])

    "return Success" when {
      "File upload service successfully upload the file" in {

        val json = Json.obj("id" -> envelopeId, "status" -> "OPEN", "TEST" -> "Data").toString()

        server.importStubs(
          stubImport()
            .stub(
              post(urlEqualTo(fileUrl)).willReturn(
                aResponse()
                  .withStatus(OK)
              )
            )
            .stub(
              get(urlEqualTo(s"$envelopesUrl/$envelopeId")).willReturn(
                aResponse()
                  .withStatus(OK)
                  .withBody(json)
              )
            )
            .build()
        )

        val result =
          sut.uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, ahcWSClient).futureValue

        result.status mustBe OK

        server.verify(
          postRequestedFor(urlEqualTo(fileUrl))
            .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
            .withHeader(HeaderNames.xRequestId, equalTo(requestId))
            .withHeader("CSRF-token", equalTo("nocheck"))
        )

      }
    }

    "throw runtime exception" when {

      "file upload service return status other than OK on uploadfile" in {

        server.importStubs(
          stubImport()
            .stub(
              post(urlEqualTo(fileUrl)).willReturn(
                aResponse()
                  .withStatus(OK)
              )
            )
            .stub(
              get(urlEqualTo(envelopeWithIdUrl)).willReturn(
                aResponse()
                  .withStatus(BAD_REQUEST)
              )
            )
            .build()
        )

        val result =
          sut.uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, ahcWSClient).failed.futureValue

        result mustBe a[RuntimeException]

        result.getMessage mustBe "Unable to find Envelope"
      }

      "file upload service returns non-open summary" in {

        val json = Json.obj("id" -> envelopeId, "status" -> "CLOSED", "TEST" -> "Data").toString()

        server.importStubs(
          stubImport()
            .stub(
              post(urlEqualTo(fileUrl)).willReturn(
                aResponse()
                  .withStatus(OK)
              )
            )
            .stub(
              get(urlEqualTo(envelopeWithIdUrl)).willReturn(
                aResponse()
                  .withStatus(OK)
                  .withBody(json)
              )
            )
            .build()
        )

        val result =
          sut.uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, ahcWSClient).failed.futureValue

        result mustBe a[RuntimeException]

        result.getMessage mustBe "Unable to find Envelope"
      }

      "file upload service returns none" in {

        val json = Json.obj("id" -> envelopeId, "status" -> "CLOSED", "TEST" -> "Data").toString()

        server.importStubs(
          stubImport()
            .stub(
              post(urlEqualTo(fileUrl)).willReturn(
                aResponse()
                  .withStatus(BAD_REQUEST)
              )
            )
            .stub(
              get(urlEqualTo(envelopeWithIdUrl)).willReturn(
                aResponse()
                  .withStatus(OK)
                  .withBody(json)
              )
            )
            .build()
        )

        val result =
          sut.uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, ahcWSClient).failed.futureValue

        result mustBe a[RuntimeException]

        result.getMessage mustBe "Unable to find Envelope"
      }

      List(
        BAD_REQUEST,
        NOT_FOUND,
        IM_A_TEAPOT,
        INTERNAL_SERVER_ERROR,
        SERVICE_UNAVAILABLE
      ).foreach { httpStatus =>
        s" RuntimeException occurred for $httpStatus  response" in {

          server.importStubs(
            stubImport()
              .stub(
                post(urlEqualTo(fileUrl)).willReturn(
                  aResponse()
                    .withFault(Fault.MALFORMED_RESPONSE_CHUNK)
                )
              )
              .stub(
                get(urlEqualTo(envelopeWithIdUrl)).willReturn(
                  aResponse()
                    .withStatus(httpStatus)
                )
              )
              .build()
          )

          val result = sut
            .uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, ahcWSClient)
            .failed
            .futureValue

          result mustBe a[RuntimeException]

          result.getMessage mustBe "Unable to find Envelope"
        }
      }

      "any error occurred" in {

        server.importStubs(
          stubImport()
            .stub(
              post(urlEqualTo(fileUrl)).willReturn(
                aResponse()
                  .withFault(Fault.MALFORMED_RESPONSE_CHUNK)
              )
            )
            .stub(
              get(urlEqualTo(envelopeWithIdUrl)).willReturn(
                aResponse()
                  .withStatus(BAD_REQUEST)
              )
            )
            .build()
        )

        val result =
          sut.uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, ahcWSClient).failed.futureValue

        result mustBe a[RuntimeException]

        result.getMessage mustBe "Unable to find Envelope"
      }

    }
  }

  "closeEnvelope" must {
    "return an envelope id" in {

      server.stubFor(
        post(urlEqualTo(closeEnvelopeUrl)).willReturn(
          aResponse()
            .withStatus(CREATED)
            .withBody(envelopeId)
            .withHeader("Location", s"/file-routing/requests/$envelopeId")
        )
      )

      sut.closeEnvelope(envelopeId).futureValue mustBe envelopeId

      verifyOutgoingUpdateHeaders(postRequestedFor(urlEqualTo(closeEnvelopeUrl)))
    }

    "throw a runtime exception" when {

      "the success response does not contain a location header" in {

        server.stubFor(
          post(urlEqualTo(closeEnvelopeUrl)).willReturn(
            aResponse()
              .withStatus(CREATED)
              .withBody(envelopeId)
          )
        )

        val result = sut.closeEnvelope(envelopeId).failed.futureValue

        result mustBe a[RuntimeException]

        result.getMessage mustBe "No envelope id returned by file upload service"
      }

      "the call to the file upload service routing request endpoint fails" in {

        server.stubFor(
          post(urlEqualTo(closeEnvelopeUrl)).willReturn(
            aResponse()
              .withFault(Fault.MALFORMED_RESPONSE_CHUNK)
          )
        )

        val result = sut.closeEnvelope(envelopeId).failed.futureValue

        result mustBe a[RemotelyClosedException]
      }

      List(
        BAD_REQUEST,
        NOT_FOUND,
        IM_A_TEAPOT,
        INTERNAL_SERVER_ERROR,
        SERVICE_UNAVAILABLE
      ).foreach { httpStatus =>
        s" RuntimeException occurred for $httpStatus  response" in {

          server.stubFor(
            post(urlEqualTo(closeEnvelopeUrl)).willReturn(
              aResponse()
                .withStatus(httpStatus)
            )
          )

          val result = sut.closeEnvelope(envelopeId).failed.futureValue

          result mustBe a[UpstreamErrorResponse]

          result.getMessage contains s"returned $httpStatus" mustBe true
        }
      }

      "the call to the file upload service returns a failure response" in {

        server.stubFor(
          post(urlEqualTo(closeEnvelopeUrl)).willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
          )
        )

        val result = sut.closeEnvelope(envelopeId).failed.futureValue

        result mustBe a[UpstreamErrorResponse]

        result.getMessage contains s"returned 400" mustBe true
      }
    }
  }

  "envelopeStatus" must {
    "return envelope summary" when {
      "both files are available" in {

        val status = "AVAILABLE"

        val file1 =
          Json.obj("id" -> "4142477f-9242-4a98-9c8b-73295cfb170c-EndEmployment-20171009-iform", "status" -> status)
        val file2 =
          Json.obj("id" -> "4142477f-9242-4a98-9c8b-73295cfb170c-EndEmployment-20171009-metadata", "status" -> status)
        val body = Json.obj("id" -> envelopeId, "status" -> "CLOSED", "files" -> JsArray(Seq(file1, file2)))

        server.stubFor(
          get(urlEqualTo(envelopeWithIdUrl)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(body.toString())
          )
        )

        val result = sut.envelope(envelopeId).futureValue

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

        val body =
          Json.obj("id" -> envelopeId, "status" -> "OPEN", "files" -> JsArray(Seq.empty)).toString

        server.stubFor(
          get(urlEqualTo(envelopeWithIdUrl)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(body)
          )
        )

        val result = sut.envelope(envelopeId).futureValue

        result.get mustBe EnvelopeSummary(envelopeId, "OPEN", Nil)

      }
    }

    "return None" when {
      "status is not OK" in {

        server.stubFor(
          get(urlEqualTo(envelopeWithIdUrl)).willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
          )
        )

        val result = sut.envelope(envelopeId).futureValue

        result mustBe None
      }

      "response is incorrect" in {

        server.stubFor(
          get(urlEqualTo(envelopeWithIdUrl)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody("""{"foo": "bar"}""")
          )
        )

        val result = sut.envelope(envelopeId).futureValue

        result mustBe None
      }
    }

    "Throw a RuntimeException on returning 404 and after all retries have failed" in {

      server.stubFor(
        get(urlEqualTo(envelopeWithIdUrl)).willReturn(
          aResponse()
            .withStatus(NOT_FOUND)
        )
      )

      val result = sut.envelope(envelopeId).failed.futureValue

      result mustBe a[RuntimeException]
    }

    List(
      BAD_REQUEST,
      IM_A_TEAPOT,
      INTERNAL_SERVER_ERROR,
      SERVICE_UNAVAILABLE
    ).foreach { httpStatus =>
      s" None response returned for $httpStatus  response" in {

        server.stubFor(
          get(urlEqualTo(envelopeWithIdUrl)).willReturn(
            aResponse()
              .withStatus(httpStatus)
          )
        )
        val result = sut.envelope(envelopeId).futureValue

        result mustBe None
      }
    }
  }
}

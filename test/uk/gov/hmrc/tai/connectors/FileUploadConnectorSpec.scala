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

package uk.gov.hmrc.tai.connectors

import akka.stream.Materializer
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, equalTo, get, getRequestedFor, matching, post, postRequestedFor, urlEqualTo}
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.StubImport.stubImport
import controllers.Assets.IM_A_TEAPOT
import play.api.http.Status.{BAD_REQUEST, CREATED, INTERNAL_SERVER_ERROR, NOT_FOUND, OK, SERVICE_UNAVAILABLE}
import play.api.libs.json.{JsArray, Json}
import play.api.libs.ws.ahc.AhcWSClient
import uk.gov.hmrc.tai.model.domain.MimeContentType
import uk.gov.hmrc.tai.model.fileupload.{EnvelopeFile, EnvelopeSummary}
import uk.gov.hmrc.http.{BadRequestException, HeaderNames}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class FileUploadConnectorSpec extends ConnectorBaseSpec {

  lazy val sut: FileUploadConnector = inject[FileUploadConnector]

  val envelopeId: String = "4142477f-9242-4a98-9c8b-73295cfb170c"
  val fileId = "fileId"
  val fileName = "fileName.pdf"
  val contentType = MimeContentType.ApplicationPdf

  lazy val envelopesHeader = s"${server.baseUrl()}/file-upload/envelopes/$envelopeId"

  val envelopesUrl = s"/file-upload/envelopes"
  val envelopeWithIdUrl = s"/file-upload/envelopes/$envelopeId"
  val fileUrl = s"/file-upload/upload/envelopes/$envelopeId/files/$fileId"
  val closeEnvelopeUrl = s"/file-routing/requests"

  def verifyOutgoingUpdateHeaders(requestPattern: RequestPatternBuilder): Unit =
    server.verify(
      requestPattern
        .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
        .withHeader(HeaderNames.xRequestId, equalTo(requestId)))

  "createEnvelope" must {
    "return an envelope id" in {

      server.stubFor(
        post(urlEqualTo(envelopesUrl)).willReturn(
          aResponse()
            .withStatus(CREATED)
            .withBody("")
            .withHeader("Location", s"${server.baseUrl()}$envelopeWithIdUrl"))
      )

      Await.result(sut.createEnvelope, 5 seconds) mustBe envelopeId

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

          the[RuntimeException] thrownBy {
            Await.result(sut.createEnvelope, 5 seconds)
          } must have message "File upload envelope creation failed"
        }
      }

      "the success response does not contain a location header" in {

        server.stubFor(
          post(urlEqualTo(envelopesUrl)).willReturn(
            aResponse()
              .withStatus(CREATED)
          )
        )

        the[RuntimeException] thrownBy {
          Await.result(sut.createEnvelope, 5 seconds)
        } must have message "File upload envelope creation failed"
      }

      "the call to the file upload service create envelope endpoint fails" in {

        server.stubFor(
          post(urlEqualTo(envelopesUrl)).willReturn(
            aResponse()
              .withFault(Fault.MALFORMED_RESPONSE_CHUNK)
          )
        )

        the[RuntimeException] thrownBy {
          Await.result(sut.createEnvelope, 5 seconds)
        } must have message "File upload envelope creation failed"

      }
      "the call to the file upload service returns a failure response" in {

        server.stubFor(
          post(urlEqualTo(envelopesUrl)).willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
          )
        )

        the[RuntimeException] thrownBy {
          Await.result(sut.createEnvelope, 5 seconds)
        } must have message "File upload envelope creation failed"
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

        val result = Await
          .result(sut.uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, ahcWSClient), 5 seconds)

        result.status mustBe OK

        server.verify(
          postRequestedFor(urlEqualTo(fileUrl))
            .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
            .withHeader(HeaderNames.xRequestId, equalTo(requestId))
            .withHeader("CSRF-token", equalTo("nocheck")))

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

        the[RuntimeException] thrownBy Await
          .result(sut.uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, ahcWSClient), 5 seconds)
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

        the[RuntimeException] thrownBy Await
          .result(sut.uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, ahcWSClient), 5 seconds)
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

        the[RuntimeException] thrownBy Await
          .result(sut.uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, ahcWSClient), 5 seconds)
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

          the[RuntimeException] thrownBy Await
            .result(
              sut.uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, ahcWSClient),
              5 seconds)
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

        the[RuntimeException] thrownBy Await
          .result(sut.uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, ahcWSClient), 5 seconds)
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

      Await.result(sut.closeEnvelope(envelopeId), 5 seconds) mustBe envelopeId

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

        the[RuntimeException] thrownBy {
          Await.result(sut.closeEnvelope(envelopeId), 5 seconds)
        } must have message "File upload envelope routing request failed"
      }

      "the call to the file upload service routing request endpoint fails" in {

        server.stubFor(
          post(urlEqualTo(closeEnvelopeUrl)).willReturn(
            aResponse()
              .withFault(Fault.MALFORMED_RESPONSE_CHUNK)
          )
        )

        the[RuntimeException] thrownBy {
          Await.result(sut.closeEnvelope(envelopeId), 5 seconds)
        } must have message "File upload envelope routing request failed"
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

          the[RuntimeException] thrownBy {
            Await.result(sut.closeEnvelope(envelopeId), 5 seconds)
          } must have message "File upload envelope routing request failed"
        }
      }

      "the call to the file upload service returns a failure response" in {

        server.stubFor(
          post(urlEqualTo(closeEnvelopeUrl)).willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
          )
        )

        the[RuntimeException] thrownBy {
          Await.result(sut.closeEnvelope(envelopeId), 5 seconds)
        } must have message "File upload envelope routing request failed"
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
          Json.obj("id"          -> "4142477f-9242-4a98-9c8b-73295cfb170c-EndEmployment-20171009-metadata", "status" -> status)
        val body = Json.obj("id" -> envelopeId, "status"                                                             -> "CLOSED", "files" -> JsArray(Seq(file1, file2)))

        server.stubFor(
          get(urlEqualTo(envelopeWithIdUrl)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(body.toString())
          )
        )

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

        val body =
          Json.obj("id" -> envelopeId, "status" -> "OPEN", "files" -> JsArray(Seq.empty)).toString

        server.stubFor(
          get(urlEqualTo(envelopeWithIdUrl)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(body)
          )
        )

        val result = Await.result(sut.envelope(envelopeId), 5.seconds)

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

        val result = Await.result(sut.envelope(envelopeId), 5.seconds)

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

        val result = Await.result(sut.envelope(envelopeId), 5.seconds)

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

      assertThrows[RuntimeException] {
        Await.result(sut.envelope(envelopeId), Duration.Inf)
      }
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
        val result = Await.result(sut.envelope(envelopeId), 5.seconds)

        result mustBe None
      }
    }
  }
}

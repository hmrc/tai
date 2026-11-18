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
import play.api.http.Status.*
import play.api.libs.json.Json
import uk.gov.hmrc.tai.config.DmsSubmissionConfig
import uk.gov.hmrc.tai.model.dms.{DmsSubmissionMetadata, DmsSubmissionResponse}

import java.time.Instant

class DmsSubmissionConnectorSpec extends ConnectorBaseSpec {

  lazy val sut: DmsSubmissionConnector = inject[DmsSubmissionConnector]
  lazy val config: DmsSubmissionConfig = inject[DmsSubmissionConfig]

  private val submitUrl = "/dms-submission/submit"
  private val pdf = "pdf-content".getBytes
  private val metadata =
    DmsSubmissionMetadata("AB123456", "TES1", "TAI", "PSA-DFS TES", "PSA", Instant.parse("2026-10-05T12:30:45Z"))

  private val acceptedBody = Json.obj("id" -> "dms-1").toString

  "submit" must {

    "post the metadata and the pdf as multipart with the internal-auth token and return the submission id" in {
      server.stubFor(post(urlEqualTo(submitUrl)).willReturn(aResponse().withStatus(ACCEPTED).withBody(acceptedBody)))

      sut.submit(pdf, "file.pdf", metadata).futureValue mustBe DmsSubmissionResponse("dms-1")

      server.verify(
        postRequestedFor(urlEqualTo(submitUrl))
          .withHeader("Authorization", equalTo(config.authToken))
          .withHeader("Content-Type", containing("multipart/form-data"))
          .withRequestBodyPart(aMultipart().withName("callbackUrl").withBody(equalTo(config.callbackUrl)).build())
          .withRequestBodyPart(aMultipart().withName("metadata.store").withBody(equalTo("true")).build())
          .withRequestBodyPart(aMultipart().withName("metadata.source").withBody(equalTo("TAI")).build())
          .withRequestBodyPart(
            aMultipart().withName("metadata.timeOfReceipt").withBody(equalTo("2026-10-05T12:30:45")).build()
          )
          .withRequestBodyPart(aMultipart().withName("metadata.formId").withBody(equalTo("TES1")).build())
          .withRequestBodyPart(aMultipart().withName("metadata.customerId").withBody(equalTo("AB123456")).build())
          .withRequestBodyPart(
            aMultipart().withName("metadata.classificationType").withBody(equalTo("PSA-DFS TES")).build()
          )
          .withRequestBodyPart(aMultipart().withName("metadata.businessArea").withBody(equalTo("PSA")).build())
          .withRequestBodyPart(aMultipart().withName("form").withBody(equalTo("pdf-content")).build())
      )
    }

    "throw a RuntimeException" when {
      List(BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, INTERNAL_SERVER_ERROR).foreach { httpStatus =>
        s"dms-submission responds with $httpStatus" in {
          server.stubFor(post(urlEqualTo(submitUrl)).willReturn(aResponse().withStatus(httpStatus).withBody("{}")))

          val result = sut.submit(pdf, "file.pdf", metadata).failed.futureValue

          result mustBe a[RuntimeException]
          result.getMessage mustBe s"dms-submission request failed with status $httpStatus: {}"
        }
      }
    }
  }
}

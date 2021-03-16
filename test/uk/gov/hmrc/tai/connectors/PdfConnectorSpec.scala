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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, urlEqualTo}
import play.api.http.Status._
import uk.gov.hmrc.http.HttpException

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class PdfConnectorSpec extends ConnectorBaseSpec {

  lazy val sut: PdfConnector = inject[PdfConnector]

  val url: String = "/pdf-generator-service/generate"
  val htmlAsString: String = "<html>test</html>"

  "PdfConnector" must {

    "return the pdf service payload in bytes " when {
      "generatePdf is called successfully" in {

        server.stubFor(
          post(urlEqualTo(url)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(htmlAsString))
        )

        Await.result(sut.generatePdf(htmlAsString), 5 seconds) mustBe htmlAsString.getBytes
      }
    }

    "generate an HttpException" when {
      "generatePdf is called and the pdf service returns 4xx" in {
        val exMessage = "Invalid payload"

        server.stubFor(
          post(urlEqualTo(url)).willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
              .withBody(exMessage))
        )

        assertConnectorException[HttpException](
          sut.generatePdf(htmlAsString),
          BAD_REQUEST,
          exMessage
        )
      }

      "generatePdf is called and the pdf service returns 5xx" in {
        val exMessage = "An error occurred"

        server.stubFor(
          post(urlEqualTo(url)).willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
              .withBody(exMessage))
        )

        assertConnectorException[HttpException](
          sut.generatePdf(htmlAsString),
          INTERNAL_SERVER_ERROR,
          exMessage
        )
      }
    }
  }
}

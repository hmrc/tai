/*
 * Copyright 2019 HM Revenue & Customs
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

import akka.util.ByteString
import com.codahale.metrics.Timer
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import uk.gov.hmrc.http.HttpException
import uk.gov.hmrc.tai.controllers.FakeTaiPlayApplication
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.enums.APITypes
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class PdfConnectorSpec extends PlaySpec
    with MockitoSugar
    with FakeTaiPlayApplication {

  "PdfConnector" must {

    "return the pdf service payload in bytes " when {
      "generatePdf is called successfully" in {
        val htmlAsString = "<html>test</html>"

        val mockTimerContext = mock[Timer.Context]
        val mockWSResponse = createMockResponse(200, htmlAsString)

        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val mockWSRequest = mock[WSRequest]
        when(mockWSRequest.post(anyString())(any()))
          .thenReturn(Future.successful(mockWSResponse))

        val mockWSClient = mock[WSClient]
        when(mockWSClient.url(any()))
          .thenReturn(mockWSRequest)

        val sut = createSut(mockMetrics, mockWSClient, mock[PdfUrls])
        val response = sut.generatePdf(htmlAsString)

        val result = Await.result(response, 5 seconds)

        result mustBe htmlAsString.getBytes

        verify(mockWSRequest, times(1))
          .post(anyString())(any())
        verify(mockMetrics, times(1))
          .startTimer(APITypes.PdfServiceAPI)
        verify(mockMetrics, times(1))
          .incrementSuccessCounter(APITypes.PdfServiceAPI)
        verify(mockMetrics, never())
          .incrementFailedCounter(any())
        verify(mockTimerContext, times(1))
          .stop()
      }
    }

    "generate an HttpException" when {
      "generatePdf is called and the pdf service returns something other than 200" in {
        val htmlAsString = "<html>test</html>"

        val mockTimerContext = mock[Timer.Context]
        val mockWSResponse = createMockResponse(400, "")

        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val mockWSRequest = mock[WSRequest]
        when(mockWSRequest.post(anyString())(any()))
          .thenReturn(Future.successful(mockWSResponse))

        val mockWSClient = mock[WSClient]
        when(mockWSClient.url(any()))
          .thenReturn(mockWSRequest)

        val sut = createSut(mockMetrics, mockWSClient, mock[PdfUrls])
        val result = sut.generatePdf(htmlAsString)

        the[HttpException] thrownBy Await.result(result, 5 seconds)

        verify(mockWSRequest, times(1))
          .post(anyString())(any())
        verify(mockMetrics, times(1))
          .startTimer(APITypes.PdfServiceAPI)
        verify(mockMetrics, never())
          .incrementSuccessCounter(any())
        verify(mockMetrics, times(1))
          .incrementFailedCounter(APITypes.PdfServiceAPI)
        verify(mockTimerContext, times(1))
          .stop()
      }
    }
  }

  private def createMockResponse(status: Int, body: String): WSResponse = {

    val wsResponseMock = mock[WSResponse]

    when(wsResponseMock.status).thenReturn(status)
    when(wsResponseMock.body).thenReturn(body)
    when(wsResponseMock.bodyAsBytes).thenReturn(ByteString(body))

    wsResponseMock
  }

  private def createSut(metrics: Metrics, wsClient: WSClient, urls: PdfUrls) =
    new PdfConnector(metrics, wsClient, urls)
  
}

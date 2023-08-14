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

import com.codahale.metrics.Timer
import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ reset => resetMock}
import play.api.http.Status._
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.rti.RtiData
import uk.gov.hmrc.tai.model.tai.TaxYear

class BaseConnectorSpec extends ConnectorBaseSpec {

  lazy val metrics: Metrics = inject[Metrics]
  lazy val httpClient: HttpClient = inject[HttpClient]

  lazy val sut: BaseConnector = new BaseConnector(metrics, httpClient) {
    override def originatorId: String = "testOriginatorId"
  }

  lazy val npsConnector: NpsConnector = inject[NpsConnector]

  lazy val endpoint: String = "/foo"
  lazy val url: String = s"${server.baseUrl()}$endpoint"

  val apiType: APITypes.Value = APITypes.RTIAPI

  val body: String =
    """{
      |"name": "Bob",
      |"age": 24
      |}""".stripMargin

  case class ResponseObject(name: String, age: Int)
  implicit val format: OFormat[ResponseObject] = Json.format[ResponseObject]

  val bodyAsObj: ResponseObject = Json.parse(body).as[ResponseObject]

  val rtiData: RtiData = RtiData(nino.nino, TaxYear(2017), "req123", Nil)
  val rtiDataBody: String = Json.toJson(rtiData).toString()

  val eTagKey: String = "ETag"
  val eTag: Int = 34

  val mockMetrics: Metrics = mock[Metrics]

  def sutWithMockedMetrics: BaseConnector = new BaseConnector(mockMetrics, httpClient) {
    override val originatorId: String = "testOriginatorId"
  }

  override def beforeEach(): Unit = {
    resetMock(mockMetrics)
    super.beforeEach()
  }

  "BaseConnector" must {
    "get the version from the HttpResponse" when {
      "the HttpResponse contains the ETag header" in {

        val response: HttpResponse = HttpResponse(200, "", Map(eTagKey -> Seq(s"$eTag")))

        sut.getVersionFromHttpHeader(response) mustBe eTag
      }
    }

    "get a default version value" when {
      "the HttpResponse does not contain the ETag header" in {
        val response: HttpResponse = HttpResponse(200, "")

        sut.getVersionFromHttpHeader(response) mustBe -1
      }
    }

    "start and stop a transaction timer" when {
      "making a GET request to NPS" in {

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(body)
              .withHeader(eTagKey, s"$eTag"))
        )

        sutWithMockedMetrics.getFromNps(url, apiType, npsConnector.basicNpsHeaders(hc)).futureValue

        verify(mockMetrics).startTimer(any())
        verify(mockTimerContext).stop()
      }

      "making a POST request to NPS" in {

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        server.stubFor(
          post(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(body)
              .withHeader(eTagKey, s"$eTag"))
        )

        sutWithMockedMetrics.postToNps[ResponseObject](url, apiType, bodyAsObj, Seq.empty).futureValue

        verify(mockMetrics).startTimer(any())
        verify(mockTimerContext).stop()
      }

      "making a GET request to DES" in {

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(body)
              .withHeader(eTagKey, s"$eTag"))
        )

        sutWithMockedMetrics.getFromDes(url, apiType, Seq.empty).futureValue

        verify(mockMetrics).startTimer(any())
        verify(mockTimerContext).stop()
      }

      "making a POST request to DES" in {

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        server.stubFor(
          post(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(body)
              .withHeader(eTagKey, s"$eTag"))
        )

        sutWithMockedMetrics.postToDes[ResponseObject](url, apiType, bodyAsObj, Seq.empty).futureValue

        verify(mockMetrics).startTimer(any())
        verify(mockTimerContext).stop()
      }
    }

    "return a success response from NPS" when {
      "it returns a success Http response for GET transactions" in {

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(body)
              .withHeader(eTagKey, s"$eTag"))
        )

        val (res, resEtag) = sut.getFromNps(url, apiType, npsConnector.basicNpsHeaders(hc)).futureValue

        res mustBe bodyAsObj
        resEtag mustBe eTag
      }

      "it returns a success Http response for POST transactions" in {

        server.stubFor(
          post(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(body)
              .withHeader(eTagKey, s"$eTag"))
        )

        val res = sut.postToNps(url, apiType, bodyAsObj, Seq.empty).futureValue

        res.status mustBe OK
        res.json.as[ResponseObject] mustBe bodyAsObj
        res.header(eTagKey) mustBe Some(eTag.toString)
      }
    }

    "return an error response from NPS" when {
      "it returns a not found http response for GET transactions" in {

        val exMessage = "Not found"

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
              .withBody(exMessage)
              .withHeader(eTagKey, s"$eTag"))
        )

        assertConnectorException[NotFoundException](
          sut.getFromNps(url, apiType, npsConnector.basicNpsHeaders(hc)),
          NOT_FOUND,
          exMessage
        )
      }
      "it returns an internal server error http response for GET transactions" in {

        val exMessage = "An error occurred"

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
              .withBody(exMessage)
              .withHeader(eTagKey, s"$eTag"))
        )

        assertConnectorException[InternalServerException](
          sut.getFromNps(url, apiType, npsConnector.basicNpsHeaders(hc)),
          INTERNAL_SERVER_ERROR,
          exMessage
        )
      }
      "it returns an bad request http response for GET transactions" in {

        val exMessage = "Invalid query"

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
              .withBody(exMessage)
              .withHeader(eTagKey, s"$eTag"))
        )

        assertConnectorException[BadRequestException](
          sut.getFromNps(url, apiType, npsConnector.basicNpsHeaders(hc)),
          BAD_REQUEST,
          exMessage
        )
      }

      "it returns an unknown http response for GET transactions" in {

        val exMessage = "There was a conflict"

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(CONFLICT)
              .withBody(exMessage)
              .withHeader(eTagKey, s"$eTag"))
        )

        assertConnectorException[HttpException](
          sut.getFromNps(url, apiType, npsConnector.basicNpsHeaders(hc)),
          CONFLICT,
          exMessage
        )
      }

      "it returns a non success Http response for POST transactions" in {

        val exMessage = "There was a conflict"

        server.stubFor(
          post(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(CONFLICT)
              .withBody(exMessage)
              .withHeader(eTagKey, s"$eTag"))
        )

        assertConnectorException[HttpException](
          sut.postToNps[ResponseObject](url, apiType, bodyAsObj, Seq.empty),
          CONFLICT,
          exMessage
        )
      }
    }

    "return a success response from DES" when {
      "it returns a success Http response for GET transactions" in {

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(body)
              .withHeader(eTagKey, s"$eTag"))
        )

        val (resBody, resEtag) = sut.getFromDes(url, apiType, Seq.empty).futureValue

        resBody mustBe bodyAsObj
        resEtag mustBe eTag
      }

      "it returns a success Http response for POST transactions" in {

        server.stubFor(
          post(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(body)
              .withHeader(eTagKey, s"$eTag"))
        )

        val res = sut.postToDes(url, apiType, bodyAsObj, Seq.empty).futureValue

        res.status mustBe OK
        res.json.as[ResponseObject] mustBe bodyAsObj
        res.header(eTagKey) mustBe Some(eTag.toString)
      }
    }

    "return an error response from DES" when {
      "it returns a not found http response for GET transactions" in {

        val exMessage = "Not found"

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
              .withBody(exMessage)
              .withHeader(eTagKey, s"$eTag"))
        )

        assertConnectorException[NotFoundException](
          sut.getFromDes(url, apiType, Seq.empty),
          NOT_FOUND,
          exMessage
        )
      }

      "it returns an internal server error http response for GET transactions" in {

        val exMessage = "An error occurred"

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
              .withBody(exMessage)
              .withHeader(eTagKey, s"$eTag"))
        )

        assertConnectorException[InternalServerException](
          sut.getFromDes(url, apiType, Seq.empty),
          INTERNAL_SERVER_ERROR,
          exMessage
        )
      }

      "it returns an bad request http response for GET transactions" in {

        val exMessage = "Invalid query"

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
              .withBody(exMessage)
              .withHeader(eTagKey, s"$eTag"))
        )

        assertConnectorException[BadRequestException](
          sut.getFromDes(url, apiType, Seq.empty),
          BAD_REQUEST,
          exMessage
        )
      }

      "it returns an unknown http response for GET transactions" in {

        val exMessage = "Access denied"

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(IM_A_TEAPOT)
              .withBody(exMessage)
              .withHeader(eTagKey, s"$eTag"))
        )

        assertConnectorException[HttpException](
          sut.getFromDes(url, apiType, Seq.empty),
          IM_A_TEAPOT,
          exMessage
        )
      }

      "it returns a non success Http response for POST transactions" in {

        val exMessage = "An error occurred"

        server.stubFor(
          post(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(IM_A_TEAPOT)
              .withBody(exMessage)
              .withHeader(eTagKey, s"$eTag"))
        )

        assertConnectorException[HttpException](
          sut.postToDes(url, apiType, bodyAsObj, Seq.empty),
          IM_A_TEAPOT,
          exMessage
        )
      }
    }

    "throw a NumberFormatException" when {
      "the ETag value is not a valid integer" in {

        val invalidEtag = "Foobar"

        val response: HttpResponse = HttpResponse(200, "", Map(eTagKey -> Seq(invalidEtag)))

        val ex = the[NumberFormatException] thrownBy sut.getVersionFromHttpHeader(response)
        ex.getMessage mustBe s"""For input string: "$invalidEtag""""
      }
    }
  }
}

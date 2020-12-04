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

import com.codahale.metrics.Timer
import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.{verify, when, reset => resetMock}
import play.api.http.Status._
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.nps.{Person, PersonDetails}
import uk.gov.hmrc.tai.model.rti.RtiData
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.language.postfixOps
import scala.util.Random

class BaseConnectorSpec extends ConnectorBaseSpec {

  lazy val auditor: Auditor = inject[Auditor]
  lazy val metrics: Metrics = inject[Metrics]
  lazy val httpClient: HttpClient = inject[HttpClient]

  lazy val sut: BaseConnector = new BaseConnector(auditor, metrics, httpClient) {
    override def originatorId: String = "testOriginatorId"
  }

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

  val fakePersonalDetails: PersonDetails = PersonDetails(
    "4",
    Person(
      Some("TestName"),
      None,
      None,
      None,
      Some("TestTitle"),
      Some("TestHonours"),
      None,
      None,
      Nino(nino.nino),
      Some(true),
      Some(false)))

  val fakePersonalDetailsString: String = Json.toJson(fakePersonalDetails).toString()

  val eTagKey: String = "ETag"
  val eTag: Int = 34

  val mockAuditor: Auditor = mock[Auditor]
  val mockMetrics: Metrics = mock[Metrics]

  def sutWithMockedMetrics: BaseConnector = new BaseConnector(mockAuditor, mockMetrics, httpClient) {
    override val originatorId: String = "testOriginatorId"
  }

  override def beforeEach(): Unit = {
    resetMock(mockMetrics, mockAuditor)
    super.beforeEach()
  }

  "BaseConnector" should {
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

    "add extra headers for NPS" in {
      val headers = sut
        .extraNpsHeaders(HeaderCarrier(), eTag, "testtxID")
        .headers

      headers must contain(eTagKey                -> s"$eTag")
      headers must contain("X-TXID"               -> "testtxID")
      headers must contain("Gov-Uk-Originator-Id" -> "testOriginatorId")

    }

    "add basic headers for NPS" in {
      val headers = sut
        .extraNpsHeaders(HeaderCarrier(), eTag, "testtxID")
        .headers

      headers must contain("Gov-Uk-Originator-Id" -> "testOriginatorId")

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

        Await.result(sutWithMockedMetrics.getFromNps(url, apiType), 5 seconds)

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

        Await.result(sutWithMockedMetrics.postToNps[ResponseObject](url, apiType, bodyAsObj), 5 seconds)

        verify(mockMetrics).startTimer(any())
        verify(mockTimerContext).stop()
      }

      "making a GET request to RTI" in {

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(rtiDataBody)
              .withHeader(eTagKey, s"$eTag"))
        )

        Await.result(sutWithMockedMetrics.getFromRTIWithStatus(url, apiType, nino.nino), 5 seconds)

        verify(mockMetrics).startTimer(any())
        verify(mockTimerContext).stop()
      }

      "making a GET request to Citizen Details" in {

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(fakePersonalDetailsString)
              .withHeader(eTagKey, s"$eTag"))
        )

        Await.result(sutWithMockedMetrics.getPersonDetailsFromCitizenDetails(url, nino, apiType), 5 seconds)

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

        Await.result(sutWithMockedMetrics.getFromDes(url, apiType), 5 seconds)

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

        Await.result(sutWithMockedMetrics.postToDes[ResponseObject](url, apiType, bodyAsObj), 5 seconds)

        verify(mockMetrics).startTimer(any())
        verify(mockTimerContext).stop()
      }
    }

    "create an audit event" when {
      "The RTI response does not match the nino that was requested" in {

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(rtiDataBody)
              .withHeader(eTagKey, s"$eTag"))
        )

        val randomNino = new Generator(new Random).nextNino

        Await.result(sutWithMockedMetrics.getFromRTIWithStatus(url, apiType, randomNino.nino), 5 seconds)

        verify(mockAuditor).sendDataEvent(meq("RTI returned incorrect account"), any())(any())
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

        val (res, resEtag) = Await.result(sut.getFromNps(url, apiType), 5.seconds)

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

        val res = Await.result(sut.postToNps(url, apiType, bodyAsObj), 5.seconds)

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
          sut.getFromNps(url, apiType),
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
          sut.getFromNps(url, apiType),
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
          sut.getFromNps(url, apiType),
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
          sut.getFromNps(url, apiType),
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
          sut.postToNps[ResponseObject](url, apiType, bodyAsObj),
          CONFLICT,
          exMessage
        )
      }
    }

    "return a success response from RTI" when {
      "it returns a success Http response for GET transactions with matching nino data" in {

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(rtiDataBody)
              .withHeader(eTagKey, s"$eTag"))
        )

        val (resData, resStatus) =
          Await.result(sut.getFromRTIWithStatus[ResponseObject](url, apiType, nino.nino), 5.seconds)

        resData mustBe Some(rtiData)
        resStatus.status mustBe OK
      }

      "it returns a success Http response for GET transactions with incorrect nino data" in {

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(rtiDataBody)
              .withHeader(eTagKey, s"$eTag"))
        )

        val randomNino = new Generator(new Random).nextNino

        val (resData, resStatus) =
          Await.result(sut.getFromRTIWithStatus[ResponseObject](url, apiType, randomNino.nino), 5.seconds)

        resData mustBe None
        resStatus.response mustBe "Incorrect RTI Payload"
      }
    }

    "return an error response from RTI" when {
      "it returns a bad request Http response for GET transactions" in {

        val exMessage = "Incorrect query"

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
              .withBody(exMessage)
              .withHeader(eTagKey, s"$eTag"))
        )

        val (resData, resStatus) =
          Await.result(sut.getFromRTIWithStatus[ResponseObject](url, apiType, nino.nino), 5.seconds)

        resData mustBe None
        resStatus.status mustBe BAD_REQUEST
        resStatus.response mustBe exMessage
      }

      "it returns a not found Http response for GET transactions" in {

        val exMessage = "Not found"

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
              .withBody(exMessage)
              .withHeader(eTagKey, s"$eTag"))
        )

        val (resData, resStatus) =
          Await.result(sut.getFromRTIWithStatus[ResponseObject](url, apiType, nino.nino), 5.seconds)

        resData mustBe None
        resStatus.status mustBe NOT_FOUND
        resStatus.response mustBe exMessage
      }

      "it returns a internal server error Http response for GET transactions" in {

        val exMessage = "An error occurred"

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
              .withBody(exMessage)
              .withHeader(eTagKey, s"$eTag"))
        )

        val (resData, resStatus) =
          Await.result(sut.getFromRTIWithStatus[ResponseObject](url, apiType, nino.nino), 5.seconds)

        resData mustBe None
        resStatus.status mustBe INTERNAL_SERVER_ERROR
        resStatus.response mustBe exMessage
      }

      "it returns an unknown Http response for GET transactions" in {

        val exMessage = "Access denied"

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(IM_A_TEAPOT)
              .withBody(exMessage)
              .withHeader(eTagKey, s"$eTag"))
        )

        val (resData, resStatus) =
          Await.result(sut.getFromRTIWithStatus[ResponseObject](url, apiType, nino.nino), 5.seconds)

        resData mustBe None
        resStatus.status mustBe IM_A_TEAPOT
        resStatus.response mustBe exMessage

      }
    }

    "return a success response from citizen details" when {
      "it returns a success Http response for GET transactions" in {

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(fakePersonalDetailsString)
              .withHeader(eTagKey, s"$eTag"))
        )

        val res = Await.result(sut.getPersonDetailsFromCitizenDetails(url, nino, apiType), 5.seconds)

        res mustBe fakePersonalDetails
      }

      "it returns a locked Http response for GET transactions" in {

        val fakePersonalDetails =
          PersonDetails("0", Person(None, None, None, None, None, None, None, None, Nino(nino.nino), Some(true), None))

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(LOCKED)
              .withHeader(eTagKey, s"$eTag"))
        )

        val res = Await.result(sut.getPersonDetailsFromCitizenDetails(url, nino, apiType), 5.seconds)

        res mustBe fakePersonalDetails
      }
    }

    "return an error response from citizen details" when {
      "it returns an unknown http response" in {

        val exMessage = "Access denied"

        server.stubFor(
          get(urlEqualTo(endpoint)).willReturn(
            aResponse()
              .withStatus(IM_A_TEAPOT)
              .withBody(exMessage)
              .withHeader(eTagKey, s"$eTag"))
        )

        assertConnectorException[HttpException](
          sut.getPersonDetailsFromCitizenDetails(url, nino, apiType),
          IM_A_TEAPOT,
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

        val (resBody, resEtag) = Await.result(sut.getFromDes(url, apiType), 5 seconds)

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

        val res = Await.result(sut.postToDes(url, apiType, bodyAsObj), 5.seconds)

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
          sut.getFromDes(url, apiType),
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
          sut.getFromDes(url, apiType),
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
          sut.getFromDes(url, apiType),
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
          sut.getFromDes(url, apiType),
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
          sut.postToDes(url, apiType, bodyAsObj),
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

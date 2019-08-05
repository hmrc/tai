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

import com.codahale.metrics.Timer
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.Status
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.nps.{Person, PersonDetails}
import uk.gov.hmrc.tai.model.rti.{RtiData, RtiStatus}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class BaseConnectorSpec extends PlaySpec with MockitoSugar {

  "BaseConnector" should {
    "get the version from the HttpResponse" when {
      "the HttpResponse contains the ETag header" in {
        val response: HttpResponse = HttpResponse(200, None, Map("ETag" -> Seq("34")))

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        createSUT(mock[Auditor], mockMetrics, mock[HttpClient]).getVersionFromHttpHeader(response) mustBe 34

      }
    }

    "get a default version value" when {
      "the HttpResonse does not contain the ETag header" in {
        val response: HttpResponse = HttpResponse(200)

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        createSUT(mock[Auditor], mockMetrics, mock[HttpClient]).getVersionFromHttpHeader(response) mustBe -1
      }
    }

    "add extra headers for NPS" in {
      val headers = createSUT(mock[Auditor], mock[Metrics], mock[HttpClient])
        .extraNpsHeaders(HeaderCarrier(), 23, "testtxID")
        .headers

      headers must contain("ETag"                 -> "23")
      headers must contain("X-TXID"               -> "testtxID")
      headers must contain("Gov-Uk-Originator-Id" -> "testOriginatorId")

    }

    "add basic headers for NPS" in {
      val headers = createSUT(mock[Auditor], mock[Metrics], mock[HttpClient])
        .extraNpsHeaders(HeaderCarrier(), 23, "testtxID")
        .headers

      headers must contain("Gov-Uk-Originator-Id" -> "testOriginatorId")

    }

    "start and stop a transaction timer" when {
      "making a GET request to NPS" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(SuccesfulGetResponseWithObject))

        val resp = sut.getFromNps("/testURL", APITypes.NpsTaxAccountAPI)(HeaderCarrier(), Json.format[ResponseObject])
        Await.result(resp, 5 seconds)

        verify(mockMetrics, times(1)).startTimer(any())
        verify(mockTimerContext, times(1)).stop()

      }
      "making a POST request to NPS" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.POST[ResponseObject, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(SuccesfulGetResponseWithObject))

        val resp = sut.postToNps[ResponseObject]("/testURL", APITypes.NpsTaxAccountAPI, responseBodyObject)(
          HeaderCarrier(),
          Json.format[ResponseObject])
        Await.result(resp, 5 seconds)

        verify(mockMetrics, times(1)).startTimer(any())
        verify(mockTimerContext, times(1)).stop()

      }
      "making a GET request to RTI" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        val fakeRtiData = RtiData(nino.nino, TaxYear(2017), "req123", Nil)
        val fakeResponse: HttpResponse = HttpResponse(200, Some(Json.toJson(fakeRtiData)))

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(fakeResponse))

        val resp = sut.getFromRTIWithStatus("/testURL", APITypes.NpsTaxAccountAPI, nino.nino)(
          HeaderCarrier(),
          Json.format[ResponseObject])
        Await.result(resp, 5 seconds)

        verify(mockMetrics, times(1)).startTimer(any())
        verify(mockTimerContext, times(1)).stop()
      }
      "making a GET request to Citizen Details" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        val fakePersonalDetails = PersonDetails(
          "4",
          Person(
            Some("Monkey"),
            None,
            None,
            None,
            Some("King"),
            Some("Great Sage equal of heaven"),
            None,
            None,
            Nino(nino.nino),
            Some(true),
            Some(false)))

        val fakeResponse: HttpResponse = HttpResponse(200, Some(Json.toJson(fakePersonalDetails)))

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(fakeResponse))

        val resp = sut.getPersonDetailsFromCitizenDetails("/testURL", Nino(nino.nino), APITypes.NpsTaxAccountAPI)(
          HeaderCarrier(),
          Json.format[PersonDetails])
        Await.result(resp, 5 seconds)

        verify(mockMetrics, times(1)).startTimer(any())
        verify(mockTimerContext, times(1)).stop()
      }
      "making a GET request to DES" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(SuccesfulGetResponseWithObject))

        val resp = sut.getFromDes("/testURL", APITypes.NpsTaxAccountAPI)(HeaderCarrier(), Json.format[ResponseObject])
        Await.result(resp, 5 seconds)

        verify(mockMetrics, times(1)).startTimer(any())
        verify(mockTimerContext, times(1)).stop()
      }
      "making a POST request to DES" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.POST[ResponseObject, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(SuccesfulGetResponseWithObject))

        val resp = sut.postToDes[ResponseObject]("/testURL", APITypes.NpsTaxAccountAPI, responseBodyObject)(
          HeaderCarrier(),
          Json.format[ResponseObject])
        Await.result(resp, 5 seconds)

        verify(mockMetrics, times(1)).startTimer(any())
        verify(mockTimerContext, times(1)).stop()
      }
    }
    "create an audit event" when {
      "The RTI response does not match the nino that was requested!" in {
        val mockAuditor = mock[Auditor]
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mockAuditor, mockMetrics, mockHttpClient)

        val fakeRtiData = RtiData(nino.nino, TaxYear(2017), "req123", Nil)
        val fakeResponse: HttpResponse = HttpResponse(200, Some(Json.toJson(fakeRtiData)))

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(fakeResponse))

        val randomNino = new Generator(new Random).nextNino
        val resp = sut.getFromRTIWithStatus("/testURL", APITypes.NpsTaxAccountAPI, randomNino.nino)(
          HeaderCarrier(),
          Json.format[ResponseObject])
        Await.result(resp, 5 seconds)

        verify(mockAuditor, times(1))
          .sendDataEvent(Matchers.eq("RTI returned incorrect account"), any())(any())
      }
    }
    "return a success response from NPS" when {
      "it returns a success Http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(SuccesfulGetResponseWithObject))

        val resp = sut.getFromNps("/testURL", APITypes.NpsTaxAccountAPI)(HeaderCarrier(), Json.format[ResponseObject])
        val (responseBody, etag) = Await.result(resp, 5 seconds)

        responseBody mustBe responseBodyObject
        etag mustBe 34

      }
      "it returns a success Http response for POST transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.POST[ResponseObject, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(SuccesfulGetResponseWithObject))

        val resp = sut.postToNps[ResponseObject]("/testURL", APITypes.NpsTaxAccountAPI, responseBodyObject)(
          HeaderCarrier(),
          Json.format[ResponseObject])
        val r = Await.result(resp, 5 seconds)

        r.status mustBe 200
        r.json.as[ResponseObject] mustBe responseBodyObject

      }
    }

    "return an error response from NPS" when {
      "it returns a not found http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(NotFoundHttpResponse))

        val resp = sut.getFromNps("/testURL", APITypes.NpsTaxAccountAPI)(HeaderCarrier(), Json.format[ResponseObject])

        val ex = the[NotFoundException] thrownBy Await.result(resp, 5 seconds)

        ex.getMessage mustBe "\"not found\""

      }
      "it returns an internal server error http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(InternalServerErrorHttpResponse))

        val resp = sut.getFromNps("/testURL", APITypes.NpsTaxAccountAPI)(HeaderCarrier(), Json.format[ResponseObject])

        val ex = the[InternalServerException] thrownBy Await.result(resp, 5 seconds)

        ex.getMessage mustBe "\"internal server error\""

      }
      "it returns an bad request http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(BadRequestHttpResponse))

        val resp = sut.getFromNps("/testURL", APITypes.NpsTaxAccountAPI)(HeaderCarrier(), Json.format[ResponseObject])

        val ex = the[BadRequestException] thrownBy Await.result(resp, 5 seconds)

        ex.getMessage mustBe "\"bad request\""

      }
      "it returns an unknown http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(UnknownErrorHttpResponse))

        val resp = sut.getFromNps("/testURL", APITypes.NpsTaxAccountAPI)(HeaderCarrier(), Json.format[ResponseObject])

        val ex = the[HttpException] thrownBy Await.result(resp, 5 seconds)

        ex.getMessage mustBe "\"unknown response\""

      }
      "it returns a non success Http response for POST transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.POST[ResponseObject, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(UnknownErrorHttpResponse))

        val resp = sut.postToNps[ResponseObject]("/testURL", APITypes.NpsTaxAccountAPI, responseBodyObject)(
          HeaderCarrier(),
          Json.format[ResponseObject])

        val ex = the[HttpException] thrownBy Await.result(resp, 5 seconds)
        ex.getMessage mustBe "\"unknown response\""

      }
    }

    "return a success response from RTI" when {
      "it returns a success Http response for GET transactions with matching nino data" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        val fakeRtiData = RtiData(nino.nino, TaxYear(2017), "req123", Nil)
        val fakeResponse: HttpResponse = HttpResponse(200, Some(Json.toJson(fakeRtiData)))

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(fakeResponse))

        val resp = sut.getFromRTIWithStatus("/testURL", APITypes.NpsTaxAccountAPI, nino.nino)(
          HeaderCarrier(),
          Json.format[ResponseObject])
        val (rtiData, rtiStatus) = Await.result(resp, 5 seconds)

        rtiStatus.response mustBe "Success"
        rtiData.get mustBe fakeRtiData

      }
      "it returns a success Http response for GET transactions with incorrect nino data" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        val fakeRtiData = RtiData(nino.nino, TaxYear(2017), "req123", Nil)
        val fakeResponse: HttpResponse = HttpResponse(200, Some(Json.toJson(fakeRtiData)))

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(fakeResponse))
        val randomNino = new Generator(new Random).nextNino
        val resp = sut.getFromRTIWithStatus("/testURL", APITypes.NpsTaxAccountAPI, randomNino.nino)(
          HeaderCarrier(),
          Json.format[ResponseObject])
        val (rtiData, rtiStatus: RtiStatus) = Await.result(resp, 5 seconds)

        rtiStatus.response mustBe "Incorrect RTI Payload"
        rtiData mustBe empty

      }
    }
    "return an error response from RTI" when {
      "it returns a bad request Http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(BadRequestHttpResponse))

        val resp = sut.getFromRTIWithStatus("/testURL", APITypes.NpsTaxAccountAPI, nino.nino)(
          HeaderCarrier(),
          Json.format[ResponseObject])
        val (rtiData, rtiStatus: RtiStatus) = Await.result(resp, 5 seconds)

        rtiStatus.response mustBe "\"bad request\""
        rtiData mustBe empty

      }
      "it returns a not found Http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(NotFoundHttpResponse))

        val resp = sut.getFromRTIWithStatus("/testURL", APITypes.NpsTaxAccountAPI, nino.nino)(
          HeaderCarrier(),
          Json.format[ResponseObject])
        val (rtiData, rtiStatus: RtiStatus) = Await.result(resp, 5 seconds)

        rtiStatus.response mustBe "\"not found\""
        rtiData mustBe empty

      }
      "it returns a internal server error Http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(InternalServerErrorHttpResponse))

        val resp = sut.getFromRTIWithStatus("/testURL", APITypes.NpsTaxAccountAPI, nino.nino)(
          HeaderCarrier(),
          Json.format[ResponseObject])
        val (rtiData, rtiStatus: RtiStatus) = Await.result(resp, 5 seconds)

        rtiStatus.response mustBe "\"internal server error\""
        rtiData mustBe empty

      }
      "it returns an unknown Http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(UnknownErrorHttpResponse))

        val resp = sut.getFromRTIWithStatus("/testURL", APITypes.NpsTaxAccountAPI, nino.nino)(
          HeaderCarrier(),
          Json.format[ResponseObject])
        val (rtiData, rtiStatus: RtiStatus) = Await.result(resp, 5 seconds)

        rtiStatus.response mustBe "\"unknown response\""
        rtiData mustBe empty

      }
    }

    "return a success response from citizen details" when {
      "it returns a success Http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        val fakePersonalDetails = PersonDetails(
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

        val fakeResponse: HttpResponse = HttpResponse(200, Some(Json.toJson(fakePersonalDetails)))

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(fakeResponse))

        val resp = sut.getPersonDetailsFromCitizenDetails("/testURL", Nino(nino.nino), APITypes.NpsTaxAccountAPI)(
          HeaderCarrier(),
          Json.format[PersonDetails])
        val personalDetails = Await.result(resp, 5 seconds)

        personalDetails mustBe fakePersonalDetails
      }
      "it returns a locked Http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        val fakePersonalDetails =
          PersonDetails("0", Person(None, None, None, None, None, None, None, None, Nino(nino.nino), Some(true), None))

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(HttpResponse(Status.LOCKED, None)))

        val resp = sut.getPersonDetailsFromCitizenDetails("/testURL", Nino(nino.nino), APITypes.NpsTaxAccountAPI)(
          HeaderCarrier(),
          Json.format[PersonDetails])
        val personalDetails = Await.result(resp, 5 seconds)

        personalDetails mustBe fakePersonalDetails
      }
    }
    "return an error response from citizen details" when {
      "it returns an unknown http response" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(UnknownErrorHttpResponse))

        val resp = sut.getPersonDetailsFromCitizenDetails("/testURL", Nino(nino.nino), APITypes.NpsTaxAccountAPI)(
          HeaderCarrier(),
          Json.format[PersonDetails])

        val ex = the[HttpException] thrownBy Await.result(resp, 5 seconds)
        ex.getMessage mustBe "\"unknown response\""
      }
    }
    "return a success response from DES" when {
      "it returns a success Http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(SuccesfulGetResponseWithObject))

        val resp = sut.getFromDes("/testURL", APITypes.NpsTaxAccountAPI)(HeaderCarrier(), Json.format[ResponseObject])
        val (responseBody, etag) = Await.result(resp, 5 seconds)

        responseBody mustBe responseBodyObject
        etag mustBe 34

      }
      "it returns a success Http response for POST transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.POST[ResponseObject, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(SuccesfulGetResponseWithObject))

        val resp = Await.result(
          sut.postToDes[ResponseObject]("/testURL", APITypes.NpsTaxAccountAPI, responseBodyObject)(
            HeaderCarrier(),
            Json.format[ResponseObject]),
          5 seconds)

        resp.status mustBe 200
        resp.json.as[ResponseObject] mustBe responseBodyObject

      }
    }
    "return an error response from DES" when {
      "it returns a not found http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(NotFoundHttpResponse))

        val resp = sut.getFromDes("/testURL", APITypes.NpsTaxAccountAPI)(HeaderCarrier(), Json.format[ResponseObject])

        val ex = the[NotFoundException] thrownBy Await.result(resp, 5 seconds)

        ex.getMessage mustBe "\"not found\""

      }
      "it returns an internal server error http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(InternalServerErrorHttpResponse))

        val resp = sut.getFromDes("/testURL", APITypes.NpsTaxAccountAPI)(HeaderCarrier(), Json.format[ResponseObject])

        val ex = the[InternalServerException] thrownBy Await.result(resp, 5 seconds)

        ex.getMessage mustBe "\"internal server error\""

      }
      "it returns an bad request http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(BadRequestHttpResponse))

        val resp = sut.getFromDes("/testURL", APITypes.NpsTaxAccountAPI)(HeaderCarrier(), Json.format[ResponseObject])

        val ex = the[BadRequestException] thrownBy Await.result(resp, 5 seconds)

        ex.getMessage mustBe "\"bad request\""

      }
      "it returns an unknown http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(UnknownErrorHttpResponse))

        val resp = sut.getFromDes("/testURL", APITypes.NpsTaxAccountAPI)(HeaderCarrier(), Json.format[ResponseObject])

        val ex = the[HttpException] thrownBy Await.result(resp, 5 seconds)

        ex.getMessage mustBe "\"unknown response\""

      }
      "it returns a non success Http response for POST transactions" in {
        val mockHttpClient = mock[HttpClient]

        val mockTimerContext = mock[Timer.Context]
        when(mockTimerContext.stop())
          .thenReturn(123L)
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mock[Auditor], mockMetrics, mockHttpClient)

        when(mockHttpClient.POST[ResponseObject, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(UnknownErrorHttpResponse))

        val resp = sut.postToDes[ResponseObject]("/testURL", APITypes.NpsTaxAccountAPI, responseBodyObject)(
          HeaderCarrier(),
          Json.format[ResponseObject])

        val ex = the[HttpException] thrownBy Await.result(resp, 5 seconds)
        ex.getMessage mustBe "\"unknown response\""

      }
    }

    "throw a NumberFormatException" when {
      "the ETag value is not a valid integer" in {
        val mockHttpClient = mock[HttpClient]
        val sut = createSUT(mock[Auditor], mock[Metrics], mockHttpClient)
        val response: HttpResponse = HttpResponse(200, None, Map("ETag" -> Seq("BLOM")))

        val ex = the[NumberFormatException] thrownBy sut.getVersionFromHttpHeader(response)
        ex.getMessage mustBe "For input string: \"BLOM\""
      }
    }
  }

  private case class ResponseObject(name: String, age: Int)
  private implicit val responseObjectFormat = Json.format[ResponseObject]
  private val responseBodyObject = ResponseObject("ttt", 24)

  private val SuccesfulGetResponseWithObject: HttpResponse =
    HttpResponse(200, Some(Json.toJson(responseBodyObject)), Map("ETag"                            -> Seq("34")))
  private val BadRequestHttpResponse = HttpResponse(400, Some(JsString("bad request")), Map("ETag" -> Seq("34")))
  private val UnknownErrorHttpResponse: HttpResponse =
    HttpResponse(418, Some(JsString("unknown response")), Map("ETag" -> Seq("34")))
  private val NotFoundHttpResponse: HttpResponse =
    HttpResponse(404, Some(JsString("not found")), Map("ETag" -> Seq("34")))
  private val InternalServerErrorHttpResponse: HttpResponse =
    HttpResponse(500, Some(JsString("internal server error")), Map("ETag" -> Seq("34")))
  private val nino: Nino = new Generator(new Random).nextNino

  private def createSUT(auditor: Auditor, metrics: Metrics, httpClient: HttpClient) =
    new BaseConnector(auditor, metrics, httpClient) {

      override val originatorId: String = "testOriginatorId"
    }

}

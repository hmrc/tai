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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.http.Status._
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.nps._
import uk.gov.hmrc.tai.model.{ETag, TaiRoot}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class CitizenDetailsConnectorSpec extends ConnectorBaseSpec with ScalaFutures with IntegrationPatience {

  "Get data from citizen-details service" must {
    "return person information when requesting " in {
      val jsonData = Json.toJson(
        PersonDetails(
          "100",
          Person(
            Some("FName"),
            None,
            Some("LName"),
            None,
            Some("Mr"),
            None,
            None,
            None,
            Nino(nino.nino),
            Some(false),
            Some(false))))

      val mockHttpClient = mock[HttpClient]
      when(mockHttpClient.GET[HttpResponse](any())(any(), any(), any()))
        .thenReturn(Future.successful(
          HttpResponse(responseStatus = 200, responseString = Some("Success"), responseJson = Some(jsonData))))

      val mockTimerContext = mock[Timer.Context]
      val mockMetrics = mock[Metrics]
      when(mockMetrics.startTimer(any()))
        .thenReturn(mockTimerContext)

      val sut = createSUT(mockMetrics, mockHttpClient, mock[Auditor], mock[CitizenDetailsUrls])
      val personDetails =
        Await.result(sut.getPersonDetails(Nino(nino.nino))(HeaderCarrier(), PersonDetails.formats), 5 seconds)

      personDetails.person.nino.value mustBe nino.nino
      personDetails.etag mustBe "100"
      personDetails.toTaiRoot mustBe TaiRoot(
        nino.nino,
        100,
        "Mr",
        "FName",
        None,
        "LName",
        "FName LName",
        manualCorrespondenceInd = false,
        Some(false))

    }

    "return Record Locked when requesting  " in {
      val jsonData = Json.toJson(
        PersonDetails(
          "0",
          Person(Some(""), None, Some(""), None, Some(""), None, None, None, Nino(nino.nino), Some(true), Some(false))))

      val mockHttpClient = mock[HttpClient]
      when(mockHttpClient.GET[HttpResponse](any())(any(), any(), any()))
        .thenReturn(Future.successful(
          HttpResponse(responseStatus = 423, responseString = Some("Record Locked"), responseJson = Some(jsonData))))

      val mockTimerContext = mock[Timer.Context]
      val mockMetrics = mock[Metrics]
      when(mockMetrics.startTimer(any()))
        .thenReturn(mockTimerContext)

      val sut = createSUT(mockMetrics, mockHttpClient, mock[Auditor], mock[CitizenDetailsUrls])
      val personDetails =
        Await.result(sut.getPersonDetails(Nino(nino.nino))(HeaderCarrier(), PersonDetails.formats), 5 seconds)

      personDetails.person.nino.value mustBe nino.nino
      personDetails.etag mustBe "0"
      personDetails.toTaiRoot mustBe TaiRoot(nino.nino, 0, "", "", None, "", " ", manualCorrespondenceInd = true, None)
    }

    "return Internal server error when requesting " in {
      val mockHttpClient = mock[HttpClient]
      when(mockHttpClient.GET[HttpResponse](any())(any(), any(), any()))
        .thenReturn(Future.successful(
          HttpResponse(responseStatus = 500, responseString = Some("Internal Server Error"), responseJson = None)))

      val mockTimerContext = mock[Timer.Context]
      val mockMetrics = mock[Metrics]
      when(mockMetrics.startTimer(any()))
        .thenReturn(mockTimerContext)

      val sut = createSUT(mockMetrics, mockHttpClient, mock[Auditor], mock[CitizenDetailsUrls])
      val thrown = the[HttpException] thrownBy Await
        .result(sut.getPersonDetails(Nino(nino.nino))(HeaderCarrier(), PersonDetails.formats), 5 seconds)

      thrown.getMessage mustBe "Internal Server Error"
    }

    "return deceased indicator as false if no value is returned form citizen details" in {
      val jsonData = Json.toJson(
        PersonDetails(
          "100",
          Person(
            Some("FName"),
            None,
            Some("LName"),
            None,
            Some("Mr"),
            None,
            None,
            None,
            Nino(nino.nino),
            None,
            Some(false))))

      val mockHttpClient = mock[HttpClient]
      when(mockHttpClient.GET[HttpResponse](any())(any(), any(), any()))
        .thenReturn(Future.successful(
          HttpResponse(responseStatus = 200, responseString = Some("Success"), responseJson = Some(jsonData))))

      val mockTimerContext = mock[Timer.Context]
      val mockMetrics = mock[Metrics]
      when(mockMetrics.startTimer(any()))
        .thenReturn(mockTimerContext)

      val sut = createSUT(mockMetrics, mockHttpClient, mock[Auditor], mock[CitizenDetailsUrls])
      val personDetails =
        Await.result(sut.getPersonDetails(Nino(nino.nino))(HeaderCarrier(), PersonDetails.formats), 5 seconds)

      personDetails.person.nino.value mustBe nino.nino
      personDetails.etag mustBe "100"
      personDetails.toTaiRoot mustBe TaiRoot(
        nino.nino,
        100,
        "Mr",
        "FName",
        None,
        "LName",
        "FName LName",
        manualCorrespondenceInd = false,
        Some(false))
    }

    "return deceased indicator as true" in {
      val jsonData = Json.toJson(
        PersonDetails(
          "100",
          Person(
            Some("FName"),
            None,
            Some("LName"),
            None,
            Some("Mr"),
            None,
            None,
            None,
            Nino(nino.nino),
            Some(true),
            Some(false))))

      val mockHttpClient = mock[HttpClient]
      when(mockHttpClient.GET[HttpResponse](any())(any(), any(), any()))
        .thenReturn(Future.successful(
          HttpResponse(responseStatus = 200, responseString = Some("Success"), responseJson = Some(jsonData))))

      val mockTimerContext = mock[Timer.Context]
      val mockMetrics = mock[Metrics]
      when(mockMetrics.startTimer(any()))
        .thenReturn(mockTimerContext)

      val sut = createSUT(mockMetrics, mockHttpClient, mock[Auditor], mock[CitizenDetailsUrls])
      val personDetails =
        Await.result(sut.getPersonDetails(Nino(nino.nino))(HeaderCarrier(), PersonDetails.formats), 5 seconds)

      personDetails.person.nino.value mustBe nino.nino
      personDetails.etag mustBe "100"
      personDetails.toTaiRoot mustBe TaiRoot(
        nino.nino,
        100,
        "Mr",
        "FName",
        None,
        "LName",
        "FName LName",
        manualCorrespondenceInd = true,
        Some(false))
    }
  }

  "getEtag" must {
    "return an etag on success" in {
      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$nino/etag"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(etagJson.toString)
          )
      )

      citizenDetailsConnector.getEtag(nino).futureValue mustBe Some(ETag(etag))
    }

    "return a None for bad json" in {
      val badJson: JsValue = Json.parse(s"""
                                           |{
                                           |   "not an etag":"$etag"
                                           |}
    """.stripMargin)

      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$nino/etag"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(badJson.toString)
          )
      )

      citizenDetailsConnector.getEtag(nino).futureValue mustBe None
    }

    "return None on an unrecoverable error, possibly bad data received from the upstream API" in {
      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$nino/etag"))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )

      citizenDetailsConnector.getEtag(nino).futureValue mustBe None
    }

    "return None when record not found" in {
      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$nino/etag"))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )

      citizenDetailsConnector.getEtag(nino).futureValue mustBe None
    }

    "return None when record was hidden, due to manual correspondence indicator flag being set" in {
      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$nino/etag"))
          .willReturn(
            aResponse()
              .withStatus(LOCKED)
          )
      )

      citizenDetailsConnector.getEtag(nino).futureValue mustBe None
    }
  }

  val etag = "123"
  val etagJson: JsValue = Json.parse(s"""
                                        |{
                                        |   "etag":"$etag"
                                        |}
    """.stripMargin)

  private lazy val citizenDetailsConnector = app.injector.instanceOf[CitizenDetailsConnector]

  def createSUT(metrics: Metrics, httpClient: HttpClient, audit: Auditor, urls: CitizenDetailsUrls) =
    new CitizenDetailsConnector(metrics, httpClient, audit, urls)
}

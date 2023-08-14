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

import java.time.LocalDate
import com.codahale.metrics.Timer
import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.ArgumentMatchers.{any, eq => meq}
import play.api.http.Status._
import play.api.libs.json._
import uk.gov.hmrc.http._
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.ETag
import uk.gov.hmrc.tai.model.domain.Address
import uk.gov.hmrc.tai.model.enums.APITypes

import scala.concurrent.Future

class CitizenDetailsConnectorSpec extends ConnectorBaseSpec {

  lazy val sut: CitizenDetailsConnector = inject[CitizenDetailsConnector]

  val baseUrl: String = s"/citizen-details/${nino.nino}"
  val designatoryDetailsUrl: String = s"$baseUrl/designatory-details"
  val eTagUrl: String = s"$baseUrl/etag"

  val etag: String = "123"
  val etagJson: JsValue = Json.parse(s"""
                                        |{
                                        |   "etag":"$etag"
                                        |}
    """.stripMargin)

  val jsonPayload = Json.parse(s"""
                                  |{
                                  | "etag":"1",
                                  | "person":{
                                  |   "firstName":"FName",
                                  |   "lastName":"LName",
                                  |   "title":"Mr",
                                  |   "sex":"M",
                                  |   "dateOfBirth":"1975-09-15",
                                  |   "nino":"${nino.nino}",
                                  |   "deceased":false
                                  | },
                                  | "address":{
                                  |   "line1":"1 Test Line",
                                  |   "line2":"Test Line 2",
                                  |   "postcode":"TEST",
                                  |   "startDate":"2013-11-28",
                                  |   "country":"GREAT BRITAIN",
                                  |   "type":"Residential"
                                  | }
                                  |}""".stripMargin).toString

  "getPerson" must {
    import uk.gov.hmrc.tai.model.domain.Person

    "return a person's information from citizen details" in {
      server.stubFor(
        get(urlEqualTo(designatoryDetailsUrl)).willReturn(
          ok().withBody(jsonPayload)
        )
      )

      val result = sut.getPerson(nino).futureValue
      result mustBe Person(
        nino,
        "FName",
        "LName",
        Some(LocalDate.parse("1975-09-15")),
        Address(Some("1 Test Line"), Some("Test Line 2"), None, Some("TEST"), Some("GREAT BRITAIN")),
        false,
        false)
    }

    "return a locked Person when designatory details returns a LOCKED status code" in {
      server.stubFor(
        get(urlEqualTo(designatoryDetailsUrl)).willReturn(
          aResponse().withStatus(LOCKED).withBody("Locked user")
        )
      )

      val result = sut.getPerson(nino).futureValue
      result mustBe Person.createLockedUser(nino)
    }

    "throws an internal server error on anything else" in {
      val exceptionMessage = "An exception occured"

      server.stubFor(
        get(urlEqualTo(designatoryDetailsUrl)).willReturn(
          aResponse().withStatus(INTERNAL_SERVER_ERROR).withBody(exceptionMessage)
        )
      )

      assertConnectorException[HttpException](
        sut.getPerson(nino),
        INTERNAL_SERVER_ERROR,
        exceptionMessage
      )
    }

    "handles missing fields" in {
      val jsonWithMissingFields = Json.obj(
        "etag" -> "000",
        "person" -> Json.obj(
          "nino"    -> nino.nino,
          "address" -> Json.obj()
        )
      )

      server.stubFor(
        get(urlEqualTo(designatoryDetailsUrl)).willReturn(
          ok().withBody(jsonWithMissingFields.toString)
        )
      )

      val result = sut.getPerson(nino).futureValue
      result mustBe Person(nino, "", "", None, Address.emptyAddress)
    }
  }

  "getEtag" must {
    "return an etag on success" in {
      server.stubFor(
        get(urlEqualTo(eTagUrl))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(etagJson.toString)
          )
      )

      sut.getEtag(nino).futureValue mustBe Some(ETag(etag))
    }

    "return a None for bad json" in {
      val badJson: JsValue = Json.parse(s"""
                                           |{
                                           |   "not an etag":"$etag"
                                           |}
    """.stripMargin)

      server.stubFor(
        get(urlEqualTo(eTagUrl))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(badJson.toString)
          )
      )

      sut.getEtag(nino).futureValue mustBe None
    }

    List(
      BAD_REQUEST,
      NOT_FOUND,
      IM_A_TEAPOT
    ).foreach { httpResponse =>
      s"return a None when a $httpResponse occurs" in {

        server.stubFor(
          get(urlEqualTo(eTagUrl))
            .willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
            )
        )

        sut.getEtag(nino).futureValue mustBe None
      }
    }

    "return None on an unrecoverable error, possibly bad data received from the upstream API" in {
      server.stubFor(
        get(urlEqualTo(eTagUrl))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )

      sut.getEtag(nino).futureValue mustBe None
    }

    "return None when record not found" in {
      server.stubFor(
        get(urlEqualTo(eTagUrl))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )

      sut.getEtag(nino).futureValue mustBe None
    }

    "return None when record was hidden, due to manual correspondence indicator flag being set" in {
      server.stubFor(
        get(urlEqualTo(eTagUrl))
          .willReturn(
            aResponse()
              .withStatus(LOCKED)
          )
      )

      sut.getEtag(nino).futureValue mustBe None
    }
  }

  "metrics" must {
    "increment the success counter on an OK response" in {
      val metrics = mock[Metrics]

      val mockTimerContext = mock[Timer.Context]
      when(metrics.startTimer(any()))
        .thenReturn(mockTimerContext)

      val httpClient = mock[HttpClient]

      val connector = new CitizenDetailsConnector(metrics, httpClient, inject[CitizenDetailsUrls])

      when(httpClient.GET[HttpResponse](any(), any(), any())(any(), any(), any())).thenReturn(
        Future.successful(HttpResponse(OK, jsonPayload))
      )

      connector.getPerson(nino).futureValue

      verify(metrics).incrementSuccessCounter(meq(APITypes.NpsPersonAPI))
    }

    "increment the success counter on an LOCKED response" in {
      val metrics = mock[Metrics]

      val mockTimerContext = mock[Timer.Context]
      when(metrics.startTimer(any()))
        .thenReturn(mockTimerContext)

      val httpClient = mock[HttpClient]

      val connector = new CitizenDetailsConnector(metrics, httpClient, inject[CitizenDetailsUrls])

      when(httpClient.GET[HttpResponse](any(), any(), any())(any(), any(), any())).thenReturn(
        Future.successful(HttpResponse(LOCKED, "a locked user"))
      )

      connector.getPerson(nino).futureValue

      verify(metrics).incrementSuccessCounter(meq(APITypes.NpsPersonAPI))
    }

    "increment the failure counter on an INTERNAL_SERVER_ERROR response" in {
      val metrics = mock[Metrics]

      val mockTimerContext = mock[Timer.Context]
      when(metrics.startTimer(any()))
        .thenReturn(mockTimerContext)

      val httpClient = mock[HttpClient]

      val connector = new CitizenDetailsConnector(metrics, httpClient, inject[CitizenDetailsUrls])

      when(httpClient.GET[HttpResponse](any(), any(), any())(any(), any(), any())).thenReturn(
        Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "an error occured"))
      )

      connector.getPerson(nino).failed.futureValue

      verify(metrics).incrementFailedCounter(meq(APITypes.NpsPersonAPI))
    }
  }
}

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

  lazy val sut: CitizenDetailsConnector = inject[CitizenDetailsConnector]

  val baseUrl: String = s"/citizen-details/${nino.nino}"
  val designatoryDetailsUrl: String = s"$baseUrl/designatory-details"
  val eTagUrl: String = s"$baseUrl/etag"

  val data: PersonDetails = PersonDetails(
    "100",
    Person(Some("FName"), None, Some("LName"), None, Some("Mr"), None, None, None, nino, Some(false), Some(false)))

  val jsonData: String = Json.toJson(data).toString()

  val etag: String = "123"
  val etagJson: JsValue = Json.parse(s"""
                                        |{
                                        |   "etag":"$etag"
                                        |}
    """.stripMargin)

  "Get data from citizen-details service" must {
    "return person information when requesting " in {

      server.stubFor(
        get(urlEqualTo(designatoryDetailsUrl)).willReturn(aResponse().withStatus(OK).withBody(jsonData))
      )

      val personDetails = Await.result(sut.getPersonDetails(nino), 5 seconds)

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

    "return Record Locked when requesting designatory details" in {

      server.stubFor(
        get(urlEqualTo(designatoryDetailsUrl)).willReturn(aResponse().withStatus(LOCKED).withBody("Record locked"))
      )

      val personDetails =
        Await.result(sut.getPersonDetails(nino)(HeaderCarrier(), PersonDetails.formats), 5 seconds)

      personDetails.person.nino.value mustBe nino.nino
      personDetails.etag mustBe "0"
      personDetails.toTaiRoot mustBe TaiRoot(nino.nino, 0, "", "", None, "", " ", manualCorrespondenceInd = true, None)
    }

    "return Internal server error when requesting " in {

      val exMessage = "An error occurred"

      server.stubFor(
        get(urlEqualTo(designatoryDetailsUrl))
          .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR).withBody(exMessage))
      )

      val thrown = the[HttpException] thrownBy Await
        .result(sut.getPersonDetails(nino), 5 seconds)

      thrown.responseCode mustBe INTERNAL_SERVER_ERROR
      thrown.getMessage mustBe exMessage
    }

    "return deceased indicator as false if no value is returned from citizen details" in {

      val body = Json
        .toJson(
          data.copy(
            person = data.person.copy(manualCorrespondenceInd = None)
          ))
        .toString()

      server.stubFor(
        get(urlEqualTo(designatoryDetailsUrl)).willReturn(aResponse().withStatus(OK).withBody(body))
      )

      val personDetails =
        Await.result(sut.getPersonDetails(nino), 5 seconds)

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

      val body = Json
        .toJson(
          data.copy(
            person = data.person.copy(deceased = Some(true))
          ))
        .toString()

      server.stubFor(
        get(urlEqualTo(designatoryDetailsUrl)).willReturn(aResponse().withStatus(OK).withBody(body))
      )

      val personDetails =
        Await.result(sut.getPersonDetails(nino), 5 seconds)

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
        Some(true))
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

      sut.getEtag(nino).futureValue mustBe Some(ETag(etag))
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

      sut.getEtag(nino).futureValue mustBe None
    }

    "return None on an unrecoverable error, possibly bad data received from the upstream API" in {
      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$nino/etag"))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )

      sut.getEtag(nino).futureValue mustBe None
    }

    "return None when record not found" in {
      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$nino/etag"))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )

      sut.getEtag(nino).futureValue mustBe None
    }

    "return None when record was hidden, due to manual correspondence indicator flag being set" in {
      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$nino/etag"))
          .willReturn(
            aResponse()
              .withStatus(LOCKED)
          )
      )

      sut.getEtag(nino).futureValue mustBe None
    }
  }
}

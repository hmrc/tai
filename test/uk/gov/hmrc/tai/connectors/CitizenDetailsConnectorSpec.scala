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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.joda.time.LocalDate
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.http.Status._
import play.api.libs.json._
import uk.gov.hmrc.http._
import uk.gov.hmrc.tai.model.domain.Address
import uk.gov.hmrc.tai.model.nps._
import uk.gov.hmrc.tai.model.{ETag, TaiRoot}

import scala.concurrent.Await
import scala.concurrent.duration._
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

  "getPerson" must {
    "return person information when requesting" in {

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
                                      |}""".stripMargin).toString()

      server.stubFor(
        get(urlEqualTo(designatoryDetailsUrl)).willReturn(aResponse().withStatus(OK).withBody(jsonPayload))
      )

      val person = sut.getPerson(nino).futureValue

      import uk.gov.hmrc.tai.model.domain.Person
      person mustBe Person(
        nino,
        "FName",
        "LName",
        Some(LocalDate.parse("1975-09-15")),
        Address("", "", "", "", ""),
        false,
        false)
    }

    "missing fields" in {

      val jsonWithMissingFields = Json
        .obj(
          "etag" -> "000",
          "person" -> Json.obj(
            "nino"    -> nino.nino,
            "address" -> Json.obj()
          )
        )
        .toString

      server.stubFor(
        get(urlEqualTo(designatoryDetailsUrl)).willReturn(aResponse().withStatus(OK).withBody(jsonWithMissingFields))
      )

      val person = sut.getPerson(nino).futureValue

      import uk.gov.hmrc.tai.model.domain.Person
      val expectedPersonFromPartialJson = Person(nino, "", "", None, Address("", "", "", "", ""), false, false)
      person mustBe expectedPersonFromPartialJson
    }

    "marks the deceased indicator as true if the user is deceased" in {

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
                                      |   "deceased":true
                                      | },
                                      | "address":{
                                      |   "line1":"1 Test Line",
                                      |   "line2":"Test Line 2",
                                      |   "postcode":"TEST",
                                      |   "startDate":"2013-11-28",
                                      |   "country":"GREAT BRITAIN",
                                      |   "type":"Residential"
                                      | }
                                      |}""".stripMargin).toString()

      server.stubFor(
        get(urlEqualTo(designatoryDetailsUrl)).willReturn(aResponse().withStatus(OK).withBody(jsonPayload))
      )

      val person = sut.getPerson(nino).futureValue

      import uk.gov.hmrc.tai.model.domain.Person
      person mustBe Person(
        nino,
        "FName",
        "LName",
        Some(LocalDate.parse("1975-09-15")),
        Address("", "", "", "", ""),
        true,
        false)
    }

    "return an empty user marked as locked when there is a Locked response" in {

      server.stubFor(
        get(urlEqualTo(designatoryDetailsUrl))
          .willReturn(aResponse().withStatus(LOCKED).withBody("User opted for manual correspondence"))
      )

      val person = sut.getPerson(nino).futureValue
      import uk.gov.hmrc.tai.model.domain.Person
      person mustBe Person.createLockedUser(nino)
    }

    "throws an internal server error on anything else " in {

      val exMessage = "An error occurred"

      server.stubFor(
        get(urlEqualTo(designatoryDetailsUrl))
          .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR).withBody(exMessage))
      )

      assertConnectorException[HttpException](
        sut.getPerson(nino),
        INTERNAL_SERVER_ERROR,
        exMessage
      )
    }
  }

  "getPersonDetails" must {
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

      assertConnectorException[HttpException](
        sut.getPersonDetails(nino),
        INTERNAL_SERVER_ERROR,
        exMessage
      )
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
}

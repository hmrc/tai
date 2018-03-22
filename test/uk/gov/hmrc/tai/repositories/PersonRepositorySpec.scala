/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.tai.repositories

import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.tai.connectors.{CacheConnector, CitizenDetailsUrls, HttpHandler}
import uk.gov.hmrc.tai.controllers.FakeTaiPlayApplication
import uk.gov.hmrc.tai.model.domain.{Address, Person}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class PersonRepositorySpec extends PlaySpec
  with MockitoSugar
  with FakeTaiPlayApplication {

  "PersonDetailsConnector" should {

    "return the person details from the person API" when {

      "storage retrieval returns None" in {
        val person = Person(Nino(nino.nino), "firstName1", "lastName1", Some(dateOfBirth), address, false)

        val mockCacheConnector = mock[CacheConnector]
        val mockCitizenDetailsUrls = mock[CitizenDetailsUrls]
        val mockHttpHandler = mock[HttpHandler]

        val SUT = spy(createSUT(mockCacheConnector, mockCitizenDetailsUrls, mockHttpHandler))

        doReturn(Future.successful(None)).when(SUT).getPersonFromStorage(any())(any())
        doReturn(Future.successful(person)).when(SUT).getPersonFromAPI(any())(any())

        val responseFuture = SUT.getPerson(nino)
        val result = Await.result(responseFuture, 5 seconds)

        result mustBe person

        verify(SUT, times(1))
          .getPersonFromStorage(Matchers.eq(nino))(any())
        verify(SUT, times(1))
          .getPersonFromAPI(Matchers.eq(nino))(any())
      }
    }

    "return the person details from storage" when {

      "storage retrieval returns person" in {

        val person = Person(Nino(nino.nino), "firstName1", "lastName1", Some(dateOfBirth), address, false)

        val mockCacheConnector = mock[CacheConnector]
        val mockCitizenDetailsUrls = mock[CitizenDetailsUrls]
        val mockHttpHandler = mock[HttpHandler]

        val SUT = spy(createSUT(mockCacheConnector, mockCitizenDetailsUrls, mockHttpHandler))

        doReturn(Future.successful(Some(person))).when(SUT).getPersonFromStorage(any())(any())

        val responseFuture = SUT.getPerson(Nino(nino.nino))
        val result = Await.result(responseFuture, 5 seconds)

        result mustBe person

        verify(SUT, times(1))
          .getPersonFromStorage(Matchers.eq(Nino(nino.nino)))(any())
        verify(SUT, never())
          .getPersonFromAPI(Matchers.eq(Nino(nino.nino)))(any())
      }
    }

    "return the person details" when {

      "the person API sends json with missing fields" in {

        val jsonWithMissingFields = Json.obj(
          "etag" -> "000",
          "person" -> Json.obj(
            "nino" -> nino.nino,
            "address" -> Json.obj()
          )
        )

        val deceased: Boolean = false

        val address = Address("", "", "", "", "")
        val person = Person(Nino(nino.nino), "", "", None, address, deceased)

        val mockCacheConnector = mock[CacheConnector]
        val mockCitizenDetailsUrls = mock[CitizenDetailsUrls]
        val mockHttpHandler = mock[HttpHandler]

        val SUT = spy(createSUT(mockCacheConnector, mockCitizenDetailsUrls, mockHttpHandler))

        when(mockHttpHandler.getFromApi(any(), any())(any()))
          .thenReturn(Future.successful(jsonWithMissingFields))
        when(mockCacheConnector.createOrUpdate[Person](any(), any(), any())(any()))
          .thenReturn(Future.successful(person))

        val responseFuture = SUT.getPersonFromAPI(Nino(nino.nino))
        Await.result(responseFuture, 5 seconds)

        verify(mockCacheConnector)
          .createOrUpdate(any(), Matchers.eq(person), any())(any())
      }
    }

    "return the person details from the person API" when {

      "a nino is provided and the person is not deceased" in {

        val deceased: Boolean = false

        val person = Person(Nino(nino.nino), "firstName1", "lastName1", Some(dateOfBirth), address, deceased)

        val mockCacheConnector = mock[CacheConnector]
        val mockCitizenDetailsUrls = mock[CitizenDetailsUrls]
        val mockHttpHandler = mock[HttpHandler]

        val SUT = spy(createSUT(mockCacheConnector, mockCitizenDetailsUrls, mockHttpHandler))

        when(mockHttpHandler.getFromApi(any(), any())(any()))
          .thenReturn(Future.successful(citizenDetailsJson(deceased)))
        when(mockCacheConnector.createOrUpdate[Person](any(), any(), any())(any()))
          .thenReturn(Future.successful(person))

        val responseFuture = SUT.getPersonFromAPI(Nino(nino.nino))
        Await.result(responseFuture, 5 seconds)

        verify(mockCacheConnector)
          .createOrUpdate(any(), Matchers.eq(person), any())(any())
      }

      "a nino is provided and the person is deceased" in {

        val deceased: Boolean = true

        val person = Person(Nino(nino.nino), "firstName1", "lastName1", Some(dateOfBirth), address, deceased)

        val mockCacheConnector = mock[CacheConnector]
        val mockCitizenDetailsUrls = mock[CitizenDetailsUrls]
        val mockHttpHandler = mock[HttpHandler]

        val SUT = spy(createSUT(mockCacheConnector, mockCitizenDetailsUrls, mockHttpHandler))

        when(mockHttpHandler.getFromApi(any(), any())(any()))
          .thenReturn(Future.successful(citizenDetailsJson(deceased)))
        when(mockCacheConnector.createOrUpdate[Person](any(), any(), any())(any()))
          .thenReturn(Future.successful(person))

        val responseFuture = SUT.getPersonFromAPI(Nino(nino.nino))
        Await.result(responseFuture, 5 seconds)

        verify(mockCacheConnector)
          .createOrUpdate(any(), Matchers.eq(person), any())(any())
      }

      "a nino but no service url is provided" in {

        val person = Person(Nino(nino.nino), "firstName1", "lastName1", Some(dateOfBirth), address, false)

        val mockCacheConnector = mock[CacheConnector]
        val mockCitizenDetailsUrls = mock[CitizenDetailsUrls]
        val mockHttpHandler = mock[HttpHandler]

        val SUT = spy(createSUT(mockCacheConnector, mockCitizenDetailsUrls, mockHttpHandler))

        when(mockHttpHandler.getFromApi(any(), any())(any()))
          .thenReturn(Future.successful(citizenDetailsJson()))
        when(mockCacheConnector.createOrUpdate[Person](any(), any(), any())(any()))
          .thenReturn(Future.successful(person))

        val responseFuture = SUT.getPersonFromAPI(Nino(nino.nino))
        Await.result(responseFuture, 5 seconds)

        verify(mockCacheConnector)
          .createOrUpdate(any(), Matchers.eq(person), any())(any())
      }
    }

    "return the person details from storage" when {
      "a nino is provided" in {

        val person = Person(Nino(nino.nino), "firstName1", "lastName1", Some(dateOfBirth), address, true)

        val mockCacheConnector = mock[CacheConnector]
        val mockCitizenDetailsUrls = mock[CitizenDetailsUrls]
        val mockHttpHandler = mock[HttpHandler]

        val SUT = spy(createSUT(mockCacheConnector, mockCitizenDetailsUrls, mockHttpHandler))

        when(mockCacheConnector.find[Person](any(), any())(any()))
          .thenReturn(Future.successful(Some(person)))

        val responseFuture = SUT.getPersonFromStorage(Nino(nino.nino))
        val result = Await.result(responseFuture, 5 seconds)

        result mustBe Some(person)

        verify(mockCacheConnector)
          .find(Matchers.eq(nino.nino), any())(any())
      }

      "a nino but no service url is provided" in {

        val person = Person(Nino(nino.nino), "firstName1", "lastName1", Some(dateOfBirth), address, true)

        val mockCacheConnector = mock[CacheConnector]
        val mockCitizenDetailsUrls = mock[CitizenDetailsUrls]
        val mockHttpHandler = mock[HttpHandler]

        val SUT = spy(createSUT(mockCacheConnector, mockCitizenDetailsUrls, mockHttpHandler))

        when(mockCacheConnector.find[Person](any(), any())(any()))
          .thenReturn(Future.successful(Some(person)))

        val responseFuture = SUT.getPersonFromStorage(Nino(nino.nino))
        val result = Await.result(responseFuture, 5 seconds)

        result mustBe Some(person)

        verify(mockCacheConnector)
          .find(Matchers.eq(nino.nino), any())(any())
      }
    }
  }

  private def citizenDetailsJson(deceased: Boolean = false): JsValue =
    Json.obj(
      "etag" -> "000",
      "person" -> Json.obj(
        "firstName" -> "firstName1",
        "middleName" -> "T",
        "lastName" -> "lastName1",
        "title" -> "Mr",
        "honours" -> "BSC",
        "sex" -> "M",
        "dateOfBirth" -> dateOfBirthString,
        "nino" -> nino.nino,
        "deceased" -> deceased
      ),
      "address" -> Json.obj(
        "line1" -> "line1",
        "line2" -> "line2",
        "line3" -> "line3",
        "postcode" -> "postcode",
        "startDate" -> "startDate",
        "country" -> "country",
        "type" -> "Residential"
      )
    )

  private val nino: Nino = new Generator(new Random).nextNino
  private implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("testSession")))
  private val address: Address = Address("line1", "line2", "line3", "postcode", "country")

  private val dateOfBirthString = "2017-02-01"
  private val dateOfBirth = LocalDate.parse(dateOfBirthString)

  private def createSUT(cacheConnector: CacheConnector,
                        citizenDetailsUrls: CitizenDetailsUrls,
                        httpHandler: HttpHandler) =
    new PersonRepository(cacheConnector, citizenDetailsUrls, httpHandler)
}
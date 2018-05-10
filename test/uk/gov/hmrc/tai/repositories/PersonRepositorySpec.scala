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
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.connectors.{CacheConnector, CitizenDetailsUrls, HttpHandler}
import uk.gov.hmrc.tai.controllers.FakeTaiPlayApplication
import uk.gov.hmrc.tai.model.domain.{Address, Person, PersonFormatter}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class PersonRepositorySpec extends PlaySpec
  with MockitoSugar
  with FakeTaiPlayApplication {

  "The getPerson method" should {

    "retrieve person details from mongo cache, bypassing an API call" when {

      "cached data is present for the requested nino" in {

        val person = Person(Nino(nino.nino), "firstName1", "lastName1", Some(dateOfBirth), address, false, false)

        val mockCacheConnector = mock[CacheConnector]
        val mockCitizenDetailsUrls = mock[CitizenDetailsUrls]
        val mockHttpHandler = mock[HttpHandler]
        when(mockCacheConnector.find[Person](Matchers.eq(nino.nino), Matchers.eq(personMongoKey))(any())).thenReturn(Future.successful(Some(person)))

        val SUT = createSUT(mockCacheConnector, mockCitizenDetailsUrls, mockHttpHandler)
        val responseFuture = SUT.getPerson(Nino(nino.nino))
        val result = Await.result(responseFuture, 5 seconds)

        result mustBe person

        verify(mockCacheConnector, times(1))
          .find[Person](Matchers.eq(nino.nino), Matchers.eq(personMongoKey))(any())

        verify(mockHttpHandler, never())
          .getFromApi(any(), any())(any())
      }
    }

    "retrieve person details from the person API, and update the mongo cache" when {

      "no cache data is currently held for the requested nino" in {

        val person = Person(Nino(nino.nino), "firstName1", "lastName1", Some(dateOfBirth), address, false, false)
        implicit val formats = PersonFormatter.personMongoFormat

        val mockCacheConnector = mock[CacheConnector]
        val mockCitizenDetailsUrls = mock[CitizenDetailsUrls]
        val mockHttpHandler = mock[HttpHandler]
        when(mockCacheConnector.find[Person](Matchers.eq(nino.nino), Matchers.eq(personMongoKey))(any())).thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdate[Person](any(), any(), Matchers.eq(personMongoKey))(any())).thenReturn(Future.successful(person))
        when(mockHttpHandler.getFromApi(any(), any())(any())).thenReturn(Future.successful(JsObject(Seq("person" -> Json.toJson(person)))))

        val SUT = createSUT(mockCacheConnector, mockCitizenDetailsUrls, mockHttpHandler)
        val responseFuture = SUT.getPerson(Nino(nino.nino))
        val result = Await.result(responseFuture, 5 seconds)

        result mustBe person

        verify(mockHttpHandler, times(1))
          .getFromApi(any(), any())(any())

        verify(mockCacheConnector, times(1))
          .createOrUpdate(any(), any(), Matchers.eq(personMongoKey))(any())
      }
    }

    "cache a correctly configured Person instace" when {

      "the person API sends json with missing fields" in {

        val jsonWithMissingFields = Json.obj(
          "etag" -> "000",
          "person" -> Json.obj(
            "nino" -> nino.nino,
            "address" -> Json.obj()
          )
        )
        val expectedPersonFromPartialJson = Person(nino,"", "", None,Address("","","","",""),false,false)

        val person = Person(Nino(nino.nino), "firstName1", "lastName1", Some(dateOfBirth), address, false, false)
        implicit val formats = PersonFormatter.personMongoFormat

        val mockCacheConnector = mock[CacheConnector]
        val mockCitizenDetailsUrls = mock[CitizenDetailsUrls]
        val mockHttpHandler = mock[HttpHandler]
        when(mockCacheConnector.find[Person](Matchers.eq(nino.nino), Matchers.eq(personMongoKey))(any())).thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdate[Person](any(), any(), Matchers.eq(personMongoKey))(any())).thenReturn(Future.successful(expectedPersonFromPartialJson))
        when(mockHttpHandler.getFromApi(any(), any())(any())).thenReturn(Future.successful(jsonWithMissingFields))

        val SUT = createSUT(mockCacheConnector, mockCitizenDetailsUrls, mockHttpHandler)
        val responseFuture = SUT.getPerson(Nino(nino.nino))
        val result = Await.result(responseFuture, 5 seconds)

        verify(mockCacheConnector, times(1))
          .createOrUpdate(any(), Matchers.eq(expectedPersonFromPartialJson), Matchers.eq(personMongoKey))(any())
      }
    }
  }

  private implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("testSession")))
  private val nino: Nino = new Generator(new Random).nextNino
  private val address: Address = Address("line1", "line2", "line3", "postcode", "country")
  private val personMongoKey = "PersonData"
  private val dateOfBirth = LocalDate.parse("2017-02-01")

  private def createSUT(cacheConnector: CacheConnector,
                        citizenDetailsUrls: CitizenDetailsUrls,
                        httpHandler: HttpHandler) =
    new PersonRepository(cacheConnector, citizenDetailsUrls, httpHandler)
}
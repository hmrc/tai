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

package uk.gov.hmrc.tai.repositories

import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.tai.connectors.{CacheConnector, CitizenDetailsUrls, HttpHandler}
import uk.gov.hmrc.tai.model.domain.{Address, Person, PersonFormatter}
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class PersonRepositorySpec extends BaseSpec {

  val address = Address("line1", "line2", "line3", "postcode", "country")
  val personMongoKey = "PersonData"
  val dateOfBirth = LocalDate.parse("2017-02-01")

  def createSUT(cacheConnector: CacheConnector, citizenDetailsUrls: CitizenDetailsUrls, httpHandler: HttpHandler) =
    new PersonRepository(cacheConnector, citizenDetailsUrls, httpHandler)

  "The getPerson method" should {

    "retrieve person details from mongo cache, bypassing an API call" when {

      "cached data is present for the requested nino" in {

        val person = Person(Nino(nino.nino), "firstName1", "lastName1", Some(dateOfBirth), address, false, false)

        val mockCacheConnector = mock[CacheConnector]
        val mockCitizenDetailsUrls = mock[CitizenDetailsUrls]
        val mockHttpHandler = mock[HttpHandler]
        when(mockCacheConnector.find[Person](meq(cacheId), meq(personMongoKey))(any()))
          .thenReturn(Future.successful(Some(person)))

        val SUT = createSUT(mockCacheConnector, mockCitizenDetailsUrls, mockHttpHandler)
        val responseFuture = SUT.getPerson(Nino(nino.nino))
        val result = Await.result(responseFuture, 5 seconds)

        result mustBe person

        verify(mockCacheConnector, times(1))
          .find[Person](meq(cacheId), meq(personMongoKey))(any())

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
        when(mockCacheConnector.find[Person](meq(cacheId), meq(personMongoKey))(any()))
          .thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdate[Person](any(), any(), meq(personMongoKey))(any()))
          .thenReturn(Future.successful(person))
        when(mockHttpHandler.getFromApi(any(), any())(any()))
          .thenReturn(Future.successful(JsObject(Seq("person" -> Json.toJson(person)))))

        val SUT = createSUT(mockCacheConnector, mockCitizenDetailsUrls, mockHttpHandler)
        val responseFuture = SUT.getPerson(Nino(nino.nino))
        val result = Await.result(responseFuture, 5 seconds)

        result mustBe person

        verify(mockHttpHandler, times(1))
          .getFromApi(any(), any())(any())

        verify(mockCacheConnector, times(1))
          .createOrUpdate(any(), any(), meq(personMongoKey))(any())
      }
    }

    "cache a correctly configured Person instace" when {

      "the person API sends json with missing fields" in {

        val jsonWithMissingFields = Json.obj(
          "etag" -> "000",
          "person" -> Json.obj(
            "nino"    -> nino.nino,
            "address" -> Json.obj()
          )
        )
        val expectedPersonFromPartialJson = Person(nino, "", "", None, Address("", "", "", "", ""), false, false)

        val person = Person(Nino(nino.nino), "firstName1", "lastName1", Some(dateOfBirth), address, false, false)
        implicit val formats = PersonFormatter.personMongoFormat

        val mockCacheConnector = mock[CacheConnector]
        val mockCitizenDetailsUrls = mock[CitizenDetailsUrls]
        val mockHttpHandler = mock[HttpHandler]
        when(mockCacheConnector.find[Person](meq(cacheId), meq(personMongoKey))(any()))
          .thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdate[Person](any(), any(), meq(personMongoKey))(any()))
          .thenReturn(Future.successful(expectedPersonFromPartialJson))
        when(mockHttpHandler.getFromApi(any(), any())(any())).thenReturn(Future.successful(jsonWithMissingFields))

        val SUT = createSUT(mockCacheConnector, mockCitizenDetailsUrls, mockHttpHandler)
        val responseFuture = SUT.getPerson(Nino(nino.nino))
        val result = Await.result(responseFuture, 5 seconds)

        verify(mockCacheConnector, times(1))
          .createOrUpdate(any(), meq(expectedPersonFromPartialJson), meq(personMongoKey))(any())
      }
    }
  }
}

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

package uk.gov.hmrc.tai.repositories

import java.time.LocalDate
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import org.scalatest.concurrent.IntegrationPatience
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.tai.connectors.CitizenDetailsConnector
import uk.gov.hmrc.tai.model.domain.{Address, Person}
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class PersonRepositorySpec extends BaseSpec with IntegrationPatience {

  val address = Address("line1", "line2", "line3", "postcode", "country")
  val personMongoKey = "PersonData"
  val dateOfBirth = LocalDate.parse("2017-02-01")

  def createSUT(cacheRepository: CacheRepository, citizenDetailsConnector: CitizenDetailsConnector) =
    new PersonRepository(cacheRepository, citizenDetailsConnector)

  "The getPerson method" must {
    "retrieve person details from mongo cache, bypassing an API call" when {
      "cached data is present for the requested nino" in {

        val person = Person(Nino(nino.nino), "firstName1", "lastName1", Some(dateOfBirth), address, false, false)

        val mockCacheConnector = mock[CacheRepository]
        val citizenDetailsConnector = mock[CitizenDetailsConnector]
        when(mockCacheConnector.find[Person](meq(cacheId), meq(personMongoKey))(any()))
          .thenReturn(Future.successful(Some(person)))

        val SUT = createSUT(mockCacheConnector, citizenDetailsConnector)
        val result = SUT.getPerson(Nino(nino.nino)).futureValue

        result mustBe person

        verify(mockCacheConnector, times(1))
          .find[Person](meq(cacheId), meq(personMongoKey))(any())

        verify(citizenDetailsConnector, never())
          .getPerson(any())(any())
      }
    }

    "retrieve person details from the person API, and update the mongo cache" when {
      "no cache data is currently held for the requested nino" in {

        val person = Person(Nino(nino.nino), "firstName1", "lastName1", Some(dateOfBirth), address, false, false)

        val mockCacheConnector = mock[CacheRepository]
        val citizenDetailsConnector = mock[CitizenDetailsConnector]
        when(mockCacheConnector.find[Person](meq(cacheId), meq(personMongoKey))(any()))
          .thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdate[Person](any(), any(), meq(personMongoKey))(any()))
          .thenReturn(Future.successful(person))
        when(citizenDetailsConnector.getPerson(any())(any()))
          .thenReturn(Future.successful(person))

        val SUT = createSUT(mockCacheConnector, citizenDetailsConnector)
        val result = SUT.getPerson(Nino(nino.nino)).futureValue

        result mustBe person

        verify(citizenDetailsConnector, times(1))
          .getPerson(any())(any())

        verify(mockCacheConnector, times(1))
          .createOrUpdate(any(), any(), meq(personMongoKey))(any())
      }
    }
  }
}

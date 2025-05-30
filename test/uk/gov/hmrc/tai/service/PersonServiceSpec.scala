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

package uk.gov.hmrc.tai.service

import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.when
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.tai.model.domain.{Address, Person}
import uk.gov.hmrc.tai.repositories.deprecated.PersonRepository
import uk.gov.hmrc.tai.util.BaseSpec

import java.time.LocalDate
import scala.concurrent.Future

class PersonServiceSpec extends BaseSpec {

  val person: Person =
    Person(nino, "firstname", "surname", Some(LocalDate.now()), Address("l1", "l2", "l3", "pc", "country"))

  def createSUT(personRepository: PersonRepository = mock[PersonRepository]) = new PersonService(personRepository)

  "person method" must {
    "return a person model instance, retrieved from the person repository" in {
      val mockRepo = mock[PersonRepository]
      when(mockRepo.getPerson(meq(nino))(any())).thenReturn(Future.successful(person))
      val SUT = createSUT(mockRepo)
      SUT.person(nino).futureValue mustBe person
    }

    "expose any exception thrown by the person repository" in {
      val mockRepo = mock[PersonRepository]
      when(mockRepo.getPerson(meq(nino))(any()))
        .thenReturn(Future.failed(new NotFoundException("an example not found exception")))
      val SUT = createSUT(mockRepo)
      val result = SUT.person(nino).failed.futureValue
      result mustBe a[NotFoundException]
      result.getMessage mustBe "an example not found exception"
    }
  }
}

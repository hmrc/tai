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

package uk.gov.hmrc.tai.service

import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.when
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.tai.model.domain.{Address, Person}
import uk.gov.hmrc.tai.repositories.PersonRepository
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class PersonServiceSpec extends BaseSpec {

  "person method" must {
    "return a person model instance, retrieved from the person repository" in {
      val mockRepo = mock[PersonRepository]
      when(mockRepo.getPerson(meq(nino))(any())).thenReturn(Future.successful(person))
      val SUT = createSUT(mockRepo)
      Await.result(SUT.person(nino), 5 seconds) mustBe person
    }

    "expose any exception thrown by the person repository" in {
      val mockRepo = mock[PersonRepository]
      when(mockRepo.getPerson(meq(nino))(any()))
        .thenReturn(Future.failed(new NotFoundException("an example not found exception")))
      val SUT = createSUT(mockRepo)
      val thrown = the[NotFoundException] thrownBy Await.result(SUT.person(nino), 5 seconds)
      thrown.getMessage mustBe "an example not found exception"
    }
  }

  val person: Person =
    Person(nino, "firstname", "surname", Some(new LocalDate()), Address("l1", "l2", "l3", "pc", "country"))
  def createSUT(personRepository: PersonRepository = mock[PersonRepository]) = new PersonService(personRepository)

}

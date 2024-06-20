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

package uk.gov.hmrc.tai.controllers

import java.time.LocalDate
import org.mockito.ArgumentMatchers.{any, eq => meq}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.service.PersonService
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class PersonControllerSpec extends BaseSpec {

  "taxPayer method" must {
    "return 200" when {
      "given a valid nino" in {
        val mockPersonService = mock[PersonService]
        when(mockPersonService.person(meq(nino))(any())).thenReturn(Future.successful(person))

        val result = createSUT(personService = mockPersonService).person(nino)(FakeRequest())
        status(result) mustBe OK
      }
    }
    "return a JSON ApiResponse wrapping a person instance" when {
      "the person service was able to successfully retrieve details" in {
        val expectedJson = Json.obj(
          "data" ->
            Json.obj(
              "nino"        -> nino.nino,
              "firstName"   -> "firstname",
              "surname"     -> "surname",
              "dateOfBirth" -> "1982-05-26",
              "address" -> Json.obj(
                "line1"    -> "l1",
                "line2"    -> "l2",
                "line3"    -> "l3",
                "postcode" -> "pc",
                "country"  -> "country"
              ),
              "isDeceased"              -> false,
              "manualCorrespondenceInd" -> false
            ),
          "links" -> Json.arr()
        )

        val mockPersonService = mock[PersonService]
        when(mockPersonService.person(meq(nino))(any())).thenReturn(Future.successful(person))

        val result = createSUT(personService = mockPersonService).person(nino)(FakeRequest())
        contentAsJson(result) mustBe expectedJson
      }
    }
    "expose any underlying excpetion" in {
      val mockPersonService = mock[PersonService]
      when(mockPersonService.person(meq(nino))(any()))
        .thenReturn(Future.failed(new NotFoundException("an example not found exception")))

      val result = createSUT(personService = mockPersonService).person(nino)(FakeRequest()).failed.futureValue

      result mustBe a[NotFoundException]

      result.getMessage mustBe "an example not found exception"
    }
  }

  val person = Person(
    nino,
    "firstname",
    "surname",
    Some(LocalDate.of(1982, 5, 26)),
    Address("l1", "l2", "l3", "pc", "country"),
    false,
    false
  )

  private def createSUT(
    authenticationPredicate: AuthenticationPredicate = loggedInAuthenticationPredicate,
    personService: PersonService = mock[PersonService]
  ) =
    new PersonController(authenticationPredicate, personService, cc)
}

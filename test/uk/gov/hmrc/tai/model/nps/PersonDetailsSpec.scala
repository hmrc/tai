/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.nps

import org.joda.time.DateTime
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.model.TaiRoot

import scala.util.Random

class PersonDetailsSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  "PersonDetails" should {
    "return a blank taiRoot object (except for nino)" when {
      "a blank person (except for nino) is supplied" in {

        val person = Person(None, None, None, None, None, None, None, None, Nino(nino.nino), None, None)

        val sut = PersonDetails("0", person)

        val result = sut.toTaiRoot

        result mustBe TaiRoot(nino.nino, 0, "", "", None, "", " ", false, None)
      }

      "a blank person (except for nino) represented as json is supplied" in {

        val json = Json.obj(
          "etag"   -> "0",
          "person" -> Json.obj("nino" -> nino)
        )

        val sut = json.as[PersonDetails]

        val result = sut.toTaiRoot

        result mustBe TaiRoot(nino.nino, 0, "", "", None, "", " ", false, None)
      }
    }

    "return a taiRoot object containing person details" when {
      "a person is supplied" in {

        val person = Person(
          Some("firstName"),
          Some("middleName"),
          Some("lastName"),
          Some("initials"),
          Some("title"),
          Some("honours"),
          Some("sex"),
          Some(DateTime.parse("1900-01-01")),
          Nino(nino.nino),
          Some(true),
          None
        )

        val sut = PersonDetails("1", person)

        val result = sut.toTaiRoot

        result mustBe TaiRoot(
          nino.nino,
          1,
          "title",
          "firstName",
          Some("middleName"),
          "lastName",
          "firstName lastName",
          true,
          None)
      }

      "a person represented as json is supplied" in {

        val json = Json.obj(
          "etag" -> "1",
          "person" -> Json.obj(
            "nino"                    -> nino,
            "title"                   -> "testTitle",
            "firstName"               -> "testFirstName",
            "middleName"              -> "testMiddleName",
            "lastName"                -> "testLastName",
            "manualCorrespondenceInd" -> true
          )
        )

        val sut = json.as[PersonDetails]

        val result = sut.toTaiRoot

        result mustBe TaiRoot(
          nino.nino,
          1,
          "testTitle",
          "testFirstName",
          Some("testMiddleName"),
          "testLastName",
          "testFirstName testLastName",
          true,
          None)
      }
    }

    "throw an exception" when {
      "supplied etag data is not a number" in {

        val person = Person(None, None, None, None, None, None, None, None, Nino(nino.nino), None, None)

        val sut = PersonDetails("", person)

        the[NumberFormatException] thrownBy sut.toTaiRoot
      }

      "supplied etag data (represented as json) is not a number" in {

        val json = Json.obj(
          "etag"   -> "",
          "person" -> Json.obj("nino" -> nino)
        )

        val sut = json.as[PersonDetails]

        the[NumberFormatException] thrownBy sut.toTaiRoot
      }
    }
  }

  private val nino: Nino = new Generator(new Random).nextNino

}

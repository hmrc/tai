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

import org.joda.time.{ DateTimeZone, DateTime }
import play.api.libs.json.{ Writes, Format, Reads, Json }
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.tai.model.TaiRoot

object Person {
  implicit val formats = Json.format[Person]
}
case class Person(
  firstName: Option[String],
  middleName: Option[String],
  lastName: Option[String],
  initials: Option[String],
  title: Option[String],
  honours: Option[String],
  sex: Option[String],
  dateOfBirth: Option[DateTime],
  nino: Nino,
  manualCorrespondenceInd: Option[Boolean],
  deceased: Option[Boolean])

object PersonDetails {
  implicit val formats = Json.format[PersonDetails]
}

case class PersonDetails(
  etag: String,
  person: Person) {

  def toTaiRoot: TaiRoot = {
    val firstName = person.firstName.getOrElse("")
    val surname = person.lastName.getOrElse("")
    val secondName = person.middleName
    val nino = person.nino.value
    val title = person.title.getOrElse("")
    val deceasedIndicator = person.deceased
    val manualCorrespondenceInd = person.manualCorrespondenceInd.getOrElse(false)
    TaiRoot(nino, etag.toInt, title, firstName, secondName, surname, s"$firstName $surname", manualCorrespondenceInd, deceasedIndicator)
  }
}

/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.domain

import org.joda.time.LocalDate
import uk.gov.hmrc.domain.Nino

case class Person(
  nino: Nino,
  firstName: String,
  surname: String,
  dateOfBirth: Option[LocalDate],
  address: Address,
  isDeceased: Boolean = false,
  hasCorruptData: Boolean = false)

case class Address(line1: String, line2: String, line3: String, postcode: String, country: String)

object PersonFormatter {

  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  implicit val addressFormat = Json.format[Address]

  val personMongoFormat = Json.format[Person]

  val personHodRead: Reads[Person] = (
    (JsPath \ "person" \ "nino").read[Nino] and
      ((JsPath \ "person" \ "firstName").read[String] or Reads.pure("")) and
      ((JsPath \ "person" \ "lastName").read[String] or Reads.pure("")) and
      (JsPath \ "person" \ "dateOfBirth").readNullable[LocalDate] and
      ((JsPath \ "address").read[Address] or Reads.pure(Address("", "", "", "", ""))) and
      ((JsPath \ "person" \ "deceased").read[Boolean] or Reads.pure(false)) and
      ((JsPath \ "person" \ "manualCorrespondenceInd").read[Boolean] or Reads.pure(false))
  )(Person.apply _)
}

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

package uk.gov.hmrc.tai.model.nps

import play.api.libs.json._
import uk.gov.hmrc.tai.model.nps.NpsDate._

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.language.implicitConversions
import scala.util.Try

case class NpsDate(localDate: LocalDate) {
  val toTaxPlatformString: String = localDate.format(taxPlatformDateFormat)
  val toNpsString: String = localDate.format(npsDateFormat)
}

object NpsDate {
  implicit val reads: Reads[NpsDate] = {
    case JsString(npsDateRegex(d, m, y)) => JsSuccess(NpsDate(LocalDate.of(y.toInt, m.toInt, d.toInt)))
    case JsNull                          => JsError(JsonValidationError("Cannot convert null to NpsDate"))
    case invalid => JsError(JsonValidationError(s"The date was not of the expected format [dd/MM/yyyy]: $invalid"))
  }
  implicit val writes: Writes[NpsDate] = (date: NpsDate) => JsString(date.toNpsString)

  private val taxPlatformDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  private val npsDateRegex = """^(\d\d)/(\d\d)/(\d\d\d\d)$""".r
  private val npsDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
}

object NpsDateImplicitConversions {

  implicit def optDateFromOptString(string: Option[String]): Option[NpsDate] = string.flatMap { str =>
    Try {
      dateFromString(str)
    }.toOption
  }

  implicit def dateFromString(stringDate: String): NpsDate = NpsDate(localDateSerializer.deserialize(stringDate))

  implicit def dateToString(optionDate: Option[NpsDate]): Option[String] = optionDate.map(_.toTaxPlatformString)

  implicit def localToNps(date: LocalDate): NpsDate = NpsDate(date)
}

// TODO: Replace this with our existing date serialization code
object localDateSerializer {

  private val localDateRegex = """^(\d\d\d\d)-(\d\d)-(\d\d)$""".r

  def deserialize(str: String): LocalDate = str match {
    case localDateRegex(y, m, d) =>
      LocalDate.of(y.toInt, m.toInt, d.toInt)
    case _ => throw new Exception(parseError(str))
  }

  def serialize(value: LocalDate): String =
    "%04d-%02d-%02d".format(value.getYear, value.getMonthValue, value.getDayOfMonth)

  private def parseError(str: String) =
    s"Unable to parse '$str' to type 'LocalDate', expected a valid value with format: yyyy-MM-dd"
}

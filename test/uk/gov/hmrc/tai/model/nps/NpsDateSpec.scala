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

package uk.gov.hmrc.tai.model.nps

import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._

class NpsDateSpec extends PlaySpec {

  "NpsDate" should {

    "expose date in tax platform string form" in {
      NpsDate(LocalDate.parse("2016-12-12")).toNpsString mustBe "12/12/2016"
    }

    "expose date in nps string form" in {
      NpsDate(LocalDate.parse("2016-12-12")).toTaxPlatformString mustBe "2016-12-12"
    }
  }

  "NpsDate Json integration" should {

    "marshall an NpsDate instance into a JsValue" in {
      val jsonObj = Json.toJson(NpsDate(LocalDate.parse("2017-05-03")))
      jsonObj.toString() mustBe """"03/05/2017""""
    }

    "unmarshall a valid Json date string into an NpsDate" in {
      val npsDate = Json.parse(""""03/05/2017"""").as[NpsDate]
      npsDate mustBe NpsDate(LocalDate.parse("2017-05-03"))
    }

    "throw a JsError" when {

      "an empty string is given" in {
        val thrown = the[JsResultException] thrownBy Json.parse("""""""").as[NpsDate]
        extractErrorsPerPath(thrown) mustBe List("""The date was not of the expected format [dd/MM/yyyy]: """"")
      }

      "an invalid string is given" in {
        val thrown = the[JsResultException] thrownBy Json.parse(""""invalid"""").as[NpsDate]
        extractErrorsPerPath(thrown) mustBe List("""The date was not of the expected format [dd/MM/yyyy]: "invalid"""")
      }

      "JsNull is given" in {
        val p = NpsDate.reads.reads(JsNull)
        p match {
          case error: JsError => {
            val valErrors: Seq[JsonValidationError] = error.errors.head._2
            valErrors.size mustBe 1
            valErrors.head.message mustBe "Cannot convert null to NpsDate"
          }
          case _ => fail("parsing of null should result in an error")
        }
      }
    }
  }

  "NpsDateImplicitConversions" should {

    "create an NpsDate" when {

      "given an Option of a String" in {
        NpsDateImplicitConversions.optDateFromOptString(Some("2016-10-10")) mustBe Some(
          NpsDate(LocalDate.parse("2016-10-10")))
        NpsDateImplicitConversions.optDateFromOptString(None) mustBe None
      }

      "given a String" in {
        NpsDateImplicitConversions.dateFromString("2016-09-11") mustBe NpsDate(LocalDate.parse("2016-09-11"))
      }

      "given a LocalDate instance" in {
        val date = LocalDate.parse("2017-03-03")
        NpsDateImplicitConversions.localToNps(date) mustBe NpsDate(date)
      }
    }

    "throw an exception" when {

      "given a badly formatted date string" in {
        val thrown = the[Exception] thrownBy NpsDateImplicitConversions.dateFromString("44/123/12/33")
        thrown.getMessage mustBe """Unable to parse '44/123/12/33' to type 'LocalDate', expected a valid value with format: yyyy-MM-dd"""
      }
      "given an empty date string" in {
        val thrown = the[Exception] thrownBy NpsDateImplicitConversions.dateFromString("")
        thrown.getMessage mustBe """Unable to parse '' to type 'LocalDate', expected a valid value with format: yyyy-MM-dd"""
      }
    }

    "provide a tax platform date string" when {

      "given an NpsDate instance" in {
        val string = NpsDateImplicitConversions.dateToString(Some(NpsDate(LocalDate.parse("2015-11-11"))))
        string mustBe Some("2015-11-11")
      }
    }
  }

  "localDateSerializer" should {

    "serialize a LocalDate instance into yyyy-MM-dd string form" in {
      localDateSerializer.serialize(LocalDate.parse("2015-11-11")) mustBe "2015-11-11"
    }
  }

  private def extractErrorsPerPath(exception: JsResultException): Seq[String] =
    for {
      (path: JsPath, errors: Seq[JsonValidationError]) <- exception.errors
      error: JsonValidationError                       <- errors
      message: String                                  <- error.messages
    } yield {
      message
    }
}

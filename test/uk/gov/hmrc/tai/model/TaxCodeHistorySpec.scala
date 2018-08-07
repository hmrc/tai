/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model

import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNull, JsValue, Json}
import uk.gov.hmrc.domain.Generator

import scala.util.Random

class TaxCodeHistorySpec extends PlaySpec {

  "TaxCodeHistory reads" should {
    "return a TaxCodeHistory given valid Json missing the taxCodeRecord field" in {

      val nino = randomNino
      val taxCodeHistory = TaxCodeHistory(nino, None)

      val validJson = Json.obj(
        "nino" -> nino,
        "taxCodeRecord" -> JsNull
      )

      validJson.as[TaxCodeHistory] mustEqual taxCodeHistory

    }
  }


  "TaxCodeHistory writes" should {
    "return a json representation of TaxCodeHistory given a taxCodeRecord" when {
      "taxCodeRecord is present" in {
        val nino = randomNino
        val taxCodeHistory = TaxCodeHistory(nino, Some(Seq()))

        val validJson = Json.obj(
          "nino" -> nino,
          "taxCodeRecord" -> Seq.empty[TaxCodeRecord]
        )

        Json.toJson(taxCodeHistory) mustEqual validJson

      }


      "taxCodeRecord is not present" in {
        val nino = randomNino
        val taxCodeHistory = TaxCodeHistory(nino, None)

        val validJson = Json.obj(
          "nino" -> nino
        )

        Json.toJson(taxCodeHistory) mustEqual validJson

      }

    }
  }


  private def randomNino: String = new Generator(new Random).nextNino.toString().slice(0, -1)

}

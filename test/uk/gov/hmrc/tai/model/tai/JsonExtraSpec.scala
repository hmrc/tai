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

package uk.gov.hmrc.tai.model.tai

import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import org.slf4j.{Logger, LoggerFactory}
import uk.gov.hmrc.tai.model.rti.RtiEyu
import org.joda.time.LocalDate
import org.scalatest.mockito.MockitoSugar

class JsonExtraSpec extends PlaySpec with MockitoSugar {

  implicit val log: Logger = LoggerFactory.getLogger(this.getClass)

  "JsonExtra" must {
    "Use map format to write to Json value" when {
      "given valid input return JsValue" in {
        val inputMap: Map[TaxYear, TaxYear] = Map(TaxYear(2016) -> TaxYear(2017))
        val result = JsonExtra.mapFormat("foo", "bar").writes(inputMap)
        result.toString() must be("""[{"foo":2016,"bar":2017}]""")
      }
    }

    "Use JsValue to read input into map" when {
      "given valid input return correct object" in {
        val inputJsValue: JsValue = Json.parse("""[{"foo":2016, "bar":2017}]""")
        val result = JsonExtra.mapFormat("foo", "bar").reads(inputJsValue)
        result must be(JsSuccess(Map(TaxYear(2016) -> TaxYear(2017))))
      }

      "given invalid input return JsError" in {
        val inputJsValue: JsValue = Json.parse("""{}""")
        val result = JsonExtra.mapFormat("foo", "bar").reads(inputJsValue)
        result must be(JsError(s"Expected JsArray(...), found {}"))
      }
    }

    "Use enumeration format to" when {

      object testEnum extends Enumeration {
        type testEnum = Value
        val FOO, BAR = Value
      }

      "read when given Json" in {
        val inputJsValue: JsString = JsString("""FOO""")
        val result = JsonExtra.enumerationFormat(testEnum).reads(inputJsValue)
        result must be(JsSuccess(testEnum.FOO.leftSideValue))
      }

      "write when given a value" in {
        val inputValue = testEnum.BAR.leftSideValue
        val result = JsonExtra.enumerationFormat(testEnum).writes(inputValue)
        result must be(JsString("""BAR"""))
      }
    }

    "Use bodge list to" when {
      "write to Json" in {
        implicit val formatRtiEyuList: Format[List[RtiEyu]] = JsonExtra.bodgeList[RtiEyu]
        val json = Json.toJson(List(RtiEyu(None, None, None, new LocalDate(2016, 6, 9))))
        json.toString() must be(
          """[{"optionalAdjustmentAmount":[],"niLettersAndValues":[{"niFigure":[]}],"rcvdDate":"2016-06-09"}]""")
      }

      "read Json and return list" in {
        val json: JsValue = Json.parse(
          """[{"optionalAdjustmentAmount":[],"niLettersAndValues":[{"niFigure":[]}],"rcvdDate":"2016-06-09"}]""")
        val result = JsonExtra.bodgeList[RtiEyu].reads(json)
        result.asOpt must be(Some(List(RtiEyu(None, None, None, new LocalDate(2016, 6, 9)))))
      }

      "read empty Json and return error" in {
        val json: JsValue = Json.parse("""[{"optionalAdjustmentAmount":[],"niLettersAndValues":[{"niFigure":[]}]}]""")
        val ex = JsonExtra.bodgeList[RtiEyu].reads(json)
        ex.asOpt must be(Some(List()))
      }
    }
  }
}

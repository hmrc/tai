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

package hmrc.nps2

import org.scalatest._
import play.api.libs.json._
import uk.gov.hmrc.tai.model.nps2.{NpsEmployment, NpsFormatter}

import scala.util.Success
import scala.util.Try

class NpsEmploymentTest extends WordSpec with Matchers with NpsFormatter {
  val data: Seq[JsValue] = {
    val resource = this.getClass.getResourceAsStream("nps-employments.json")
    val stream = scala.io.Source.fromInputStream(resource)
    Json.parse(stream.getLines.mkString) match {
      case JsArray(e) => e
      case _          => throw new IllegalArgumentException("Cannot read test NPS employment data")
    }
  }

  val tryParse: Seq[(Int, Try[NpsEmployment])] = {
    data.zipWithIndex.map { x =>
      (x._2, Try(x._1.as[NpsEmployment]))
    }
  }

  "NPS Employment JSON Parsing" should {
    "be able to parse all the stub data" in {
      tryParse.filter(_._2.isFailure).toMap should be(Map.empty)
    }

    "have the property fromJson(toJson(x)) == x" in {
      tryParse.foreach {
        case (k, Success(v)) => Json.toJson(v).as[NpsEmployment] should be(v)
        case _               =>
      }
    }
  }
}

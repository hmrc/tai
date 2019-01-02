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
import java.io.File

import uk.gov.hmrc.tai.model.nps2.{NpsFormatter, TaxAccount}

import scala.io.Source.fromFile
import util._
import play.api.libs.json.Json

class TaxAccountTest extends WordSpec with Matchers with NpsFormatter {

  def parseNpsTaxAccount(f: File): Try[TaxAccount] = for {
    text <- Try( fromFile(f).mkString )
    json <- Try( Json.parse(text) )
    obj  <- Try( json.as[TaxAccount] )
  } yield obj

  val qaData = {
    new java.io.File("test/data/QAData")
  }.listFiles.map(f =>
    f.getAbsolutePath -> parseNpsTaxAccount(f)
  ).toMap

  val otherData = {
    val files = {
      new java.io.File("test/data")
    }.listFiles.collect{
      case x if x.isDirectory => x.listFiles
    }.flatten.filter(
      _.getName.endsWith("NpsTaxAccount.json")
    )
    files.map(f =>
      f.getAbsolutePath -> parseNpsTaxAccount(f)
    ).toMap
  }

  "NPS TaxAccount JSON Parsing" should {
    "be able to parse the QA data" in {
      qaData.filter(_._2.isFailure) should be (Map.empty)
    }

    "be able to parse all the other files" in {
      otherData.filter(_._2.isFailure) should be (Map.empty)
    }
  }
}

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

package data

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.model.TaxSummaryDetails
import uk.gov.hmrc.tai.model.nps2.NpsFormatter

import java.io.File
import scala.io.BufferedSource
import scala.util.Random

object NpsData extends NpsFormatter {

  private lazy val taxSummaryDetailsJson = "TaxSummaryDetails/TaxSummary.json"

  private lazy val NpsTaxAccountJson = "TaxDetail/TaxAccount.json"

  private lazy val NpsTaxAccountMultipleAllowancesJson = "TaxDetail/TaxAccount_Multiple_AllowReliefDeduct.json"

  private lazy val FETaxAccountJson = "TaxDetail/FETaxAccount.json"

  private val basePath = "test/resources/data/"

  private val nino: Nino = new Generator(new Random).nextNino

  private def getJson(fileName: String): JsValue = {
    val jsonFilePath = basePath + fileName
    val file: File = new File(jsonFilePath)
    val source: BufferedSource = scala.io.Source.fromFile(file)
    val jsVal = Json.parse(source.mkString("").replaceAll("\\$NINO", nino.nino))
    jsVal
  }

  private def getTaxSummaryDetails(fileName: String): TaxSummaryDetails = {
    val jsonFilePath = basePath + fileName
    val file: File = new File(jsonFilePath)
    val source: BufferedSource = scala.io.Source.fromFile(file)
    val jsVal = Json.parse(source.mkString("").replaceAll("\\$NINO", nino.nino))
    val result = Json.fromJson[TaxSummaryDetails](jsVal)
    result.get
  }

  def getTaxSummary = getTaxSummaryDetails(taxSummaryDetailsJson)
  def getNpsTaxAccountJson = getJson(NpsTaxAccountJson)
  def getFETaxAccountJson = getJson(FETaxAccountJson)
  def getNpsTaxAccountMultipleAllowancesJson = getJson(NpsTaxAccountMultipleAllowancesJson)
}

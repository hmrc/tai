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

package data

import java.io.File

import org.joda.time.LocalDate
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.tai.model.rti.{RtiData, RtiEmployment}

import scala.io.Source
import scala.util.Random


object RTIData {

  private lazy val RtiData = "AutoUpdate/RtiData.json"

  private val basePath = "test/resources/data/"

  val nino = new Generator(new Random).nextNino

  private def getRTIData(fileName: String):RtiData = {
    val jsonFilePath = basePath + fileName
    val source = Source.fromFile(jsonFilePath).mkString("").replace("$NINO", nino.nino)
    val jsVal = Json.parse(source)
    val result = Json.fromJson[RtiData](jsVal)
    val rtiData: RtiData = result.get
    updatePmtDateToThisYear(rtiData)
  }

  private def getRTIEmployment(fileName: String):RtiEmployment = {
    val jsonFilePath = basePath + fileName
    val file : File = new File(jsonFilePath)
    val source = scala.io.Source.fromFile(file).mkString("").replace("$NINO", nino.nino)
    val jsVal = Json.parse(source)
    val result = Json.fromJson[RtiEmployment](jsVal)
    result.get
  }

  def getRtiData = getRTIData(RtiData)

  def updatePmtDateToThisYear(oldData : RtiData) = oldData.copy(
    employments = oldData.employments.map{ employment =>
      employment.copy(payments = employment.payments.map{ inYear =>
        //We can finally try to change the value for the payment data
        inYear.copy(paidOn = new LocalDate(
          uk.gov.hmrc.time.TaxYearResolver.currentTaxYear,
          inYear.paidOn.getMonthOfYear,
          inYear.paidOn.getDayOfWeek
        ))
      })
    }
  )
}
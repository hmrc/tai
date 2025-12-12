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

import play.api.libs.json.Json
import uk.gov.hmrc.domain.{Nino, NinoGenerator}
import uk.gov.hmrc.tai.model.rti.RtiData
import uk.gov.hmrc.tai.model.tai.TaxYear

import java.time.LocalDate
import scala.io.Source

object RTIData {

  private lazy val RtiData = "AutoUpdate/RtiData.json"

  private val basePath = "test/resources/data/"

  val nino: Nino = NinoGenerator().nextNino

  private def getRTIData(fileName: String): RtiData = {
    val jsonFilePath = basePath + fileName
    val source = Source.fromFile(jsonFilePath).mkString("").replace("$NINO", nino.nino)
    val jsVal = Json.parse(source)
    val result = Json.fromJson[RtiData](jsVal)
    val rtiData: RtiData = result.get
    updatePmtDateToThisYear(rtiData)
  }

  def getRtiData: RtiData = getRTIData(RtiData)

  def updatePmtDateToThisYear(oldData: RtiData): RtiData = oldData.copy(
    employments = oldData.employments.map { employment =>
      employment.copy(payments = employment.payments.map { inYear =>
        // We can finally try to change the value for the payment data
        inYear.copy(
          paidOn = LocalDate.of(
            TaxYear().year,
            inYear.paidOn.getMonthValue,
            inYear.paidOn.getDayOfWeek.getValue
          )
        )
      })
    }
  )
}

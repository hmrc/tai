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

package uk.gov.hmrc.tai.connectors

import java.net.URL

import com.github.tomakehurst.wiremock.client.WireMock.{get, ok, urlEqualTo}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.util.WireMockHelper

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Random, Success}

class TaxAccountHistoryConnectorSpec extends PlaySpec with WireMockHelper with BeforeAndAfterAll with MockitoSugar {

  "Tax Account History" must {
    "returns a Success[Seq[CodingComponent]] for valid json" in {
      val testNino = randomNino
      val taxCodeId = 1

      val codingComponentList = ListBuffer[CodingComponent](
        CodingComponent(PersonalAllowancePA, None, 8105, "Personal Allowance", Some(8105)),
        CodingComponent(JobSeekersAllowance, None, 105, "Job Seekers", Some(105))
      )

      val url = {
        val path = new URL(urlConfig.taxAccountHistoricSnapshotUrl(testNino, taxCodeId))
        s"${path.getPath}"
      }

      val json = taxCodeHistoryJson(testNino)

      server.stubFor(
        get(urlEqualTo(url)).willReturn(ok(json.toString))
      )

      val result = Await.result(connector().taxAccountHistory(testNino, taxCodeId), 5.seconds)

      result mustEqual Success(codingComponentList)
    }
  }

  private def taxCodeHistoryJson(nino: Nino): JsObject = {
    Json.obj(
      "taxAccountId" -> 7,
      "date" -> "02/08/2018",
      "nino" -> nino.toString,
      "noCYEmployment" -> false,
      "taxYear" -> 2018,
      "previousTaxAccountId" -> 6,
      "previousYearTaxAccountId" -> 1,
      "nextTaxAccountId" -> JsNull,
      "nextYearTaxAccountId" -> JsNull,
      "totalEstTax" -> 16956,
      "inYearCalcResult" -> 1,
      "inYearCalcAmount" -> 0,
      "incomeSources" -> Json.arr(
        incomeSourceJson
      )
    )
  }

  private def incomeSourceJson: JsObject = {
    Json.obj(
      "employmentId" -> 3,
      "employmentType" -> 1,
      "employmentStatus" -> 1,
      "employmentTaxDistrictNumber" -> 754,
      "employmentPayeRef" -> "employmentPayeRef",
      "pensionIndicator" -> false,
      "otherIncomeSourceIndicator" -> false,
      "jsaIndicator" -> false,
      "name" -> "incomeSourceName",
      "taxCode" -> "1035L",
      "basisOperation" -> 1,
      "potentialUnderpayment" -> JsNull,
      "totalInYearAdjustment" -> 0,
      "inYearAdjustmentIntoCY" -> 0,
      "inYearAdjustmentIntoCYPlusOne" -> 0,
      "inYearAdjustmentFromPreviousYear" -> 0,
      "actualPUPCodedInCYPlusOneTaxYear" -> 0,
      "allowances" -> Json.arr(allowanceJson),
      "deductions" -> Json.arr(deductionJson),
      "payAndTax" -> Json.obj()
    )
  }

  private def deductionJson: JsObject = {
    Json.obj(
        "npsDescription" -> "Job Seekers",
      "amount" -> 105,
      "type" -> 18,
      "iabdSummaries" -> Json.arr(
        Json.obj(
          "amount" -> 105,
          "type" -> 18,
          "npsDescription" -> "deduction",
          "employmentId" -> 2,
          "defaultEstimatedPay" -> JsNull,
          "estimatedPaySource" -> JsNull
        )
      ),
      "sourceAmount" -> 105
    )
  }

  private def allowanceJson: JsObject = {
    Json.obj(
      "npsDescription" -> "Personal Allowance",
      "amount" -> 8105,
      "type" -> 11,
      "iabdSummaries" -> Json.arr(
        Json.obj(
          "amount" -> 8105,
          "type" -> 118,
          "npsDescription" -> "Personal Allowance (PA)",
          "employmentId" -> 1,
          "defaultEstimatedPay" -> JsNull,
          "estimatedPaySource" -> JsNull
        )
      ),
      "sourceAmount" -> 8105
    )
  }

  lazy val urlConfig = injector.instanceOf[TaxAccountHistoryUrl]

  private def connector(metrics: Metrics = injector.instanceOf[Metrics],
                        httpClient: HttpClient = injector.instanceOf[HttpClient],
                        auditor: Auditor = injector.instanceOf[Auditor],
                        config: DesConfig = injector.instanceOf[DesConfig],
                        urlConfig: TaxAccountHistoryUrl = urlConfig) = {

    new TaxAccountHistoryConnector(metrics, httpClient, auditor, config, urlConfig)

  }

  private def randomNino: Nino = new Generator(new Random).nextNino
}

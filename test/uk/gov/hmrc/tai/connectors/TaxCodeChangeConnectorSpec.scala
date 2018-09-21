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
import org.joda.time.LocalDate
import org.scalatest.BeforeAndAfterAll
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNull, JsString, JsValue, Json}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.{TaxCodeHistoryConstants, WireMockHelper}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class TaxCodeChangeConnectorSpec extends PlaySpec with WireMockHelper with BeforeAndAfterAll with MockitoSugar with TaxCodeHistoryConstants {

  "taxCodeHistory" must {
    "return tax code change response" when {
      "payroll number is returned" in {
        val testNino = randomNino
        val taxYear = TaxYear(2017)
        val payrollNumber1 = randomInt().toString()
        val payrollNumber2 = randomInt().toString()

        val url = {
          val path = new URL(urlConfig.taxCodeChangeUrl(testNino, taxYear, taxYear))
          s"${path.getPath}?${path.getQuery}"
        }

        val expectedJsonResponse =
          Json.obj("nino" -> testNino.nino,
            "taxCodeRecord" -> Seq(taxCodeHistoryJson(payrollNumber = Some(payrollNumber1), employmentType = "PRIMARY"),
              taxCodeHistoryJson(payrollNumber = Some(payrollNumber2))))


        server.stubFor(
          get(urlEqualTo(url)).willReturn(ok(expectedJsonResponse.toString))
        )

        val result = Await.result(connector().taxCodeHistory(testNino, taxYear, taxYear), 10.seconds)

        result mustEqual TaxCodeHistory(testNino.nino, Seq(
          TaxCodeRecord("1185L", Cumulative, "Employer 1", operatedTaxCode = true, LocalDate.parse("2017-06-23"), Some(payrollNumber1), pensionIndicator = false, "PRIMARY"),
          TaxCodeRecord("1185L", Cumulative, "Employer 1", operatedTaxCode = true, LocalDate.parse("2017-06-23"), Some(payrollNumber2), pensionIndicator = false, "SECONDARY")
        ))
      }

      "payroll number is not returned" in {
        val testNino = randomNino
        val taxYear = TaxYear(2017)

        val url = {
          val path = new URL(urlConfig.taxCodeChangeUrl(testNino, taxYear, taxYear))
          s"${path.getPath}?${path.getQuery}"
        }

        val taxCodeRecord = Seq(
          taxCodeHistoryJson(employmentType = "PRIMARY"),
          taxCodeHistoryJson()
        )
        val expectedJsonResponse = Json.obj("nino" -> testNino.nino, "taxCodeRecord" -> taxCodeRecord)


        server.stubFor(
          get(urlEqualTo(url)).willReturn(ok(expectedJsonResponse.toString))
        )

        val result = Await.result(connector().taxCodeHistory(testNino, taxYear, taxYear), 10.seconds)

        result mustEqual TaxCodeHistory(testNino.nino, Seq(
          TaxCodeRecord("1185L", Cumulative, "Employer 1", operatedTaxCode = true, LocalDate.parse("2017-06-23"), None, pensionIndicator = false, "PRIMARY"),
          TaxCodeRecord("1185L", Cumulative, "Employer 1", operatedTaxCode = true, LocalDate.parse("2017-06-23"), None, pensionIndicator = false, "SECONDARY")
        ))
      }

    }
  }

  "iabdDetails" must {
    "returns TaxAccountDetails" in {
      val testNino = randomNino
      val taxCodeId = 1

      val url = {
        val path = new URL(urlConfig.taxAccountHistoricSnapshotUrl(testNino, taxCodeId))
        s"${path.getPath}"
      }

      
      
      val allowanceJson = Json.obj(
        "npsDescription" -> "personal allowance",
        "amount" -> 8105,
        "type" -> 11,
        "iabdSummaries" ->Json.arr(
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

      val deductionJson = Json.obj(
        "npsDescription" -> "personal allowance",
        "amount" -> 105,
        "type" -> 18,
        "iabdSummaries" ->Json.arr(
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

      val incomeSourceJson = Json.obj(
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


      val taxAccountHistoryJson = Json.obj(
        "taxAccountId" -> 7,
        "date" -> "02/08/2018",
        "nino" -> testNino.toString,
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

      server.stubFor(
        get(urlEqualTo(url)).willReturn(ok(taxAccountHistoryJson.toString))
      )

      val iabdSummaryAllowance = TAHIabdSummary(8105, 118,"Personal Allowance (PA)", 1, None, None)
      val allowance = Allowance("personal allowance", 8105, 11, List(iabdSummaryAllowance), 8105)

      val iabdSummaryDeduction = TAHIabdSummary(105, 18,"deduction", 2, None, None)
      val deduction = Deduction("personal allowance", 105, 18, List(iabdSummaryDeduction), 105)

      val income = IncomeSources(3,1,1,754, "employmentPayeRef", false, false, false, "incomeSourceName", "1035L", 1,
                                None, 0, 0, 0, 0, 0, List(allowance), List(deduction), Json.obj())

      val expectedResult = TaxAccountDetails(7, "02/08/2018", testNino, false, TaxYear(2018),
                                             6, 1, None, None, 16956, 1, 0, List(income))


      val result = Await.result(connector().taxAccountHistory(testNino, taxCodeId), 5.seconds)

      result mustEqual expectedResult
    }

  }

  private def taxCodeHistoryJson(taxCode: String = "1185L",
                                 basisOfOperation: String = Cumulative,
                                 employerName: String = "Employer 1",
                                 operatedTaxCode: Boolean = true,
                                 p2Issued: Boolean = true,
                                 dateOfCalculation: String = "2017-06-23",
                                 payrollNumber: Option[String] = None,
                                 pensionIndicator: Boolean = false,
                                 employmentType: String = "SECONDARY"): JsValue = {

    val withOutPayroll = Json.obj("taxCode" -> taxCode,
      "basisOfOperation" -> basisOfOperation,
      "employerName" -> employerName,
      "operatedTaxCode" -> operatedTaxCode,
      "p2Issued" -> p2Issued,
      "dateOfCalculation" -> dateOfCalculation,
      "pensionIndicator" -> pensionIndicator,
      "employmentType" -> employmentType)


    if (payrollNumber.isDefined) {
      withOutPayroll + ("payrollNumber" -> JsString(payrollNumber.get))
    } else {
      withOutPayroll
    }
  }

  lazy val urlConfig = injector.instanceOf[TaxCodeChangeUrl]

  private def connector(metrics: Metrics = injector.instanceOf[Metrics],
                        httpClient: HttpClient = injector.instanceOf[HttpClient],
                        auditor: Auditor = injector.instanceOf[Auditor],
                        config: DesConfig = injector.instanceOf[DesConfig],
                        taxCodeChangeUrl: TaxCodeChangeUrl = urlConfig) = {

    new TaxCodeChangeConnector(metrics, httpClient, auditor, config, taxCodeChangeUrl)

  }

  private def randomNino: Nino = new Generator(new Random).nextNino

  private def randomInt(maxDigits: Int = 5): Int = {
    import scala.math.pow
    val random = new Random
    random.nextInt(pow(10, maxDigits).toInt)
  }
}

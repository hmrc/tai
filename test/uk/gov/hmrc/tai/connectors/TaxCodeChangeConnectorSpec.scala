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
import play.api.libs.json.{JsString, JsValue, Json}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.{TaxCodeHistory, TaxCodeRecord}
import uk.gov.hmrc.tai.util.WireMockHelper

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class TaxCodeChangeConnectorSpec extends PlaySpec with WireMockHelper with BeforeAndAfterAll with MockitoSugar {

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

        val connector = createSut()
        val result = Await.result(connector.taxCodeHistory(testNino, taxYear, taxYear), 10.seconds)

        result mustEqual TaxCodeHistory(testNino.nino, Seq(
          TaxCodeRecord("1185L", "Employer 1", operatedTaxCode = true, LocalDate.parse("2017-06-23"), Some(payrollNumber1), pensionIndicator = false, "PRIMARY"),
          TaxCodeRecord("1185L", "Employer 1", operatedTaxCode = true, LocalDate.parse("2017-06-23"), Some(payrollNumber2), pensionIndicator = false, "SECONDARY")
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

        val connector = createSut()
        val result = Await.result(connector.taxCodeHistory(testNino, taxYear, taxYear), 10.seconds)

        result mustEqual TaxCodeHistory(testNino.nino, Seq(
          TaxCodeRecord("1185L", "Employer 1", operatedTaxCode = true, LocalDate.parse("2017-06-23"), None, pensionIndicator = false, "PRIMARY"),
          TaxCodeRecord("1185L", "Employer 1", operatedTaxCode = true, LocalDate.parse("2017-06-23"), None, pensionIndicator = false, "SECONDARY")
        ))
      }

    }
  }

  private def taxCodeHistoryJson(taxCode: String = "1185L",
                                 employerName: String = "Employer 1",
                                 operatedTaxCode: Boolean = true,
                                 p2Issued: Boolean = true,
                                 dateOfCalculation: String = "2017-06-23",
                                 payrollNumber: Option[String] = None,
                                 pensionIndicator: Boolean = false,
                                 employmentType: String = "SECONDARY"): JsValue = {

    val withOutPayroll = Json.obj("taxCode" -> taxCode,
                                  "employerName" -> employerName,
                                  "operatedTaxCode" -> operatedTaxCode,
                                  "p2Issued" -> p2Issued,
                                  "dateOfCalculation" -> dateOfCalculation,
                                  "pensionIndicator" -> pensionIndicator,
                                  "employmentType" -> employmentType)


    if (payrollNumber.isDefined) {
      withOutPayroll + ("payrollNumber" -> JsString(payrollNumber.get))
    } else{
      withOutPayroll
    }
  }

  lazy val urlConfig = injector.instanceOf[TaxCodeChangeUrl]

  private def createSut(metrics: Metrics = injector.instanceOf[Metrics],
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
    random.nextInt(pow(10,maxDigits).toInt)
  }
}

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
import play.api.libs.json.Json
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.NpsJsonServiceConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.{TaxCodeHistory, TaxCodeRecord}
import uk.gov.hmrc.tai.util.WireMockHelper

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class TaxCodeChangeConnectorSpec extends PlaySpec with WireMockHelper with BeforeAndAfterAll with MockitoSugar {


  def config = injector.instanceOf[TaxCodeChangeUrl]
  "tax code change API" must {
    "return tax code change response" in {

      val metrics =  injector.instanceOf[Metrics]
      val httpClient =  injector.instanceOf[HttpClient]
      val auditor =  injector.instanceOf[Auditor]
      val npsConfig =  injector.instanceOf[NpsJsonServiceConfig]
      val testNino = randomNino
      val taxYear = TaxYear(2017)
      val payrollNumber1 = randomInt().toString()
      val payrollNumber2 = randomInt().toString()
      val employmentId1 = randomInt()
      val employmentId2 = randomInt()

      val url = new URL(config.taxCodeChangeUrl(testNino, taxYear))

      val expectedJsonResponse = Json.obj(
        "nino" -> testNino.nino,
        "taxCodeRecord" -> Seq(
          Json.obj("taxCode" -> "1185L",
                   "employerName" -> "Employer 1",
                   "operatedTaxCode" -> true,
                   "p2Issued" -> true,
                   "dateOfCalculation" -> "2017-06-23",
                   "payrollNumber" -> payrollNumber1,
                   "pensionIndicator" -> false,
                   "employmentType" -> "PRIMARY"),

          Json.obj("taxCode" -> "1185L",
                   "employerName" -> "Employer 1",
                   "operatedTaxCode" -> true,
                   "p2Issued" -> true,
                   "dateOfCalculation" -> "2017-06-23",
                   "payrollNumber" -> payrollNumber2,
                   "pensionIndicator" -> false,
                   "employmentType" -> "SECONDARY")))


      server.stubFor(
        get(urlEqualTo(url.getPath)).willReturn(ok(expectedJsonResponse.toString))
      )

      val connector = createSut(metrics, httpClient, auditor,npsConfig, config).taxCodeHistory(testNino, taxYear)
      val result = Await.result(connector, 10.seconds)

      result mustEqual TaxCodeHistory(testNino.nino, Seq(
        TaxCodeRecord("1185L", "Employer 1", operatedTaxCode = true, LocalDate.parse("2017-06-23"), payrollNumber1, pensionIndicator = false, "PRIMARY"),
        TaxCodeRecord("1185L", "Employer 1", operatedTaxCode = true, LocalDate.parse("2017-06-23"), payrollNumber2, pensionIndicator = false, "SECONDARY")
      ))
    }

  }

  implicit val headerCarrier: HeaderCarrier = mock[HeaderCarrier]


  private def createSut(metrics: Metrics,
                        httpClient: HttpClient,
                        auditor: Auditor,
                        config: NpsJsonServiceConfig,
                        taxCodeChangeUrl: TaxCodeChangeUrl) = {

    new TaxCodeChangeConnector(metrics, httpClient, auditor, config, taxCodeChangeUrl)

  }

  private def randomNino: Nino = new Generator(new Random).nextNino

  private def randomInt(maxDigits: Int = 5): Int = {
    import scala.math.pow
    val random = new Random
    random.nextInt(pow(10,maxDigits).toInt)
  }
}

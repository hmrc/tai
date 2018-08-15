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

import org.joda.time.LocalDate
import com.github.tomakehurst.wiremock.client.WireMock.{get, ok, urlEqualTo}
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
import uk.gov.hmrc.tai.model.{NonAnnualCode, TaxCodeHistory, TaxCodeRecord}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.{TaxCodeRecordConstants, WireMockHelper}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class TaxCodeChangeConnectorSpec extends PlaySpec with WireMockHelper with BeforeAndAfterAll with MockitoSugar with
  TaxCodeRecordConstants{


  def config = injector.instanceOf[TaxCodeChangeUrl]
  val testNino = randomNino

  "tax code change API" must {
    "return tax code change response" in {

      val metrics =  injector.instanceOf[Metrics]
      val httpClient =  injector.instanceOf[HttpClient]
      val auditor =  injector.instanceOf[Auditor]
      val npsConfig =  injector.instanceOf[NpsJsonServiceConfig]
      val taxYear = TaxYear(2017)

      val url = new URL(config.taxCodeChangeUrl(testNino, taxYear))

      server.stubFor(
        get(urlEqualTo(url.getPath())).willReturn(ok(jsonResponse.toString))
      )

      val result = Await.result(createSut(
        metrics,
        httpClient,
        auditor,
        npsConfig,
        config).taxCodeHistory(testNino, taxYear), 10.seconds)

      result mustEqual TaxCodeHistory(testNino.nino, Seq(
        TaxCodeRecord("1185L", "Employer 1", true, LocalDate.parse("2017-06-23"),NonAnnualCode),
        TaxCodeRecord("1185L", "Employer 1", true, LocalDate.parse("2017-06-23"),NonAnnualCode)
      ))
    }

  }



  private val jsonResponse = Json.obj(
    "nino" -> testNino.nino,
    "taxCodeRecord" -> Seq(
      Json.obj(
        "taxCode" -> "1185L",
        "employerName" -> "Employer 1",
        "operatedTaxCode" -> true,
        "p2Issued" -> true,
        "dateOfCalculation" -> "2017-06-23",
        "codeType" -> DailyCoding
      ),
      Json.obj(
        "taxCode" -> "1185L",
        "employerName" -> "Employer 1",
        "operatedTaxCode" -> true,
        "p2Issued" -> true,
        "dateOfCalculation" -> "2017-06-23",
        "codeType" -> DailyCoding
      )
    )
  )

  implicit val headerCarrier: HeaderCarrier = mock[HeaderCarrier]

  private def randomNino: Nino = new Generator(new Random).nextNino

  private def createSut(
                         metrics: Metrics,
                         httpClient: HttpClient,
                         auditor: Auditor,
                         config: NpsJsonServiceConfig,
                         taxCodeChangeUrl: TaxCodeChangeUrl) =
    new TaxCodeChangeConnector(metrics, httpClient, auditor, config, taxCodeChangeUrl)


}

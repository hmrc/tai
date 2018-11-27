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
import uk.gov.hmrc.tai.factory.TaxAccountHistoryFactory
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
    "returns a Success[Seq[CodingComponent]] for valid json of income sources" in {
      val testNino = randomNino

      val codingComponentList = ListBuffer[CodingComponent](
        CodingComponent(PersonalAllowancePA, None, 11850, "Personal Allowance", Some(11850))
      )

      val json = TaxAccountHistoryFactory.basicIncomeSourcesJson(testNino)

      testTaxAccountHistory(testNino, json, codingComponentList)
    }

    "returns a Success[Seq[CodingComponent]] for valid json of total liabilities" in {
      val testNino = randomNino

      val codingComponentList = ListBuffer[CodingComponent](
        CodingComponent(CarBenefit, Some(1), 2000, "Car Benefit", None)
      )

      val json = TaxAccountHistoryFactory.basicTotalLiabilityJson(testNino)

      testTaxAccountHistory(testNino, json, codingComponentList)
    }

    "returns a Success[Seq[CodingComponent]] for valid json of total liabilities and income sources" in {
      val testNino = randomNino

      val codingComponentList = ListBuffer[CodingComponent](
        CodingComponent(PersonalAllowancePA, None, 11850, "Personal Allowance", Some(11850)),
        CodingComponent(CarBenefit, Some(1), 2000, "Car Benefit", None)
      )

      val json = TaxAccountHistoryFactory.combinedIncomeSourcesTotalLiabilityJson(testNino)

      testTaxAccountHistory(testNino, json, codingComponentList)
    }
  }

  private def testTaxAccountHistory(nino: Nino, rawJson: JsObject, expected: ListBuffer[CodingComponent]) = {
    val taxCodeId = 1

    val url = {
      val path = new URL(urlConfig.taxAccountHistoricSnapshotUrl(nino, taxCodeId))
      s"${path.getPath}"
    }

    server.stubFor(
      get(urlEqualTo(url)).willReturn(ok(rawJson.toString))
    )

    val result = Await.result(connector().taxAccountHistory(nino, taxCodeId), 5.seconds)

    result mustEqual Success(expected)
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

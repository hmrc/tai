/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.libs.json.{JsResultException, Json}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.factory.{TaxCodeHistoryFactory, TaxCodeRecordFactory}
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.TaxCodeHistory
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.TaxCodeHistoryConstants

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class TaxCodeChangeConnectorSpec extends ConnectorBaseSpec with TaxCodeHistoryConstants {

  "taxCodeHistory" must {
    "return tax code change response" when {
      "payroll number is returned" in {
        val taxYear = TaxYear(2017)

        val url = {
          val path = new URL(urlConfig.taxCodeChangeUrl(nino, taxYear, taxYear))
          s"${path.getPath}?${path.getQuery}"
        }

        val expectedJsonResponse = TaxCodeHistoryFactory.createTaxCodeHistoryJson(nino)

        server.stubFor(
          get(urlEqualTo(url)).willReturn(ok(expectedJsonResponse.toString))
        )

        val connector = createSut()
        val result = Await.result(connector.taxCodeHistory(nino, taxYear, taxYear), 10.seconds)

        result mustEqual TaxCodeHistoryFactory.createTaxCodeHistory(nino)
      }

      "payroll number is not returned" in {
        val taxYear = TaxYear(2017)

        val url = {
          val path = new URL(urlConfig.taxCodeChangeUrl(nino, taxYear, taxYear))
          s"${path.getPath}?${path.getQuery}"
        }

        val taxCodeRecord = Seq(
          TaxCodeRecordFactory.createNoPayrollNumberJson(employmentType = Primary),
          TaxCodeRecordFactory.createNoPayrollNumberJson(employmentType = Secondary)
        )

        val expectedJsonResponse = TaxCodeHistoryFactory.createTaxCodeHistoryJson(nino, taxCodeRecord)

        server.stubFor(
          get(urlEqualTo(url)).willReturn(ok(expectedJsonResponse.toString))
        )

        val connector = createSut()
        val result = Await.result(connector.taxCodeHistory(nino, taxYear, taxYear), 10.seconds)

        result mustEqual TaxCodeHistory(
          nino.nino,
          Seq(
            TaxCodeRecordFactory.createPrimaryEmployment(payrollNumber = None),
            TaxCodeRecordFactory.createSecondaryEmployment(payrollNumber = None)
          )
        )
      }
    }

    "respond with a JsResultException when given invalid json" in {

      val taxYear = TaxYear(2017)

      val url = {
        val path = new URL(urlConfig.taxCodeChangeUrl(nino, taxYear, taxYear))
        s"${path.getPath}?${path.getQuery}"
      }

      val expectedJsonResponse = Json.obj(
        "invalid" -> "invalidjson"
      )

      server.stubFor(
        get(urlEqualTo(url)).willReturn(ok(expectedJsonResponse.toString))
      )

      val connector = createSut()
      val ex = the[JsResultException] thrownBy Await
        .result(connector.taxCodeHistory(nino, taxYear, taxYear), 10.seconds)
      ex.getMessage must include("ValidationError")
    }
  }

  lazy val urlConfig: TaxCodeChangeUrl = injector.instanceOf[TaxCodeChangeUrl]

  private def createSut(
    metrics: Metrics = injector.instanceOf[Metrics],
    httpClient: HttpClient = injector.instanceOf[HttpClient],
    auditor: Auditor = injector.instanceOf[Auditor],
    config: DesConfig = injector.instanceOf[DesConfig],
    taxCodeChangeUrl: TaxCodeChangeUrl = urlConfig) =
    new TaxCodeChangeConnector(metrics, httpClient, auditor, config, taxCodeChangeUrl)

}

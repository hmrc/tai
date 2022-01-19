/*
 * Copyright 2022 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.libs.json.{JsResultException, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.http.{BadRequestException, HeaderNames, HttpException, NotFoundException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.factory.{TaxCodeHistoryFactory, TaxCodeRecordFactory}
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.TaxCodeHistory
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.{TaiConstants, TaxCodeHistoryConstants}

import java.net.URL
import scala.language.postfixOps

class TaxCodeChangeConnectorSpec extends ConnectorBaseSpec with TaxCodeHistoryConstants with IntegrationPatience {

  private val taxYear = TaxYear()

  private lazy val url = {
    val path = new URL(urlConfig.taxCodeChangeUrl(nino, taxYear, taxYear))
    s"${path.getPath}?${path.getQuery}"
  }

  lazy val urlConfig: TaxCodeChangeUrl = injector.instanceOf[TaxCodeChangeUrl]

  def verifyOutgoingUpdateHeaders(requestPattern: RequestPatternBuilder): Unit =
    server.verify(
      requestPattern
        .withHeader("Environment", equalTo("local"))
        .withHeader("Authorization", equalTo("Bearer Local"))
        .withHeader("Content-Type", equalTo(TaiConstants.contentType))
        .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
        .withHeader(HeaderNames.xRequestId, equalTo(requestId))
        .withHeader(
          "CorrelationId",
          matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")))

  private def createSut(
    metrics: Metrics = injector.instanceOf[Metrics],
    httpClient: HttpClient = injector.instanceOf[HttpClient],
    auditor: Auditor = injector.instanceOf[Auditor],
    config: DesConfig = injector.instanceOf[DesConfig],
    taxCodeChangeUrl: TaxCodeChangeUrl = urlConfig) =
    new TaxCodeChangeConnector(metrics, httpClient, auditor, config, taxCodeChangeUrl)

  "taxCodeHistory" must {
    "return tax code change response" when {
      "payroll number is returned" in {

        val expectedJsonResponse = TaxCodeHistoryFactory.createTaxCodeHistoryJson(nino)

        server.stubFor(
          get(urlEqualTo(url)).willReturn(ok(expectedJsonResponse.toString))
        )

        val connector = createSut()

        val result = connector.taxCodeHistory(nino, taxYear, taxYear).futureValue

        result mustEqual TaxCodeHistoryFactory.createTaxCodeHistory(nino)

        verifyOutgoingUpdateHeaders(getRequestedFor(urlEqualTo(url)))
      }

      "payroll number is not returned" in {

        val taxCodeRecord = Seq(
          TaxCodeRecordFactory.createNoPayrollNumberJson(employmentType = Primary),
          TaxCodeRecordFactory.createNoPayrollNumberJson(employmentType = Secondary)
        )

        val expectedJsonResponse = TaxCodeHistoryFactory.createTaxCodeHistoryJson(nino, taxCodeRecord)

        server.stubFor(
          get(urlEqualTo(url)).willReturn(ok(expectedJsonResponse.toString))
        )

        val connector = createSut()

        val result = connector.taxCodeHistory(nino, taxYear, taxYear).futureValue

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

      val expectedJsonResponse = Json.obj(
        "invalid" -> "invalidjson"
      )

      server.stubFor(
        get(urlEqualTo(url)).willReturn(ok(expectedJsonResponse.toString))
      )

      val connector = createSut()

      val result = connector.taxCodeHistory(nino, taxYear, taxYear).failed.futureValue

      result mustBe a[JsResultException]

    }
  }

  "return an error" when {
    "a 400 occurs" in {

      server.stubFor(get(urlEqualTo(url)).willReturn(aResponse().withStatus(BAD_REQUEST)))

      val connector = createSut()

      val result = connector.taxCodeHistory(nino, taxYear, taxYear).failed.futureValue

      result mustBe a[BadRequestException]
    }

    "a 404 occurs" in {

      server.stubFor(get(urlEqualTo(url)).willReturn(aResponse().withStatus(NOT_FOUND)))

      val connector = createSut()

      val result = connector.taxCodeHistory(nino, taxYear, taxYear).failed.futureValue

      result mustBe a[NotFoundException]
    }

    List(
      IM_A_TEAPOT,
      INTERNAL_SERVER_ERROR,
      SERVICE_UNAVAILABLE
    ).foreach { httpResponse =>
      s"a $httpResponse occurs" in {

        server.stubFor(get(urlEqualTo(url)).willReturn(aResponse().withStatus(httpResponse)))

        val connector = createSut()

        val result = connector.taxCodeHistory(nino, taxYear, taxYear).failed.futureValue

        result mustBe a[HttpException]
      }
    }
  }
}

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

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, equalTo, getRequestedFor, matching, urlEqualTo}
import data.RTIData.nino
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import play.api.test.Injecting
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HeaderNames, HttpException, NotFoundException}
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.model.domain.BankAccount
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.{TaiConstants, WireMockHelper}

import scala.concurrent.ExecutionContext

class BbsiConnectorSpec extends ConnectorBaseSpec with ScalaFutures with IntegrationPatience {

  lazy val connector = inject[BbsiConnector]

  private val taxYear = TaxYear()

  lazy val config = inject[DesConfig]

  val url = s"/pre-population-of-investment-income/nino/${nino.nino.take(8)}/tax-year/2021"

  private val singleBankAccount = Json.obj(
    "nino"    -> nino.nino,
    "taxYear" -> "2016",
    "accounts" -> Json.arr(
      Json.obj(
        "accountNumber"          -> "*****5566",
        "sortCode"               -> "112233",
        "bankName"               -> "ACCOUNT ONE",
        "grossInterest"          -> 1500.5,
        "source"                 -> "Customer",
        "numberOfAccountHolders" -> 1
      ))
  )

  private val multipleBankAccounts = Json.obj(
    "nino"    -> nino.nino,
    "taxYear" -> "2016",
    "accounts" -> Json.arr(
      Json.obj(
        "accountNumber"          -> "*****5566",
        "sortCode"               -> "112233",
        "bankName"               -> "ACCOUNT ONE",
        "grossInterest"          -> 1500.5,
        "source"                 -> "Customer",
        "numberOfAccountHolders" -> 1
      ),
      Json.obj(
        "accountNumber"          -> "*****5566",
        "sortCode"               -> "112233",
        "bankName"               -> "ACCOUNT ONE",
        "grossInterest"          -> 1500.5,
        "source"                 -> "Customer",
        "numberOfAccountHolders" -> 1
      ),
      Json.obj(
        "accountNumber"          -> "*****5566",
        "sortCode"               -> "112233",
        "bankName"               -> "ACCOUNT ONE",
        "grossInterest"          -> 1500.5,
        "source"                 -> "Customer",
        "numberOfAccountHolders" -> 1
      )
    )
  )

  private val bankAccount = BankAccount(
    accountNumber = Some("*****5566"),
    sortCode = Some("112233"),
    bankName = Some("ACCOUNT ONE"),
    grossInterest = 1500.5,
    source = Some("Customer"),
    numberOfAccountHolders = Some(1)
  )

  "BbsiConnector" should {
    "return Sequence of BankAccounts" when {
      "api returns one bank account" in {

        server.stubFor(
          WireMock
            .get(urlEqualTo(url))
            .willReturn(aResponse().withStatus(OK).withBody(singleBankAccount.toString())))

        val result = connector.bankAccounts(nino, taxYear)

        result.futureValue mustBe Seq(bankAccount)

        server.verify(
          getRequestedFor(urlEqualTo(url))
            .withHeader("Environment", equalTo(config.environment))
            .withHeader("Authorization", equalTo(s"Bearer ${config.authorization}"))
            .withHeader("Content-Type", equalTo(TaiConstants.contentType))
            .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
            .withHeader(HeaderNames.xRequestId, equalTo(requestId))
            .withHeader(
              "CorrelationId",
              matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"))
        )
      }

      "api returns three bank accounts" in {

        server.stubFor(
          WireMock
            .get(urlEqualTo(url))
            .willReturn(aResponse().withStatus(OK).withBody(multipleBankAccounts.toString())))

        val result = connector.bankAccounts(nino, taxYear)

        result.futureValue mustBe Seq(bankAccount, bankAccount, bankAccount)
      }
    }

    "return empty bank accounts" when {
      "api doesn't return account" in {

        val json: JsValue = Json.obj("nino" -> nino.nino, "taxYear" -> "2016", "accounts" -> Json.arr())

        server.stubFor(
          WireMock
            .get(urlEqualTo(url))
            .willReturn(aResponse().withStatus(OK).withBody(json.toString())))

        val result = connector.bankAccounts(nino, taxYear)

        result.futureValue mustBe Nil
      }
    }

    "return exception" when {
      "api returns invalid json" in {

        val json: JsValue = Json.obj("nino" -> nino.nino, "taxYear" -> "2016")

        server.stubFor(
          WireMock
            .get(urlEqualTo(url))
            .willReturn(aResponse().withStatus(OK).withBody(json.toString())))

        val result = connector.bankAccounts(nino, taxYear).failed.futureValue

        result.getMessage mustBe "Invalid Json"

        result mustBe a[RuntimeException]
      }
    }

    "return error" when {
      "a 400 occurs" in {

        server.stubFor(
          WireMock
            .get(urlEqualTo(url))
            .willReturn(aResponse().withStatus(BAD_REQUEST)))

        val result = connector.bankAccounts(nino, taxYear).failed.futureValue

        result mustBe a[BadRequestException]
      }
      "a 404" in {

        server.stubFor(
          WireMock
            .get(urlEqualTo(url))
            .willReturn(aResponse().withStatus(NOT_FOUND)))

        val result = connector.bankAccounts(nino, taxYear).failed.futureValue

        result mustBe a[NotFoundException]
      }

      List(
        IM_A_TEAPOT,
        INTERNAL_SERVER_ERROR,
        SERVICE_UNAVAILABLE
      ).foreach { httpResponse =>
        s"a $httpResponse will throw a HttpException" in {

          server.stubFor(
            WireMock
              .get(urlEqualTo(url))
              .willReturn(aResponse().withStatus(httpResponse)))

          val result = connector.bankAccounts(nino, taxYear).failed.futureValue

          result mustBe a[HttpException]
        }
      }
    }
  }
}

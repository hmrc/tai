/*
 * Copyright 2020 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, urlEqualTo, urlPathEqualTo}
import com.github.tomakehurst.wiremock.http.Fault
import org.scalatestplus.play.PlaySpec
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.test.Injecting
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.domain.BankAccount
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.WireMockHelper

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Random

class BbsiConnectorSpec extends PlaySpec with WireMockHelper with Injecting {

  private val taxYear = TaxYear()
  private val nino = new Generator(Random).nextNino

  lazy val bbsiUrls: BbsiUrls = inject[BbsiUrls]

  lazy val url = s"/pre-population-of-investment-income/nino/${nino.withoutSuffix}/tax-year/${taxYear.year}"

  implicit lazy val ec: ExecutionContext = inject[ExecutionContext]
  implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  lazy val sut = new BbsiConnector(inject[Metrics], inject[HttpClient], bbsiUrls, inject[DesConfig])

  "BbsiConnector" when {

    "bankAccounts is called" must {

      "return a 200 response" in {

        val json = """{"message": "Success"}"""

        println("\n\n\n")
        println(s"URL: $url")
        println("\n\n\n")

        server.stubFor(
          get(urlEqualTo(url)).willReturn(aResponse().withStatus(OK).withBody(json))
        )

        val result = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)

        result.status mustBe OK
        result.json mustBe Json.parse(json)
      }

      "return a 400 response" in {

        server.stubFor(
          get(urlEqualTo(url))
            .willReturn(aResponse().withStatus(BAD_REQUEST).withBody("""{"reason": "Text from reason column"}"""))
        )

        val result = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)

        result.status mustBe BAD_REQUEST
      }

      "return a 404 response" in {

        server.stubFor(
          get(urlEqualTo(url)).willReturn(aResponse().withStatus(NOT_FOUND))
        )

        val result = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)

        result.status mustBe NOT_FOUND
      }

      "return a 500 response" in {

        server.stubFor(
          get(urlEqualTo(url)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
        )

        val result = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)

        result.status mustBe INTERNAL_SERVER_ERROR
      }

      "return a 503 response" in {

        server.stubFor(
          get(urlEqualTo(url)).willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE))
        )

        val result = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)

        result.status mustBe SERVICE_UNAVAILABLE
      }

      "handle exceptions" in {

        server.stubFor(
          get(urlEqualTo(url)).willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK))
        )

        val result = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)

        result.status mustBe INTERNAL_SERVER_ERROR
      }
    }

//    "return Sequence of BankAccounts" when {
//
//      "api returns bank accounts" in {
//        val captor = ArgumentCaptor.forClass(classOf[HeaderCarrier])
//
//        val mockHttpHandler = mock[HttpHandler]
//        when(mockHttpHandler.getFromApiV2(any(), any())(any()))
//          .thenReturn(Future.successful(HttpResponse(OK, multipleBankAccounts.toString())))
//
//        val mockDesConfig = mock[DesConfig]
//        when(mockDesConfig.environment)
//          .thenReturn("ist0")
//        when(mockDesConfig.authorization)
//          .thenReturn("123")
//
//        val sut = createSut(mockHttpHandler, mock[BbsiUrls], mockDesConfig)
//        val result = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)
//
//        result mustBe Right(Seq(bankAccount, bankAccount, bankAccount))
//
//        verify(mockHttpHandler, times(1))
//          .getFromApiV2(any(), meq(APITypes.BbsiAPI))(captor.capture())
//
//        captor.getValue.extraHeaders must contain("Environment"   -> "ist0")
//        captor.getValue.extraHeaders must contain("Authorization" -> s"Bearer 123")
//
//      }
//
//      "api return bank account" in {
//        val mockHttpHandler = mock[HttpHandler]
//        when(mockHttpHandler.getFromApiV2(any(), any())(any()))
//          .thenReturn(Future.successful(HttpResponse(OK, singleBankAccount.toString())))
//
//        val sut = createSut(mockHttpHandler, mock[BbsiUrls], mock[DesConfig])
//        val result = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)
//
//        result mustBe Right(Seq(bankAccount))
//      }
//    }
//
//    "return empty bank accounts" when {
//      "api doesn't return account" in {
//        val json: JsValue = Json.obj("nino" -> nino.nino, "taxYear" -> "2016", "accounts" -> Json.arr())
//
//        val mockHttpHandler = mock[HttpHandler]
//        when(mockHttpHandler.getFromApiV2(any(), any())(any()))
//          .thenReturn(Future.successful(HttpResponse(OK, json.toString())))
//
//        val sut = createSut(mockHttpHandler, mock[BbsiUrls], mock[DesConfig])
//        val result = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)
//
//        result mustBe Right(Nil)
//      }
//    }
//
//    "return Left" when {
//      "api returns invalid json" in {
//        val json: JsValue = Json.obj("nino" -> nino.nino, "taxYear" -> "2016")
//
//        val mockHttpHandler = mock[HttpHandler]
//        when(mockHttpHandler.getFromApiV2(any(), any())(any()))
//          .thenReturn(Future.successful(HttpResponse(OK, json.toString())))
//
//        val sut = createSut(mockHttpHandler, mock[BbsiUrls], mock[DesConfig])
//        val res = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)
//
//        res.left.get.status mustBe INTERNAL_SERVER_ERROR
//        res.left.get.body mustBe "Could not parse Json"
//      }
//
//      "api returns locked" in {
//
//        val mockHttpHandler = mock[HttpHandler]
//        val message = "Account was locked"
//
//        when(mockHttpHandler.getFromApiV2(any(), any())(any()))
//          .thenReturn(Future.successful(HttpResponse(LOCKED, message)))
//
//        val sut = createSut(mockHttpHandler, mock[BbsiUrls], mock[DesConfig])
//        val res = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)
//
//        res.left.get.status mustBe LOCKED
//        res.left.get.body mustBe message
//      }
//
//      "api returns a 4xx response" in {
//
//        val mockHttpHandler = mock[HttpHandler]
//        val message = "Bad Request"
//
//        when(mockHttpHandler.getFromApiV2(any(), any())(any()))
//          .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, message)))
//
//        val sut = createSut(mockHttpHandler, mock[BbsiUrls], mock[DesConfig])
//        val res = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)
//
//        res.left.get.status mustBe BAD_REQUEST
//        res.left.get.body mustBe message
//      }
//
//      "api returns a 5xx response" in {
//
//        val mockHttpHandler = mock[HttpHandler]
//        val message = "An error occurred"
//
//        when(mockHttpHandler.getFromApiV2(any(), any())(any()))
//          .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, message)))
//
//        val sut = createSut(mockHttpHandler, mock[BbsiUrls], mock[DesConfig])
//        val res = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)
//
//        res.left.get.status mustBe INTERNAL_SERVER_ERROR
//        res.left.get.body mustBe message
//      }
//
//      "api throws an exception" in {
//
//        val mockHttpHandler = mock[HttpHandler]
//        val message = "An error occurred"
//
//        when(mockHttpHandler.getFromApiV2(any(), any())(any()))
//          .thenReturn(Future.failed(new Exception(message)))
//
//        val sut = createSut(mockHttpHandler, mock[BbsiUrls], mock[DesConfig])
//        val res = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)
//
//        res.left.get.status mustBe INTERNAL_SERVER_ERROR
//        res.left.get.body mustBe message
//      }
//    }
  }
  private val bankAccount = BankAccount(
    accountNumber = Some("*****5566"),
    sortCode = Some("112233"),
    bankName = Some("ACCOUNT ONE"),
    grossInterest = 1500.5,
    source = Some("Customer"),
    numberOfAccountHolders = Some(1)
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
}

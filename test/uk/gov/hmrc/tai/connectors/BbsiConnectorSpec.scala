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

import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.{times, verify, when}
import org.mockito.ArgumentCaptor
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.model.domain.BankAccount
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class BbsiConnectorSpec extends BaseSpec {

  "BbsiConnector" should {

    "return Sequence of BankAccounts" when {
      "api returns bank accounts" in {
        val captor = ArgumentCaptor.forClass(classOf[HeaderCarrier])

        val mockHttpHandler = mock[HttpHandler]
        when(mockHttpHandler.getFromApi(any(), any())(any()))
          .thenReturn(Future.successful(multipleBankAccounts))

        val mockDesConfig = mock[DesConfig]
        when(mockDesConfig.environment)
          .thenReturn("ist0")
        when(mockDesConfig.authorization)
          .thenReturn("123")

        val sut = createSut(mockHttpHandler, mock[BbsiUrls], mockDesConfig)
        val result = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)

        result mustBe Seq(bankAccount, bankAccount, bankAccount)

        verify(mockHttpHandler, times(1))
          .getFromApi(any(), meq(APITypes.BbsiAPI))(captor.capture())

        captor.getValue.extraHeaders must contain("Environment"   -> "ist0")
        captor.getValue.extraHeaders must contain("Authorization" -> s"Bearer 123")

      }

      "api return bank account" in {
        val mockHttpHandler = mock[HttpHandler]
        when(mockHttpHandler.getFromApi(any(), any())(any()))
          .thenReturn(Future.successful(singleBankAccount))

        val sut = createSut(mockHttpHandler, mock[BbsiUrls], mock[DesConfig])
        val result = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)

        result mustBe Seq(bankAccount)
      }
    }

    "return empty bank accounts" when {
      "api doesn't return account" in {
        val json: JsValue = Json.obj("nino" -> nino.nino, "taxYear" -> "2016", "accounts" -> Json.arr())

        val mockHttpHandler = mock[HttpHandler]
        when(mockHttpHandler.getFromApi(any(), any())(any()))
          .thenReturn(Future.successful(json))

        val sut = createSut(mockHttpHandler, mock[BbsiUrls], mock[DesConfig])
        val result = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)

        result mustBe Nil
      }
    }

    "return exception" when {
      "api returns invalid json" in {
        val json: JsValue = Json.obj("nino" -> nino.nino, "taxYear" -> "2016")

        val mockHttpHandler = mock[HttpHandler]
        when(mockHttpHandler.getFromApi(any(), any())(any()))
          .thenReturn(Future.successful(json))

        val sut = createSut(mockHttpHandler, mock[BbsiUrls], mock[DesConfig])
        val ex = the[RuntimeException] thrownBy Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)

        ex.getMessage mustBe "Invalid Json"
      }
    }
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

  private val taxYear = TaxYear()

  private def createSut(httpHandler: HttpHandler, urls: BbsiUrls, config: DesConfig) =
    new BbsiConnector(httpHandler, urls, config)
}

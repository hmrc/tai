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

package uk.gov.hmrc.tai.repositories

import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.{never, times, verify, when}
import play.api.libs.json.Json
import play.api.http.Status._
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.tai.connectors.{BbsiConnector, CacheConnector}
import uk.gov.hmrc.tai.model.domain.BankAccount
import uk.gov.hmrc.tai.model.domain.formatters.BbsiHodFormatters
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

class BbsiRepositorySpec extends BaseSpec {

  private val BBSIKey = "BankAndBuildingSocietyInterest"

  val bankAccountJson =
    s"""{
       | "nino": "${nino.withoutSuffix}",
       | "taxYear": "2016",
       | "accounts": [
       |   {
       |    "accountNumber": "$randomNumberAsString",
       |    "sortCode": "$randomNumberAsString",
       |    "bankName": "TEST",
       |    "numberOfAccountHolders": 1,
       |    "grossInterest": 8.90,
       |    "percentageSplit": 100,
       |    "source": "Pre-pop Bank / Building Society"
       |   },
       |   {
       |    "accountNumber": "$randomNumberAsString",
       |    "sortCode": "$randomNumberAsString",
       |    "bankName": "TEST",
       |    "numberOfAccountHolders": 2,
       |    "grossInterest": 7.60,
       |    "percentageSplit": 900,
       |    "source": "Pre-pop Bank / Building Society"
       |   }
       | ]
        }""".stripMargin

  val bankAccounts = Json.parse(bankAccountJson).as[Seq[BankAccount]](BbsiHodFormatters.bankAccountHodReads)
  val bankAccount1 = bankAccounts.head.copy(id = 1)
  val bankAccount2 = bankAccounts.last.copy(id = 2)

  "Bbsi Repository" must {

    "return accounts" when {
      "data exist in cache" in {
        val mockBbsiConnector = mock[BbsiConnector]
        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findOptSeq[BankAccount](any(), any())(any()))
          .thenReturn(Future.successful(Some(Seq(bankAccount1))))

        val sut = createSUT(mockCacheConnector, mockBbsiConnector)
        val result = Await.result(sut.bbsiDetails(nino, TaxYear()), 5.seconds)

        result mustBe Right(Seq(bankAccount1))
        verify(mockCacheConnector, times(1))
          .findOptSeq[BankAccount](any(), meq(BBSIKey))(any())
        verify(mockBbsiConnector, never())
          .bankAccounts(any(), any())(any())
      }

      "data doesn't exist in cache" in {

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findOptSeq[BankAccount](any(), any())(any()))
          .thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdateSeq[BankAccount](any(), any(), any())(any()))
          .thenReturn(Future.successful(Seq(bankAccount1, bankAccount2)))

        val mockBbsiConnector = mock[BbsiConnector]
        when(mockBbsiConnector.bankAccounts(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, bankAccountJson)))

        val sut = createSUT(mockCacheConnector, mockBbsiConnector)
        val result = Await.result(sut.bbsiDetails(nino, TaxYear()), 5.seconds)

        result mustBe Right(Seq(bankAccount1, bankAccount2))

        verify(mockCacheConnector)
          .findOptSeq[BankAccount](any(), meq(BBSIKey))(any())
        verify(mockCacheConnector)
          .createOrUpdateSeq[BankAccount](any(), meq(Seq(bankAccount1, bankAccount2)), any())(any())
        verify(mockBbsiConnector)
          .bankAccounts(any(), any())(any())
      }
    }

    "return no bank accounts" when {
      "BBSI connector returns no bank accounts" in {

        val emptyBankAccountJson =
          s"""{
             | "nino": "${nino.withoutSuffix}",
             | "taxYear": "2016",
             | "accounts": []
              }""".stripMargin

        val emptySeq = Seq.empty[BankAccount]

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findOptSeq[BankAccount](any(), any())(any()))
          .thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdateSeq[BankAccount](any(), any(), any())(any()))
          .thenReturn(Future.successful(emptySeq))

        val mockBbsiConnector = mock[BbsiConnector]
        when(mockBbsiConnector.bankAccounts(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, emptyBankAccountJson)))

        val sut = createSUT(mockCacheConnector, mockBbsiConnector)
        val result = Await.result(sut.bbsiDetails(nino, TaxYear()), 5.seconds)

        result.right.get mustBe Seq.empty[BankAccount]

        verify(mockCacheConnector)
          .findOptSeq[BankAccount](any(), meq(BBSIKey))(any())
        verify(mockCacheConnector)
          .createOrUpdateSeq[BankAccount](any(), meq(emptySeq), any())(any())
        verify(mockBbsiConnector)
          .bankAccounts(any(), any())(any())
      }
    }

    "return a http response" when {
      "connector returns a left" in {

        val response = HttpResponse(500, "An error occurred")

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findOptSeq[BankAccount](any(), any())(any()))
          .thenReturn(Future.successful(None))

        val mockBbsiConnector = mock[BbsiConnector]
        when(mockBbsiConnector.bankAccounts(any(), any())(any()))
          .thenReturn(Future.successful(response))

        val sut = createSUT(mockCacheConnector, mockBbsiConnector)
        val result = Await.result(sut.bbsiDetails(nino, TaxYear()), 5.seconds)

        result.left.get.status mustBe response.status
        result.left.get.body mustBe response.body

        verify(mockCacheConnector)
          .findOptSeq[BankAccount](any(), meq(BBSIKey))(any())
        verify(mockCacheConnector, never())
          .createOrUpdateSeq[BankAccount](any(), any(), any())(any())
        verify(mockBbsiConnector)
          .bankAccounts(any(), any())(any())
      }
    }
  }

  private def randomNumberAsString = Random.nextInt(999999).toString
  private def createSUT(cacheConnector: CacheConnector, bbsiConnector: BbsiConnector) =
    new BbsiRepository(cacheConnector, bbsiConnector)
}

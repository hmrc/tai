/*
 * Copyright 2019 HM Revenue & Customs
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

import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{never, times, verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.connectors.{BbsiConnector, CacheConnector}
import uk.gov.hmrc.tai.model.domain.BankAccount
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

class BbsiRepositorySpec extends PlaySpec with MockitoSugar {

  "Bbsi Repository" must {

    "return accounts" when {
      "data exist in cache" in {
        val mockBbsiConnector = mock[BbsiConnector]
        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findOptSeq[BankAccount](any(), any())(any()))
          .thenReturn(Future.successful(Some(Seq(bankAccount))))

        val sut = createSUT(mockCacheConnector, mockBbsiConnector)
        val result = Await.result(sut.bbsiDetails(nino, TaxYear()), 5.seconds)

        result mustBe Seq(bankAccount)
        verify(mockCacheConnector, times(1))
          .findOptSeq[BankAccount](any(), Matchers.eq(sut.BBSIKey))(any())
        verify(mockBbsiConnector, never())
          .bankAccounts(any(), any())(any())
      }

      "data doesn't exist in cache" in {
        val expectedBankAccount1 = bankAccount.copy(id = 1)
        val expectedBankAccount2 = bankAccount.copy(id = 2)

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findOptSeq[BankAccount](any(), any())(any()))
          .thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdateSeq[BankAccount](any(), any(), any())(any()))
          .thenReturn(Future.successful(Seq(expectedBankAccount1, expectedBankAccount2)))

        val mockBbsiConnector = mock[BbsiConnector]
        when(mockBbsiConnector.bankAccounts(any(), any())(any()))
          .thenReturn(Future.successful(Seq(bankAccount, bankAccount)))

        val sut = createSUT(mockCacheConnector, mockBbsiConnector)
        val result = Await.result(sut.bbsiDetails(nino, TaxYear()), 5.seconds)

        result mustBe Seq(expectedBankAccount1, expectedBankAccount2)

        verify(mockCacheConnector, times(1))
          .findOptSeq[BankAccount](any(), Matchers.eq(sut.BBSIKey))(any())
        verify(mockCacheConnector, times(1))
          .createOrUpdateSeq[BankAccount](any(), Matchers.eq(Seq(expectedBankAccount1, expectedBankAccount2)), any())(
            any())
        verify(mockBbsiConnector, times(1))
          .bankAccounts(any(), any())(any())
      }
    }

  }

  private val bankAccount = BankAccount(0, Some("123"), Some("123456"), Some("TEST"), 10.80, Some("Customer"), Some(1))
  private implicit val hc = HeaderCarrier(sessionId = Some(SessionId("TEST")))
  private val nino = new Generator(new Random).nextNino

  private def createSUT(cacheConnector: CacheConnector, bbsiConnector: BbsiConnector) =
    new BbsiRepository(cacheConnector, bbsiConnector)
}

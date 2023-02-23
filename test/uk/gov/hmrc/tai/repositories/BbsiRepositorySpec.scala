/*
 * Copyright 2023 HM Revenue & Customs
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
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.tai.connectors.BbsiConnector
import uk.gov.hmrc.tai.model.domain.BankAccount
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.cache.TaiCacheRepository
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class BbsiRepositorySpec extends BaseSpec {

  "Bbsi Repository" must {

    "return accounts" when {
      "data exist in cache" in {
        val mockBbsiConnector = mock[BbsiConnector]
        val mockCacheConnector = mock[TaiCacheRepository]
        when(mockCacheConnector.findOptSeq[BankAccount](any(), any())(any()))
          .thenReturn(Future.successful(Some(Seq(bankAccount))))

        val sut = createSUT(mockCacheConnector, mockBbsiConnector)

        val result = sut.bbsiDetails(nino, TaxYear()).futureValue

        result mustBe Seq(bankAccount)
        verify(mockCacheConnector, times(1))
          .findOptSeq[BankAccount](any(), meq(sut.BBSIKey))(any())
        verify(mockBbsiConnector, never())
          .bankAccounts(any(), any())(any())
      }

      "data doesn't exist in cache" in {
        val expectedBankAccount1 = bankAccount.copy(id = 1)
        val expectedBankAccount2 = bankAccount.copy(id = 2)

        val mockCacheConnector = mock[TaiCacheRepository]
        when(mockCacheConnector.findOptSeq[BankAccount](any(), any())(any()))
          .thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdateSeq[BankAccount](any(), any(), any())(any()))
          .thenReturn(Future.successful(Seq(expectedBankAccount1, expectedBankAccount2)))

        val mockBbsiConnector = mock[BbsiConnector]
        when(mockBbsiConnector.bankAccounts(any(), any())(any()))
          .thenReturn(Future.successful(Seq(bankAccount, bankAccount)))

        val sut = createSUT(mockCacheConnector, mockBbsiConnector)
        val result = sut.bbsiDetails(nino, TaxYear()).futureValue

        result mustBe Seq(expectedBankAccount1, expectedBankAccount2)

        verify(mockCacheConnector, times(1))
          .findOptSeq[BankAccount](any(), meq(sut.BBSIKey))(any())
        verify(mockCacheConnector, times(1))
          .createOrUpdateSeq[BankAccount](any(), meq(Seq(expectedBankAccount1, expectedBankAccount2)), any())(any())
        verify(mockBbsiConnector, times(1))
          .bankAccounts(any(), any())(any())
      }
    }
  }

  private val bankAccount = BankAccount(0, Some("123"), Some("123456"), Some("TEST"), 10.80, Some("Customer"), Some(1))

  private def createSUT(taiCacheRepository: TaiCacheRepository, bbsiConnector: BbsiConnector) =
    new BbsiRepository(taiCacheRepository, bbsiConnector)
}

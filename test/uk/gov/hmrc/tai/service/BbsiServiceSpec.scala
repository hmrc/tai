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

package uk.gov.hmrc.tai.service

import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{doNothing, verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.model.CloseAccountRequest
import uk.gov.hmrc.tai.model.domain.BankAccount
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.BbsiRepository
import uk.gov.hmrc.tai.util.IFormConstants

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

class BbsiServiceSpec extends PlaySpec with MockitoSugar {

  "Bbsi Service" must {
    "return bank accounts" in {
      val mockBbsiRepository = mock[BbsiRepository]
      when(mockBbsiRepository.bbsiDetails(any(), any())(any()))
        .thenReturn(Future.successful(Seq(bankAccount)))

      val sut = createSUT(mockBbsiRepository, mock[IFormSubmissionService], mock[Auditor])
      val result = Await.result(sut.bbsiDetails(nino, TaxYear()), 5.seconds)

      result mustBe Seq(bankAccount)
    }

    "return bank account" in {
      val mockBbsiRepository = mock[BbsiRepository]
      when(mockBbsiRepository.bbsiDetails(any(), any())(any()))
        .thenReturn(Future.successful(Seq(bankAccount, bankAccount.copy(id = 2))))

      val sut = createSUT(mockBbsiRepository, mock[IFormSubmissionService], mock[Auditor])
      val result = Await.result(sut.bbsiAccount(nino, 1), 5.seconds)

      result mustBe Some(bankAccount)
    }
  }

  "closeBankAccount" must {
    "return an envelopeId" when {
      "given valid inputs" in {
        val mockBbsiRepository = mock[BbsiRepository]
        val mockIFormSubmissionService = mock[IFormSubmissionService]

        when(mockIFormSubmissionService.uploadIForm(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful("1"))
        when(mockBbsiRepository.bbsiDetails(any(), any())(any()))
          .thenReturn(Future.successful(Seq(bankAccount)))

        val mockAuditor = mock[Auditor]
        doNothing().when(mockAuditor)
          .sendDataEvent(any(), any(), any(),any())(any())

        val sut = createSUT(mockBbsiRepository, mockIFormSubmissionService, mockAuditor)
        val result = Await.result(sut.closeBankAccount(nino, 1, CloseAccountRequest(new LocalDate(2017,6,20), Some(0))), 5.seconds)

        result mustBe "1"
        verify(mockAuditor)
          .sendDataEvent(Matchers.eq("CloseBankAccountRequest"), any(), any(),any())(any())
      }
    }

    "return exception" when {
      "id is not present" in {
        val mockBbsiRepository = mock[BbsiRepository]
        when(mockBbsiRepository.bbsiDetails(any(), any())(any()))
          .thenReturn(Future.successful(Seq(bankAccount)))

        val sut = createSUT(mockBbsiRepository, mock[IFormSubmissionService], mock[Auditor])
        the[BankAccountNotFound] thrownBy
          Await.result(sut.closeBankAccount(nino, 49, CloseAccountRequest(new LocalDate(2017,6,20), Some(0))), 5.seconds)
      }
    }
  }

  "removeBankAccount" must {
    "return an envelopeId" when {
      "given valid inputs" in {
        val mockBbsiRepository = mock[BbsiRepository]
        when(mockBbsiRepository.bbsiDetails(any(), any())(any()))
          .thenReturn(Future.successful(Seq(bankAccount)))

        val mockIFormSubmissionService = mock[IFormSubmissionService]
        when(mockIFormSubmissionService.uploadIForm(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful("1"))

        val mockAuditor = mock[Auditor]
        doNothing().when(mockAuditor)
          .sendDataEvent(any(), any(), any(),any())(any())

        val sut = createSUT(mockBbsiRepository, mockIFormSubmissionService, mockAuditor)
        val result = Await.result(sut.removeIncorrectBankAccount(nino, 1), 5.seconds)

        result mustBe "1"

        verify(mockAuditor)
          .sendDataEvent(Matchers.eq(IFormConstants.RemoveBankAccountRequest), any(), any(),any())(any())
      }
    }
    "return exception" when {
      "id is not present" in {
        val mockBbsiRepository = mock[BbsiRepository]
        when(mockBbsiRepository.bbsiDetails(any(), any())(any()))
          .thenReturn(Future.successful(Seq(bankAccount)))

        val sut = createSUT(mockBbsiRepository, mock[IFormSubmissionService], mock[Auditor])
        the[BankAccountNotFound] thrownBy Await.result(sut.removeIncorrectBankAccount(nino, 49), 5.seconds)
      }
    }
  }

  "updateBankAccount" must {
    "return an envelopeId" when {
      "given valid inputs" in {

        val mockBbsiRepository = mock[BbsiRepository]
        when(mockBbsiRepository.bbsiDetails(any(), any())(any()))
          .thenReturn(Future.successful(Seq(bankAccount)))

        val mockIFormSubmissionService = mock[IFormSubmissionService]
        when(mockIFormSubmissionService.uploadIForm(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful("1"))

        val mockAuditor = mock[Auditor]
        doNothing().when(mockAuditor)
          .sendDataEvent(any(), any(), any(),any())(any())

        val sut = createSUT(mockBbsiRepository, mockIFormSubmissionService, mockAuditor)
        val result = Await.result(sut.updateBankAccountInterest(nino, 1, 1000), 5.seconds)

        result mustBe "1"
        verify(mockAuditor)
            .sendDataEvent(Matchers.eq(IFormConstants.UpdateBankAccountRequest), any(), any(),any())(any())
      }
    }

    "return exception" when {
      "id is not present" in {
        val mockBbsiRepository = mock[BbsiRepository]
        val sut = createSUT(mockBbsiRepository, mock[IFormSubmissionService], mock[Auditor])

        when(mockBbsiRepository.bbsiDetails(any(), any())(any()))
          .thenReturn(Future.successful(Seq(bankAccount)))

        the[BankAccountNotFound] thrownBy Await.result(sut.updateBankAccountInterest(nino, 49, 1000), 5.seconds)
      }
    }
  }

  private val bankAccount = BankAccount(1, Some("123"), Some("123456"), Some("TEST"), 10.80, Some("Customer"), Some(1))
  private val nino = new Generator(new Random).nextNino
  private implicit val hc = HeaderCarrier(sessionId = Some(SessionId("TEST")))

  private def createSUT(bbsiRepository: BbsiRepository, iFormSubmissionService: IFormSubmissionService, auditor: Auditor) =
    new BbsiService(bbsiRepository, iFormSubmissionService, auditor)
}
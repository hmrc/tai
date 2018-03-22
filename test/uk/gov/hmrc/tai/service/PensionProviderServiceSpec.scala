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
import org.mockito.Mockito.{doNothing, times, verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.model.domain.AddPensionProvider
import uk.gov.hmrc.tai.util.IFormConstants

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class PensionProviderServiceSpec extends PlaySpec with MockitoSugar {

  "AddPensionProvider" must {
    "return an envelopeId" when {
      "given valid inputs" in {
        val addPensionProvider = AddPensionProvider("testName", new LocalDate(2017,6,9), "1234", "Yes", Some("123456789"))

        val mockIFormSubmissionService = mock[IFormSubmissionService]
        when(mockIFormSubmissionService.uploadIForm(Matchers.eq(nino), Matchers.eq(IFormConstants.AddPensionProviderSubmissionKey),
          Matchers.eq("TES1"), any())(any())).thenReturn(Future.successful("1"))

        val mockAuditable = mock[Auditor]
        doNothing().when(mockAuditable).sendDataEvent(any(), any(), any(), any())(any())

        val sut = createSut(mockIFormSubmissionService, mockAuditable)
        val result = Await.result(sut.addPensionProvider(nino, addPensionProvider), 5 seconds)

        result mustBe "1"
      }
    }

    "send pension provider journey audit event" in {
      val pensionProvider = AddPensionProvider("testName", new LocalDate(2017,6,9), "1234", "Yes", Some("123456789"))
      val map = Map(
        "nino" -> nino.nino,
        "envelope Id" -> "1",
        "start-date" -> pensionProvider.startDate.toString(),
        "pensionNumber" -> pensionProvider.pensionNumber,
        "pensionProviderName" -> pensionProvider.pensionProviderName)

      val mockIFormSubmissionService = mock[IFormSubmissionService]
      when(mockIFormSubmissionService.uploadIForm(Matchers.eq(nino), Matchers.eq(IFormConstants.AddPensionProviderSubmissionKey),
        Matchers.eq("TES1"), any())(any())).thenReturn(Future.successful("1"))

      val mockAuditable = mock[Auditor]
      doNothing().when(mockAuditable).sendDataEvent(any(), any(), any(), any())(any())

      val sut = createSut(mockIFormSubmissionService, mockAuditable)
      Await.result(sut.addPensionProvider(nino, pensionProvider), 5 seconds)

      verify(mockAuditable, times(1)).sendDataEvent(Matchers.eq(IFormConstants.AddPensionProviderAuditTxnName), any(), any(),
        Matchers.eq(map))(any())
    }
  }

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val nino = new Generator().nextNino

  private def createSut(iFormSubmissionService: IFormSubmissionService, auditable: Auditor) =
    new PensionProviderService(iFormSubmissionService, auditable)
}

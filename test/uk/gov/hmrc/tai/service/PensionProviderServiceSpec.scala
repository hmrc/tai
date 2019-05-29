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

package uk.gov.hmrc.tai.service

import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{doNothing, times, verify, when}
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.templates.EmploymentPensionViewModel
import uk.gov.hmrc.tai.repositories.EmploymentRepository
import uk.gov.hmrc.tai.templates.html.{EmploymentIForm, PensionProviderIForm}
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
        doNothing().when(mockAuditable).sendDataEvent(any(), any())(any())

        val sut = createSut(mockIFormSubmissionService, mockAuditable, mock[EmploymentRepository])
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
      doNothing().when(mockAuditable).sendDataEvent(any(), any())(any())

      val sut = createSut(mockIFormSubmissionService, mockAuditable, mock[EmploymentRepository])
      Await.result(sut.addPensionProvider(nino, pensionProvider), 5 seconds)

      verify(mockAuditable, times(1)).sendDataEvent(Matchers.eq(IFormConstants.AddPensionProviderAuditTxnName),
        Matchers.eq(map))(any())
    }
  }

  "addPensionProviderForm" must{
    "return the correct employment IForm" when{
      "applied with Person" in {
        val pensionProvider = AddPensionProvider("testName", new LocalDate(2017,6,9), "1234", "Yes", Some("123456789"))
        val person = Person(nino, "firstname", "lastname", Some(new LocalDate("1982-04-03")),
          Address("address line 1", "address line 2", "address line 3", "postcode", "UK"))

        val sut = createSut(mock[IFormSubmissionService], mock[Auditor], mock[EmploymentRepository])

        val result = Await.result(sut.addPensionProviderForm(pensionProvider)(hc)(person), 5 seconds)
        result mustBe PensionProviderIForm(EmploymentPensionViewModel(TaxYear(), person, pensionProvider)).toString

      }
    }
  }

  "IncorrectPensionProvider" must {
    "return an envelopeId" when {
      "given valid inputs" in {
        val incorrectPensionProvider = IncorrectPensionProvider("whatYouToldUs", "No", None)

        val mockIFormSubmissionService = mock[IFormSubmissionService]
        when(mockIFormSubmissionService.uploadIForm(Matchers.eq(nino), Matchers.eq(IFormConstants.IncorrectPensionProviderSubmissionKey),
          Matchers.eq("TES1"), any())(any())).thenReturn(Future.successful("1"))

        val mockAuditable = mock[Auditor]
        doNothing().when(mockAuditable).sendDataEvent(any(), any())(any())

        val sut = createSut(mockIFormSubmissionService, mockAuditable, mock[EmploymentRepository])
        val result = Await.result(sut.incorrectPensionProvider(nino, 1, incorrectPensionProvider), 5 seconds)

        result mustBe "1"
      }
    }
    "send pension provider journey audit event" in {
      val pensionProvider = IncorrectPensionProvider("whatYouToldUs", "No", None)
      val map = Map(
        "nino" -> nino.nino,
        "envelope Id" -> "1",
        "what-you-told-us" -> pensionProvider.whatYouToldUs.length.toString,
        "telephoneContactAllowed" -> pensionProvider.telephoneContactAllowed,
        "telephoneNumber" -> pensionProvider.telephoneNumber.getOrElse(""))

      val mockIFormSubmissionService = mock[IFormSubmissionService]
      when(mockIFormSubmissionService.uploadIForm(Matchers.eq(nino), Matchers.eq(IFormConstants.IncorrectPensionProviderSubmissionKey),
        Matchers.eq("TES1"), any())(any())).thenReturn(Future.successful("1"))

      val mockAuditable = mock[Auditor]
      doNothing().when(mockAuditable).sendDataEvent(any(), any())(any())

      val sut = createSut(mockIFormSubmissionService, mockAuditable, mock[EmploymentRepository])
      Await.result(sut.incorrectPensionProvider(nino, 1, pensionProvider), 5 seconds)

      verify(mockAuditable, times(1)).sendDataEvent(Matchers.eq(IFormConstants.IncorrectPensionProviderSubmissionKey),
        Matchers.eq(map))(any())
    }
  }

  "incorrectPensionProviderForm" must{
    "return the correct employment IForm" when{
      "applied with Person" in {
        val pensionProvider = IncorrectPensionProvider("whatYouToldUs", "No", None)
        val person = Person(nino, "firstname", "lastname", Some(new LocalDate("1982-04-03")),
          Address("address line 1", "address line 2", "address line 3", "postcode", "UK"))
        val currentTaxYear = TaxYear()
        val employment = Employment("TEST", Some("12345"), LocalDate.now(), None,
          List(AnnualAccount("", currentTaxYear, Available, Nil, Nil)), "", "", 2, Some(100), false, false)

        val mockEmploymentRepository = mock[EmploymentRepository]
        when(mockEmploymentRepository.employment(any(), any())(any()))
          .thenReturn(Future.successful(Some(employment)))

        val sut = createSut(mock[IFormSubmissionService], mock[Auditor], mockEmploymentRepository)

        val result = Await.result(sut.incorrectPensionProviderForm(nino,1,pensionProvider)(hc) (person), 5 seconds)
        result mustBe EmploymentIForm(EmploymentPensionViewModel(TaxYear(), person, pensionProvider, employment)).toString

      }
    }
  }

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val nino = new Generator().nextNino

  private def createSut(iFormSubmissionService: IFormSubmissionService, auditable: Auditor, employmentRepository: EmploymentRepository) =
    new PensionProviderService(iFormSubmissionService, employmentRepository, auditable)
}

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

package uk.gov.hmrc.tai.service

import java.time.LocalDate
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.{doNothing, times, verify, when}
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.income.Live
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.templates.EmploymentPensionViewModel
import uk.gov.hmrc.tai.repositories.EmploymentRepository
import uk.gov.hmrc.tai.templates.html.{EmploymentIForm, PensionProviderIForm}
import uk.gov.hmrc.tai.util.{BaseSpec, IFormConstants}

import scala.concurrent.Future

class PensionProviderServiceSpec extends BaseSpec {

  "AddPensionProvider" must {
    "return an envelopeId" when {
      "given valid inputs" in {
        val addPensionProvider =
          AddPensionProvider("testName", LocalDate.of(2017, 6, 9), "1234", "Yes", Some("123456789"))

        val mockIFormSubmissionService = mock[IFormSubmissionService]
        when(
          mockIFormSubmissionService
            .uploadIForm(meq(nino), meq(IFormConstants.AddPensionProviderSubmissionKey), meq("TES1"), any())(any()))
          .thenReturn(Future.successful("1"))

        val mockAuditable = mock[Auditor]
        doNothing().when(mockAuditable).sendDataEvent(any(), any())(any())

        val sut = createSut(mockIFormSubmissionService, mockAuditable, mock[EmploymentRepository])
        val result = sut.addPensionProvider(nino, addPensionProvider).futureValue

        result mustBe "1"
      }
    }

    "send pension provider journey audit event" in {
      val pensionProvider = AddPensionProvider("testName", LocalDate.of(2017, 6, 9), "1234", "Yes", Some("123456789"))
      val map = Map(
        "nino"                -> nino.nino,
        "envelope Id"         -> "1",
        "start-date"          -> pensionProvider.startDate.toString(),
        "pensionNumber"       -> pensionProvider.pensionNumber,
        "pensionProviderName" -> pensionProvider.pensionProviderName
      )

      val mockIFormSubmissionService = mock[IFormSubmissionService]
      when(
        mockIFormSubmissionService
          .uploadIForm(meq(nino), meq(IFormConstants.AddPensionProviderSubmissionKey), meq("TES1"), any())(any()))
        .thenReturn(Future.successful("1"))

      val mockAuditable = mock[Auditor]
      doNothing().when(mockAuditable).sendDataEvent(any(), any())(any())

      val sut = createSut(mockIFormSubmissionService, mockAuditable, mock[EmploymentRepository])
      sut.addPensionProvider(nino, pensionProvider).futureValue

      verify(mockAuditable, times(1))
        .sendDataEvent(meq(IFormConstants.AddPensionProviderAuditTxnName), meq(map))(any())
    }
  }

  "addPensionProviderForm" must {
    "return the correct employment IForm" when {
      "applied with Person" in {
        val pensionProvider =
          AddPensionProvider("testName", LocalDate.of(2017, 6, 9), "1234", "Yes", Some("123456789"))
        val person = Person(
          nino,
          "firstname",
          "lastname",
          Some(LocalDate.parse("1982-04-03")),
          Address("address line 1", "address line 2", "address line 3", "postcode", "UK"))

        val sut = createSut(mock[IFormSubmissionService], mock[Auditor], mock[EmploymentRepository])

        val result = sut.addPensionProviderForm(pensionProvider)(hc)(person).futureValue
        result mustBe PensionProviderIForm(EmploymentPensionViewModel(TaxYear(), person, pensionProvider)).toString
      }
    }
  }

  "IncorrectPensionProvider" must {
    "return an envelopeId" when {
      "given valid inputs" in {
        val incorrectPensionProvider = IncorrectPensionProvider("whatYouToldUs", "No", None)

        val mockIFormSubmissionService = mock[IFormSubmissionService]
        when(mockIFormSubmissionService
          .uploadIForm(meq(nino), meq(IFormConstants.IncorrectPensionProviderSubmissionKey), meq("TES1"), any())(any()))
          .thenReturn(Future.successful("1"))

        val mockAuditable = mock[Auditor]
        doNothing().when(mockAuditable).sendDataEvent(any(), any())(any())

        val sut = createSut(mockIFormSubmissionService, mockAuditable, mock[EmploymentRepository])
        val result = sut.incorrectPensionProvider(nino, 1, incorrectPensionProvider).futureValue

        result mustBe "1"
      }
    }
    "send pension provider journey audit event" in {
      val pensionProvider = IncorrectPensionProvider("whatYouToldUs", "No", None)
      val map = Map(
        "nino"                    -> nino.nino,
        "envelope Id"             -> "1",
        "what-you-told-us"        -> pensionProvider.whatYouToldUs.length.toString,
        "telephoneContactAllowed" -> pensionProvider.telephoneContactAllowed,
        "telephoneNumber"         -> pensionProvider.telephoneNumber.getOrElse("")
      )

      val mockIFormSubmissionService = mock[IFormSubmissionService]
      when(
        mockIFormSubmissionService
          .uploadIForm(meq(nino), meq(IFormConstants.IncorrectPensionProviderSubmissionKey), meq("TES1"), any())(any()))
        .thenReturn(Future.successful("1"))

      val mockAuditable = mock[Auditor]
      doNothing().when(mockAuditable).sendDataEvent(any(), any())(any())

      val sut = createSut(mockIFormSubmissionService, mockAuditable, mock[EmploymentRepository])
      sut.incorrectPensionProvider(nino, 1, pensionProvider).futureValue

      verify(mockAuditable, times(1))
        .sendDataEvent(meq(IFormConstants.IncorrectPensionProviderSubmissionKey), meq(map))(any())
    }
  }

  "incorrectPensionProviderForm" must {
    "return the correct employment IForm" when {
      "applied with Person" in {
        val pensionProvider = IncorrectPensionProvider("whatYouToldUs", "No", None)
        val person = Person(
          nino,
          "firstname",
          "lastname",
          Some(LocalDate.parse("1982-04-03")),
          Address("address line 1", "address line 2", "address line 3", "postcode", "UK"))
        val currentTaxYear = TaxYear()
        val employment = Employment(
          "TEST",
          Live,
          Some("12345"),
          LocalDate.now(),
          None,
          List(AnnualAccount(0, currentTaxYear, Available, Nil, Nil)),
          "",
          "",
          2,
          Some(100),
          hasPayrolledBenefit = false,
          receivingOccupationalPension = false
        )

        val mockEmploymentRepository = mock[EmploymentRepository]
        when(mockEmploymentRepository.employment(any(), any())(any()))
          .thenReturn(Future.successful(Right(employment)))

        val sut = createSut(mock[IFormSubmissionService], mock[Auditor], mockEmploymentRepository)

        val result = sut.incorrectPensionProviderForm(nino, 1, pensionProvider)(hc)(person).futureValue
        result mustBe EmploymentIForm(EmploymentPensionViewModel(TaxYear(), person, pensionProvider, employment)).toString

      }
    }
  }

  private def createSut(
    iFormSubmissionService: IFormSubmissionService,
    auditable: Auditor,
    employmentRepository: EmploymentRepository) =
    new PensionProviderService(iFormSubmissionService, employmentRepository, auditable)
}

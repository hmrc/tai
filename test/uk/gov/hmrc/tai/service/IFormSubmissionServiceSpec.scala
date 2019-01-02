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

import java.nio.file.{Files, Paths}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.tai.model.domain.{Address, Person}
import uk.gov.hmrc.tai.repositories.PersonRepository

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

class IFormSubmissionServiceSpec extends PlaySpec with MockitoSugar {

  "IFormSubmissionService" should {
    "create and submit an iform and return an envelope id after submission" in {
      val mockPersonRepository = mock[PersonRepository]
      when(mockPersonRepository.getPerson(any())(any()))
        .thenReturn(Future.successful(person))

      val mockPdfService = mock[PdfService]
      when(mockPdfService.generatePdf(any()))
        .thenReturn(Future.successful(pdfBytes))

      val mockFileUploadService = mock[FileUploadService]
      when(mockFileUploadService.createEnvelope())
        .thenReturn(Future.successful("1"))
      when(mockFileUploadService.uploadFile(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      val sut = createSUT(mockPersonRepository, mockPdfService, mockFileUploadService)
      val messageId = Await.result(sut.uploadIForm(nextNino, iformSubmissionKey, iformId, (person: Person) => {
        Future("")}), 5.seconds)

      messageId mustBe "1"

      verify(mockFileUploadService, times(1)).uploadFile(any(), any(),
        Matchers.contains(s"1-$iformSubmissionKey-${LocalDate.now().toString("YYYYMMdd")}-iform.pdf"), any())(any())
      verify(mockFileUploadService, times(1)).uploadFile(any(), any(),
        Matchers.contains(s"1-$iformSubmissionKey-${LocalDate.now().toString("YYYYMMdd")}-metadata.xml"), any())(any())
    }

    "abort the iform submission when the iform creation fails" in {
      val mockFileUploadService = mock[FileUploadService]

      val mockPersonRepository = mock[PersonRepository]
      when(mockPersonRepository.getPerson(any())(any()))
        .thenReturn(Future.successful(person))

      val mockPdfService = mock[PdfService]
      when(mockPdfService.generatePdf(any()))
        .thenReturn(Future.failed(new RuntimeException("Error")))

      val sut = createSUT(mockPersonRepository, mockPdfService, mockFileUploadService)
      the[RuntimeException] thrownBy Await.result(sut.uploadIForm(nextNino, iformSubmissionKey, iformId, (person: Person) => {
        Future("")}), 5.seconds)

      verify(mockFileUploadService, never()).uploadFile(any(), any(),
        Matchers.contains(s"1-$iformSubmissionKey-${LocalDate.now().toString("YYYYMMdd")}-iform.pdf"), any())(any())
      verify(mockFileUploadService, never()).uploadFile(any(), any(),
        Matchers.contains(s"1-$iformSubmissionKey-${LocalDate.now().toString("YYYYMMdd")}-metadata.xml"), any())(any())
    }
  }

  private implicit val hc = HeaderCarrier()

  private def nextNino = new Generator(new Random).nextNino

  private val iformSubmissionKey = "testSubmissionKey"
  private val iformId = "testIformId"

  private val person: Person = Person(nextNino, "", "", None, Address("", "", "", "", ""))

  private val pdfBytes = Files.readAllBytes(Paths.get("test/resources/sample.pdf"))

  private def createSUT(personRepository: PersonRepository,
                        pdfService: PdfService,
                        fileUploadService: FileUploadService) =
    new IFormSubmissionService(personRepository, pdfService, fileUploadService)
}

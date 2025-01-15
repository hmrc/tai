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

package uk.gov.hmrc.tai.service

import org.mockito.ArgumentMatchers.any
import uk.gov.hmrc.http.HttpException
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.connectors.PdfConnector
import uk.gov.hmrc.tai.model.admin.UseApacheFopLibrary
import uk.gov.hmrc.tai.model.templates.EmploymentPensionViewModel
import uk.gov.hmrc.tai.service.PdfService.EmploymentIFormReportRequest
import uk.gov.hmrc.tai.service.helper.XslFo2PdfBytesFunction
import uk.gov.hmrc.tai.util.BaseSpec

import java.nio.file.{Files, Paths}
import scala.concurrent.Future

class PdfServiceSpec extends BaseSpec {

  "PdfService generatePdfDocumentBytes using PDF generator" should {
    "return the pdf as bytes " when {
      "generatePdf is called successfully" in new Setup {

        val mockHtml2Pdf = mock[PdfConnector]
        val mockXslFo2PdfBytesFunction = inject[XslFo2PdfBytesFunction]
        val mockFeatureFlagService = mock[FeatureFlagService]
        val sut = createSut(mockHtml2Pdf, mockXslFo2PdfBytesFunction, mockFeatureFlagService)

        when(mockFeatureFlagService.get(any())).thenReturn(Future.successful(FeatureFlag(UseApacheFopLibrary, false)))

        val pdfBytes = Files.readAllBytes(Paths.get("test/resources/sample.pdf"))
        when(mockHtml2Pdf.generatePdf(any())).thenReturn(Future.successful(pdfBytes))

        val request = new EmploymentIFormReportRequest(emp_isEnd_NO_isAdd_NO)

        val result = sut.generatePdfDocumentBytes(request).futureValue
        result mustBe pdfBytes
      }
    }

    "propagate an HttpException" when {
      "generatePdf is called and the pdf connector generates a HttpException" in new Setup {
        val mockHtml2Pdf = mock[PdfConnector]
        val mockXslFo2PdfBytesFunction = inject[XslFo2PdfBytesFunction]
        val mockFeatureFlagService = mock[FeatureFlagService]
        val sut = createSut(mockHtml2Pdf, mockXslFo2PdfBytesFunction, mockFeatureFlagService)

        when(mockFeatureFlagService.get(any())).thenReturn(Future.successful(FeatureFlag(UseApacheFopLibrary, false)))

        when(mockHtml2Pdf.generatePdf(any())).thenReturn(Future.failed(new HttpException("", 1)))

        val request = new EmploymentIFormReportRequest(emp_isEnd_NO_isAdd_NO)

        val result = sut.generatePdfDocumentBytes(request).failed.futureValue
        result mustBe a[HttpException]
      }
    }
  }

  "PdfService generatePdfDocumentBytes using Apache FOP" should {
    "return the pdf as bytes " when {
      "generatePdf is called successfully" in new Setup {

        val mockHtml2Pdf = mock[PdfConnector]
        val mockXslFo2PdfBytesFunction = mock[XslFo2PdfBytesFunction]
        val mockFeatureFlagService = mock[FeatureFlagService]
        val sut = createSut(mockHtml2Pdf, mockXslFo2PdfBytesFunction, mockFeatureFlagService)

        when(mockFeatureFlagService.get(any())).thenReturn(Future.successful(FeatureFlag(UseApacheFopLibrary, true)))

        val pdfBytes = Files.readAllBytes(Paths.get("test/resources/sample.pdf"))
        when(mockXslFo2PdfBytesFunction(any())).thenReturn(pdfBytes)

        val request = new EmploymentIFormReportRequest(emp_isEnd_NO_isAdd_NO)

        val result = sut.generatePdfDocumentBytes(request).futureValue
        result mustBe pdfBytes
      }
    }
  }

  trait Setup {
    def createSut(
      html2Pdf: PdfConnector,
      xslFo2Pdf: XslFo2PdfBytesFunction,
      featureFlagService: FeatureFlagService
    ) = new PdfService(html2Pdf, xslFo2Pdf, featureFlagService)

    val emp_isEnd_NO_isAdd_NO = EmploymentPensionViewModel(
      "6 April 2017 to 5 April 2018",
      "AA000000A",
      "firstname",
      "lastname",
      "1 January 1900",
      "Yes",
      "0123456789",
      "address line 1",
      "address line 2",
      "address line 3",
      "postcode",
      "No",
      "No",
      "No",
      "pension name",
      "99999999999",
      "2 February 2000",
      "3 March 2100",
      "my story"
    )
  }

}

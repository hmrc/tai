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
import uk.gov.hmrc.tai.model.templates.EmploymentPensionViewModel
import uk.gov.hmrc.tai.service.PdfService.EmploymentIFormReportRequest
import uk.gov.hmrc.tai.service.helper.XslFo2PdfBytesFunction
import uk.gov.hmrc.tai.util.BaseSpec

import java.nio.file.{Files, Paths}

class PdfServiceSpec extends BaseSpec {

  "PdfService generatePdfDocumentBytes using Apache FOP" should {
    "return the pdf as bytes " when {
      "generatePdf is called successfully" in new Setup {

        val mockXslFo2PdfBytesFunction = mock[XslFo2PdfBytesFunction]
        val sut = createSut(mockXslFo2PdfBytesFunction)

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
      xslFo2Pdf: XslFo2PdfBytesFunction
    ) = new PdfService(xslFo2Pdf)

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

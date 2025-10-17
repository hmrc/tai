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

package uk.gov.hmrc.tai.integration
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pekko.util.Timeout
import org.mockito.ArgumentMatchers.any
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.TableFor3
import org.scalatest.prop.Tables.Table
import play.api.test.Helpers.await
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.connectors.PdfConnector
import uk.gov.hmrc.tai.model.admin.UseApacheFopLibrary
import uk.gov.hmrc.tai.model.templates.{EmploymentPensionViewModel, RemoveCompanyBenefitViewModel}
import uk.gov.hmrc.tai.service.PdfService
import uk.gov.hmrc.tai.service.PdfService.{EmploymentIFormReportRequest, PdfGeneratorRequest, PensionProviderIFormRequest, RemoveCompanyBenefitIFormRequest}
import uk.gov.hmrc.tai.service.helper.XslFo2PdfBytesFunction
import uk.gov.hmrc.tai.util.BaseSpec
import org.mockito.Mockito.when

import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.TimeUnit
import scala.concurrent.Future

class PdfServiceContentSpec extends BaseSpec with PdfServiceContentSpecHelper {

  private val sut: PdfService = {
    val injectedXslFo2PdfBytesFunction = inject[XslFo2PdfBytesFunction]
    val mockHtml2Pdf = mock[PdfConnector]
    val mockFeatureFlagService = mock[FeatureFlagService]
    when(mockFeatureFlagService.get(any())).thenReturn(Future.successful(FeatureFlag(UseApacheFopLibrary, true)))
    new PdfService(mockHtml2Pdf, injectedXslFo2PdfBytesFunction, mockFeatureFlagService)
  }

  "PdfService generatePdfDocumentBytes, when using Apache FOP" should {
    forAll(table) { (scenario, request, path) =>
      s"generate correct report for scenario '$scenario'" in {
        val pdfBytes: Array[Byte] =
          await(sut.generatePdfUsingFop(request.xmlFoDocument()))(Timeout.apply(5, TimeUnit.SECONDS))
        val pdfText: String = extractPdfText(pdfBytes)
        val expectedText: String = Files.readString(path)
        pdfText mustBe expectedText
      }
    }
  }
}

trait PdfServiceContentSpecHelper {

  def extractPdfText(pdfBytes: Array[Byte]): String = {
    val document = Loader.loadPDF(pdfBytes)
    val pdfStripper = new PDFTextStripper()
    pdfStripper.getText(document)
  }

  val employmentPensionModel_isEnd_NO_isAdd_NO = EmploymentPensionViewModel(
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

  val employmentPensionModel_isEnd_YES_isAdd_NO =
    employmentPensionModel_isEnd_NO_isAdd_NO.copy(isEnd = "Yes")

  val employmentPensionModel_isEnd_NO_isAdd_YES =
    employmentPensionModel_isEnd_NO_isAdd_NO.copy(isAdd = "Yes")

  val removeBenefit_isEnd_NO = RemoveCompanyBenefitViewModel(
    "AA000000A",
    "firstname",
    "lastname",
    "1 January 1900",
    "Yes",
    "0123456789",
    "addressLine1",
    "addressLine2",
    "addressLine3",
    "postcode",
    "No",
    "Yes",
    "No",
    "Mileage",
    "1000000",
    "On or after 3 March 2100"
  )

  val removeBenefit_isEnd_YES =
    removeBenefit_isEnd_NO.copy(isEnd = "Yes")

  val table: TableFor3[String, PdfGeneratorRequest[_], Path] = Table(
    ("Scenario", "PDF Request", "Path"),
    (
      "EmploymentIFormReportRequest_isEnd_NO_isAdd_NO",
      new EmploymentIFormReportRequest(employmentPensionModel_isEnd_NO_isAdd_NO),
      Paths.get("it/test/resources/pdf_content/EmploymentIFormReportRequest_isEnd_NO_isAdd_NO_pdf.txt")
    ),
    (
      "EmploymentIFormReportRequest_isEnd_YES_isAdd_NO",
      new EmploymentIFormReportRequest(employmentPensionModel_isEnd_YES_isAdd_NO),
      Paths.get("it/test/resources/pdf_content/EmploymentIFormReportRequest_isEnd_YES_isAdd_NO_pdf.txt")
    ),
    (
      "EmploymentIFormReportRequest_isEnd_NO_isAdd_YES",
      new EmploymentIFormReportRequest(employmentPensionModel_isEnd_NO_isAdd_YES),
      Paths.get("it/test/resources/pdf_content/EmploymentIFormReportRequest_isEnd_NO_isAdd_YES_pdf.txt")
    ),
    (
      "PensionProviderIFormRequest_isEnd_NO_isAdd_YES",
      new PensionProviderIFormRequest(employmentPensionModel_isEnd_NO_isAdd_NO),
      Paths.get("it/test/resources/pdf_content/PensionProviderIFormRequest_isEnd_NO_isAdd_NO_pdf.txt")
    ),
    (
      "PensionProviderIFormRequest_isEnd_NO",
      new RemoveCompanyBenefitIFormRequest(removeBenefit_isEnd_NO),
      Paths.get("it/test/resources/pdf_content/PensionProviderIFormRequest_isEnd_NO_pdf.txt")
    ),
    (
      "PensionProviderIFormRequest_isEnd_YES",
      new RemoveCompanyBenefitIFormRequest(removeBenefit_isEnd_YES),
      Paths.get("it/test/resources/pdf_content/PensionProviderIFormRequest_isEnd_YES_pdf.txt")
    )
  )
}

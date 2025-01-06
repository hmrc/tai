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

import com.google.inject.{Inject, Singleton}
import play.twirl.api.Content
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.connectors.PdfConnector
import uk.gov.hmrc.tai.model.admin.UseApacheFopLibrary
import uk.gov.hmrc.tai.model.templates.{EmploymentPensionViewModel, RemoveCompanyBenefitViewModel}
import uk.gov.hmrc.tai.service.PdfService.PdfGeneratorRequest
import uk.gov.hmrc.tai.service.helper.XslFo2PdfBytesFunction
import uk.gov.hmrc.tai.templates._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PdfService @Inject() (
  html2Pdf: PdfConnector,
  xslFo2Pdf: XslFo2PdfBytesFunction,
  featureFlagService: FeatureFlagService
)(implicit
  ec: ExecutionContext
) {

  @Deprecated
  def generatePdf(html: String): Future[Array[Byte]] = html2Pdf.generatePdf(html)

  def generatePdfDocumentBytes(pdfReport: PdfGeneratorRequest[_]): Future[Array[Byte]] =
    featureFlagService.get(UseApacheFopLibrary).map(_.isEnabled).flatMap { enableApacheFop =>
      if (enableApacheFop)
        Future.successful(xslFo2Pdf(pdfReport.xmlFoDocument()))
      else
        generatePdf(pdfReport.htmlDocument())
    }
}

object PdfService {

  class EmploymentIFormReportRequest(model: EmploymentPensionViewModel) extends PdfGeneratorRequest(model) {
    override def htmlDocument(): String = html.EmploymentIForm(model).body
    override def xmlFoDocument(): Array[Byte] = xml.EmploymentIForm(model).body.getBytes
  }

  class PensionProviderIFormRequest(model: EmploymentPensionViewModel) extends PdfGeneratorRequest(model) {
    override def htmlDocument(): String = html.PensionProviderIForm(model).body
    override def xmlFoDocument(): Array[Byte] = xml.PensionProviderIForm(model).body.getBytes
  }

  class RemoveCompanyBenefitIFormRequest(model: RemoveCompanyBenefitViewModel) extends PdfGeneratorRequest(model) {
    override def htmlDocument(): String = html.RemoveCompanyBenefitIForm(model).body
    override def xmlFoDocument(): Array[Byte] = xml.RemoveCompanyBenefitIForm(model).body.getBytes
  }

  sealed abstract class PdfGeneratorRequest[T](model: T) {
    def htmlDocument(): String
    def xmlFoDocument(): Array[Byte]
  }
}

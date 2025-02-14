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
import play.api.Logging
import uk.gov.hmrc.tai.model.templates.{EmploymentPensionViewModel, RemoveCompanyBenefitViewModel}
import uk.gov.hmrc.tai.service.PdfService.PdfGeneratorRequest
import uk.gov.hmrc.tai.service.helper.XslFo2PdfBytesFunction
import uk.gov.hmrc.tai.templates._

import scala.annotation.nowarn
import scala.concurrent.Future

@Singleton
class PdfService @Inject() (
  xslFo2Pdf: XslFo2PdfBytesFunction
) extends Logging {

  def generatePdfDocumentBytes(pdfReport: PdfGeneratorRequest[_]): Future[Array[Byte]] =
    Future.successful(xslFo2Pdf(pdfReport.xmlFoDocument()))

}

object PdfService {

  class EmploymentIFormReportRequest(model: EmploymentPensionViewModel) extends PdfGeneratorRequest(model) {
    override def xmlFoDocument(): Array[Byte] = xml.EmploymentIForm(model).body.getBytes
    override def toString: String = xml.EmploymentIForm(model).body
  }

  class PensionProviderIFormRequest(model: EmploymentPensionViewModel) extends PdfGeneratorRequest(model) {
    override def xmlFoDocument(): Array[Byte] = xml.PensionProviderIForm(model).body.getBytes
    override def toString: String = xml.PensionProviderIForm(model).body
  }

  class RemoveCompanyBenefitIFormRequest(model: RemoveCompanyBenefitViewModel) extends PdfGeneratorRequest(model) {
    override def xmlFoDocument(): Array[Byte] = xml.RemoveCompanyBenefitIForm(model).body.getBytes
    override def toString: String = xml.RemoveCompanyBenefitIForm(model).body
  }

  @nowarn
  sealed abstract class PdfGeneratorRequest[T](model: T) {
    def xmlFoDocument(): Array[Byte]
    def toString: String
  }
}

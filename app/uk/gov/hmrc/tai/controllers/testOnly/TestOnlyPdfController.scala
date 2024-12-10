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

package uk.gov.hmrc.tai.controllers.testOnly

import com.google.inject.{Inject, Singleton}
import org.apache.pekko.util.ByteString
import play.api.http.HttpEntity.Strict
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.model.templates.{EmploymentPensionViewModel, RemoveCompanyBenefitViewModel}
import uk.gov.hmrc.tai.service.PdfService
import uk.gov.hmrc.tai.templates.html.{EmploymentIForm, PensionProviderIForm, RemoveCompanyBenefitIForm}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestOnlyPdfController @Inject() (
  ps: PdfService,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def pdf(isNewGenerator: Boolean = false, contentId: String = "default"): Action[AnyContent] = Action.async { _ =>
    println(s"--------------TestOnlyPdfController.pdf(isNewGenerator:$isNewGenerator,contentId:$contentId)")
    generatePdf(isNewGenerator, contentId).map(bytes =>
      Result(
        header = ResponseHeader(OK),
        body = Strict(ByteString.apply(bytes), contentType = Some("application/pdf"))
      )
    )
  }

  def generatePdf(isNewGenerator: Boolean, contentId: String): Future[Array[Byte]] = {
    val content = contentId match {
      case "EmploymentIForm"           => Future.successful(exampleContent_EmploymentIForm)
      case "PensionProviderIForm"      => Future.successful(exampleContent_PensionProviderIForm)
      case "RemoveCompanyBenefitIForm" => Future.successful(exampleContent_PensionProviderIForm)
      case _                           => Future.successful("<html><body>test</body></html>")
    }
    content.flatMap(ps.generatePdf)
  }

  // http://localhost:9331/tai/test-only/pdf/false/EmploymentIForm
  // http://localhost:9331/tai/test-only/pdf/true/EmploymentIForm
  def exampleContent_EmploymentIForm = EmploymentIForm(exampleData_EmploymentPensionViewModel).toString()

  // http://localhost:9331/tai/test-only/pdf/false/PensionProviderIForm
  // http://localhost:9331/tai/test-only/pdf/true/PensionProviderIForm
  def exampleContent_PensionProviderIForm = PensionProviderIForm(exampleData_EmploymentPensionViewModel).toString()

  // http://localhost:9331/tai/test-only/pdf/false/RemoveCompanyBenefitIForm
  // http://localhost:9331/tai/test-only/pdf/true/RemoveCompanyBenefitIForm
  def exampleContent_RemoveCompanyBenefitIForm =
    RemoveCompanyBenefitIForm(exampleData_RemoveCompanyBenefitViewModel).toString()

  def exampleData_EmploymentPensionViewModel = EmploymentPensionViewModel(
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
    "Yes",
    "No",
    "pension name",
    "99999999999",
    "2 February 2000",
    "3 March 2100",
    "my story"
  )

  def exampleData_RemoveCompanyBenefitViewModel = RemoveCompanyBenefitViewModel(
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

}

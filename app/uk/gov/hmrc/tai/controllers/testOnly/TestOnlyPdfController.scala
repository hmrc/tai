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
import uk.gov.hmrc.tai.templates.xml.{EmploymentXmlFoForm, HelloForm, PensionProviderXmlFoForm, RemoveCompanyBenefitXmlFoForm}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestOnlyPdfController @Inject() (
  legacyPdfService: PdfService,
  contemporaryPdfService: XslFoTransformationService,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def pdf( contentId: String = "new:hello:default"): Action[AnyContent] = Action.async { _ =>
    println(s"--------------TestOnlyPdfController.pdf($contentId)")




  }

  def generatePdf(isNewGenerator: Boolean, contentId: String): Future[Array[Byte]] = {
    val content = contentId match {
      // new
      case "hello"                         => Future.successful(example_hello)
      case "EmploymentXmlFoForm"           => Future.successful(exampleContent_EmploymentXmlFoForm)
      case "PensionProviderXmlFoForm"      => Future.successful(exampleContent_PensionProviderXmlFoForm)
      case "RemoveCompanyBenefitXmlFoForm" => Future.successful(exampleContent_RemoveCompanyBenefitXmlFoForm)
      case "RemoveCompanyBenefitXmlFoFormWithDetails" =>
        Future.successful(exampleContent_RemoveCompanyBenefitXmlFoFormWithDetails)
      // old
      case "EmploymentIForm"           => Future.successful(exampleContent_EmploymentIForm)
      case "PensionProviderIForm"      => Future.successful(exampleContent_PensionProviderIForm)
      case "RemoveCompanyBenefitIForm" => Future.successful(exampleContent_RemoveCompanyBenefitIForm)
      case "RemoveCompanyBenefitIFormWithDetails" =>
        Future.successful(exampleContent_RemoveCompanyBenefitIFormWithDetails)
      case "hello" => Future.successful("<html><body>test</body></html>")
    }
    content.flatMap(html =>
      if (isNewGenerator)
        Future.successful(contemporaryPdfService.toPdfBytes(html))
      else
        legacyPdfService.generatePdf(html)
    )
  }

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

  def exampleData_RemoveCompanyBenefitViewModel_details = RemoveCompanyBenefitViewModel(
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
    "Yes",
    "Mileage",
    "1000000",
    "On or after 3 March 2100"
  )

}

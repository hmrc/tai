/*
 * Copyright 2024 HM Revenue & Customs
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
import play.api.mvc.{Action, _}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.model.templates.{EmploymentPensionViewModel, RemoveCompanyBenefitViewModel}
import uk.gov.hmrc.tai.service.PdfService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestOnlyPdfController @Inject() (
  legacyPdfService: PdfService,
  contemporaryPdfService: XslFoTransformationService,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def index(): Action[AnyContent] =
    Action { _ =>
      Result(
        header = ResponseHeader(OK),
        body = Strict(
          ByteString.apply(uk.gov.hmrc.tai.templates.html.Index().body.getBytes()),
          contentType = Some("text/html")
        )
      )
    }

  def pdf(isNewGenerator: Boolean = false, formId: String = "default"): Action[AnyContent] =
    Action.async { _ =>
      println(s"--------------TestOnlyPdfController.pdf(isNewGenerator:$isNewGenerator,formId:$formId)")
      generatePdf(isNewGenerator, formId).map(bytes =>
        Result(
          header = ResponseHeader(OK),
          body = Strict(ByteString.apply(bytes), contentType = Some("application/pdf"))
        )
      )
    }

  def generatePdf(isNewGenerator: Boolean, formId: String): Future[Array[Byte]] = {

    val generator: String => Future[Array[Byte]] = (content: String) =>
      if (isNewGenerator)
        Future.successful(contemporaryPdfService.toPdfBytes(content))
      else
        legacyPdfService.generatePdf(content)

    def fillDefault(): String =
      if (isNewGenerator)
        uk.gov.hmrc.tai.templates.xml.HelloForm().body
      else
        uk.gov.hmrc.tai.templates.html.HelloForm().body

    def fillEmploymentPensionTemplate(model: EmploymentPensionViewModel): String =
      if (isNewGenerator)
        uk.gov.hmrc.tai.templates.xml.EmploymentIForm(model).body
      else
        uk.gov.hmrc.tai.templates.html.EmploymentIForm(model).body

    def fillRemoveCompanyBenefitTemplate(model: RemoveCompanyBenefitViewModel): String =
      if (isNewGenerator)
        uk.gov.hmrc.tai.templates.xml.RemoveCompanyBenefitIForm(model).body
      else
        uk.gov.hmrc.tai.templates.html.RemoveCompanyBenefitIForm(model).body

    def fillPensionProviderTemplate(model: EmploymentPensionViewModel): String =
      if (isNewGenerator)
        uk.gov.hmrc.tai.templates.xml.PensionProviderIForm(model).body
      else
        uk.gov.hmrc.tai.templates.html.PensionProviderIForm(model).body

    var formSource: String =
      formId match {
        case "Employment_emp_isEnd_NO_isAdd_NO" =>
          fillEmploymentPensionTemplate(emp_isEnd_NO_isAdd_NO)
        case "Employment_emp_isEnd_YES_isAdd_NO" =>
          fillEmploymentPensionTemplate(emp_isEnd_YES_isAdd_NO)
        case "Employment_emp_isEnd_NO_isAdd_YES" =>
          fillEmploymentPensionTemplate(emp_isEnd_NO_isAdd_YES)
        case "PensionProvider_emp_isEnd_NO_isAdd_NO" =>
          fillPensionProviderTemplate(emp_isEnd_NO_isAdd_NO)
        case "RemoveCompanyBenefit_rcb_isEnd_NO" =>
          fillRemoveCompanyBenefitTemplate(rcb_isEnd_NO)
        case "RemoveCompanyBenefit_rcb_isEnd_YES" =>
          fillRemoveCompanyBenefitTemplate(rcb_isEnd_YES)
        case _ =>
          fillDefault()
      }

    generator.apply(formSource)

  }

  var emp_isEnd_NO_isAdd_NO = EmploymentPensionViewModel(
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

  var emp_isEnd_YES_isAdd_NO =
    emp_isEnd_NO_isAdd_NO.copy(isEnd = "Yes")

  var emp_isEnd_NO_isAdd_YES =
    emp_isEnd_NO_isAdd_NO.copy(isAdd = "Yes")

  val rcb_isEnd_NO = RemoveCompanyBenefitViewModel(
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

  val rcb_isEnd_YES = rcb_isEnd_NO.copy(isEnd = "Yes")

}

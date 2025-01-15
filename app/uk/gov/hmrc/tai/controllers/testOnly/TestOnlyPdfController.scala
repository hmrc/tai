/*
 * Copyright 2025 HM Revenue & Customs
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

import com.google.inject.Inject
import org.apache.pekko.util.ByteString
import play.api.http.HttpEntity.Strict
import play.api.mvc.{Action, AnyContent, ControllerComponents, ResponseHeader, Result}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.model.admin.UseApacheFopLibrary
import uk.gov.hmrc.tai.model.templates.{EmploymentPensionViewModel, RemoveCompanyBenefitViewModel}
import uk.gov.hmrc.tai.service.PdfService
import uk.gov.hmrc.tai.service.PdfService.{EmploymentIFormReportRequest, PdfGeneratorRequest, PensionProviderIFormRequest, RemoveCompanyBenefitIFormRequest}

import scala.concurrent.{ExecutionContext, Future}

class TestOnlyPdfController @Inject() (
  pdfService: PdfService,
  cc: ControllerComponents,
  featureFlagService: FeatureFlagService
)(implicit ec: ExecutionContext)
    extends BackendController(cc) with TestData {

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

  def pdf(useNewGenerator: Boolean = false, formId: String, dataId: String): Action[AnyContent] =
    Action.async { _ =>
      val dataEmp = Map(
        "emp_isEnd_NO_isAdd_NO"  -> emp_isEnd_NO_isAdd_NO,
        "emp_isEnd_YES_isAdd_NO" -> emp_isEnd_YES_isAdd_NO,
        "emp_isEnd_NO_isAdd_YES" -> emp_isEnd_NO_isAdd_YES
      )

      val dataComp = Map(
        "rcb_isEnd_NO"  -> rcb_isEnd_NO,
        "rcb_isEnd_YES" -> rcb_isEnd_YES
      )

      val request: PdfGeneratorRequest[_] = if (formId == "EmploymentIFormReportRequest") {
        new EmploymentIFormReportRequest(dataEmp.get(dataId).get)
      } else if (formId == "PensionProviderIFormRequest") {
        new PensionProviderIFormRequest(dataEmp(dataId))
      } else if (formId == "RemoveCompanyBenefitIFormRequest") {
        new RemoveCompanyBenefitIFormRequest(dataComp.get(dataId).get)
      } else {
        throw new Exception("unknown form")
      }

      featureFlagService
        .get(UseApacheFopLibrary)
        .map(_.isEnabled)
        .flatMap { isEnabled =>
          if (isEnabled != useNewGenerator) {
            featureFlagService.set(UseApacheFopLibrary, useNewGenerator)
          } else {
            Future.successful((): Unit)
          }
        }
        .flatMap(_ =>
          pdfService
            .generatePdfDocumentBytes(request)
        )
        .map(bytes =>
          Result(
            header = ResponseHeader(OK),
            body = Strict(ByteString.apply(bytes), contentType = Some("application/pdf"))
          )
        )
    }
}

trait TestData {
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

  val emp_isEnd_YES_isAdd_NO =
    emp_isEnd_NO_isAdd_NO.copy(isEnd = "Yes")

  val emp_isEnd_NO_isAdd_YES =
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

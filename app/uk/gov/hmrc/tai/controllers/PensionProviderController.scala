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

package uk.gov.hmrc.tai.controllers

import com.google.inject.Inject
import javax.inject.Singleton
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.model.api.{ApiFormats, ApiResponse}
import uk.gov.hmrc.tai.model.domain.{AddPensionProvider, IncorrectPensionProvider}
import uk.gov.hmrc.tai.service.PensionProviderService

@Singleton
class PensionProviderController @Inject()(pensionProviderService: PensionProviderService,
                                          authentication: AuthenticationPredicate,
                                          cc: ControllerComponents)
  extends BackendController(cc)
  with ApiFormats {

  def addPensionProvider(nino: Nino): Action[JsValue] = authentication.async(parse.json) {
    implicit request =>
      withJsonBody[AddPensionProvider] {
        pensionProvider =>
          pensionProviderService.addPensionProvider(nino, pensionProvider) map (envelopeId => {
            Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
          })
      }
  }

  def incorrectPensionProvider(nino: Nino, id: Int): Action[JsValue] =  authentication.async(parse.json) {
    implicit request =>
      withJsonBody[IncorrectPensionProvider] {
        incorrectPension =>
          pensionProviderService.incorrectPensionProvider(nino, id, incorrectPension) map (envelopeId => {
            Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
          })
      }
  }

}

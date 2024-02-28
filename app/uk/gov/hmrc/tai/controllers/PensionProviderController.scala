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

package uk.gov.hmrc.tai.controllers

import javax.inject.Singleton
import com.google.inject.Inject
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.model.api.{ApiFormats, ApiResponse}
import uk.gov.hmrc.tai.model.domain.{AddPensionProvider, IncorrectPensionProvider}
import uk.gov.hmrc.tai.service.PensionProviderService
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate

import scala.concurrent.ExecutionContext

@Singleton
class PensionProviderController @Inject()(
  pensionProviderService: PensionProviderService,
  authentication: AuthenticationPredicate,
  cc: ControllerComponents)(
  implicit ec: ExecutionContext
) extends BackendController(cc) with ApiFormats {

  def addPensionProvider(nino: Nino): Action[JsValue] = authentication.async(parse.json) { implicit request =>
    withJsonBody[AddPensionProvider] { pensionProvider =>
      pensionProviderService.addPensionProvider(nino, pensionProvider) map (envelopeId => {
        Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
      })
    }
  }

  def incorrectPensionProvider(nino: Nino, id: Int): Action[JsValue] = authentication.async(parse.json) {
    implicit request =>
      withJsonBody[IncorrectPensionProvider] { incorrectPension =>
        println("PPPP " + incorrectPension)
        pensionProviderService.incorrectPensionProvider(nino, id, incorrectPension) map (envelopeId => {
          Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
        })
      }
  }

}

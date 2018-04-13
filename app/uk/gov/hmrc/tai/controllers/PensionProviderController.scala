/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.mvc.Action
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.model.api.{ApiFormats, ApiResponse}
import uk.gov.hmrc.tai.model.domain.AddPensionProvider
import uk.gov.hmrc.tai.service.PensionProviderService
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate

@Singleton
class PensionProviderController @Inject()(pensionProviderService: PensionProviderService,
                                          authentication: AuthenticationPredicate)
  extends BaseController
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

}

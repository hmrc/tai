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

package uk.gov.hmrc.tai.controllers.taxCodeChange

import com.google.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.config.FeatureTogglesConfig
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.service.TaxCodeChangeService

import scala.concurrent.Future

class TaxCodeChangeController @Inject()(authentication: AuthenticationPredicate,
                                        taxCodeChangeService: TaxCodeChangeService,
                                        featureToggle: FeatureTogglesConfig) extends BaseController  {

  def hasTaxCodeChanged(nino: Nino) = authentication.async {
    implicit request => {
      val result = if (featureToggle.taxCodeChangeEnabled) taxCodeChangeService.hasTaxCodeChanged(nino) else false

      Future.successful(Ok(Json.obj("hasTaxCodeChanged" -> result)))
    }
  }


  def lastTaxCodeChange(nino: Nino) = authentication.async {
    implicit request =>
      Future.successful(Ok(Json.obj("employmentId" -> 1234567890,
                                    "p2Issued" -> true,
                                    "p2Date" -> "2011-06-23")))
  }
}

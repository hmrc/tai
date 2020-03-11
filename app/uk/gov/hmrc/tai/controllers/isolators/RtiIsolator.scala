/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.tai.controllers.isolators

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.mvc.{ActionFilter, Result}
import uk.gov.hmrc.tai.config.FeatureTogglesConfig
import uk.gov.hmrc.tai.controllers.predicates.AuthenticatedRequest
import play.api.mvc.Results.ServiceUnavailable

import scala.concurrent.Future

@Singleton
class RtiIsolatorImpl @Inject()(featureTogglesConfig: FeatureTogglesConfig) extends RtiIsolator {

  override protected def filter[A](request: AuthenticatedRequest[A]): Future[Option[Result]] =
    if (featureTogglesConfig.useRti) {
      Future.successful(None)
    } else {
      Future.successful(Some(ServiceUnavailable("RTI isolation active")))
    }
}

@ImplementedBy(classOf[RtiIsolatorImpl])
trait RtiIsolator extends ActionFilter[AuthenticatedRequest]

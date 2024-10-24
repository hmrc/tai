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

package uk.gov.hmrc.tai.controllers.auth

import com.google.inject.{ImplementedBy, Inject}
import play.api.mvc.{ActionBuilder, AnyContent, DefaultActionBuilder}

@ImplementedBy(classOf[AuthJourneyImpl])
trait AuthJourney {
  val authWithUserDetails: ActionBuilder[AuthenticatedRequest, AnyContent]

  val authForEmployeeExpenses: ActionBuilder[AuthenticatedRequest, AnyContent]
}

class AuthJourneyImpl @Inject() (
  authAction: AuthRetrievals,
  pertaxAuthAction: PertaxAuthAction,
  pertaxAuthActionForEmployeeExpenses: PertaxAuthActionForEmployeeExpenses,
  defaultActionBuilder: DefaultActionBuilder
) extends AuthJourney {

  val authWithUserDetails: ActionBuilder[AuthenticatedRequest, AnyContent] =
    defaultActionBuilder andThen pertaxAuthAction andThen authAction

  val authForEmployeeExpenses: ActionBuilder[AuthenticatedRequest, AnyContent] =
    defaultActionBuilder andThen pertaxAuthActionForEmployeeExpenses andThen authAction

}

/*
 * Copyright 2026 HM Revenue & Customs
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
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.tai.model.AuthenticatedRequest

@ImplementedBy(classOf[AuthJourneyImpl])
trait AuthJourney {
  def authWithUserDetails: ActionBuilder[AuthenticatedRequest, AnyContent]

  def authWithUserDetails(nino: Nino): ActionBuilder[AuthenticatedRequest, AnyContent]

  def authForEmployeeExpenses: ActionBuilder[AuthenticatedRequest, AnyContent]

  def authForEmployeeExpenses(nino: Nino): ActionBuilder[AuthenticatedRequest, AnyContent]
}

class AuthJourneyImpl @Inject() (
  pertaxAuthAction: PertaxAuthAction,
  pertaxAuthActionForEmployeeExpenses: PertaxAuthActionForEmployeeExpenses,
  authAction: AuthAction,
  ninoValidationAction: NinoValidationAction,
  defaultActionBuilder: DefaultActionBuilder
) extends AuthJourney {

  override def authWithUserDetails: ActionBuilder[AuthenticatedRequest, AnyContent] =
    defaultActionBuilder andThen pertaxAuthAction andThen authAction

  override def authWithUserDetails(nino: Nino): ActionBuilder[AuthenticatedRequest, AnyContent] =
    defaultActionBuilder andThen pertaxAuthAction andThen authAction andThen ninoValidationAction.validateNino(nino)

  override def authForEmployeeExpenses: ActionBuilder[AuthenticatedRequest, AnyContent] =
    defaultActionBuilder andThen pertaxAuthActionForEmployeeExpenses andThen authAction

  override def authForEmployeeExpenses(nino: Nino): ActionBuilder[AuthenticatedRequest, AnyContent] =
    defaultActionBuilder andThen pertaxAuthActionForEmployeeExpenses andThen authAction andThen ninoValidationAction
      .validateNino(nino)

}

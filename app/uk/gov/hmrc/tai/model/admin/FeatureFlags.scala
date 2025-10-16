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

package uk.gov.hmrc.tai.model.admin

import uk.gov.hmrc.mongoFeatureToggles.model.Environment.{Environment, Local, Production, Qa, Staging}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlagName

case object RtiCallToggle extends FeatureFlagName {
  override val name: String = "rti-call-toggle"
  override val description: Option[String] = Some(
    "Enable/disable calls to the RTI API for individual payment information: '/rti/individual/payments/nino/:nino'"
  )
}

case object HipIabdsUpdateExpensesToggle extends FeatureFlagName {
  override val name: String = "hip-iabds-update-expenses-toggle"
  override val description: Option[String] = Some(
    "Enable/disable routing of PUT calls for IABDS employee expenses to HIP instead of DES."
  )
  override val lockedEnvironments: Seq[Environment] = Seq(Local, Staging, Qa, Production)
}

case object HipGetIabdsExpensesToggle extends FeatureFlagName {
  override val name: String = "hip-get-iabds-expenses-toggle"
  override val description: Option[String] = Some(
    "Enable/disable routing of GET calls for IABDS employee expenses to HIP instead of DES."
  )
  override val lockedEnvironments: Seq[Environment] = Seq(Local, Staging, Qa, Production)
}

case object HipTaxAccountHistoryToggle extends FeatureFlagName {
  override val name: String = "hip-tax-account-history-toggle"
  override val description: Option[String] = Some(
    "Enable/disable the GET call for tax account history via HIP instead of DES."
  )
  override val lockedEnvironments: Seq[Environment] = Seq(Local, Staging, Qa, Production)
}

case object UseApacheFopLibrary extends FeatureFlagName {
  override val name: String = "use-apache-fop-library"
  override val description: Option[String] = Some(
    "Use ApacheFopLibrary instead of PDF_GENERATOR_SERVICE"
  )
}

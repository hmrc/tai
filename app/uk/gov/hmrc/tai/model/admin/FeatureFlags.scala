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

import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlagName

case object RtiCallToggle extends FeatureFlagName {
  override val name: String                = "rti-call-toggle"
  override val description: Option[String] = Some(
    "Enable/disable toggle for RTI in the RtiConnector, controlling access to the API: '/rti/individual/payments/nino/:nino'"
  )
}

case object TaxCodeHistoryFromDESToggle extends FeatureFlagName {
  override val name: String = "tax-code-history-from-des-toggle"
  override val description: Option[String] = Some(
    "get the tax code history from DES and not IF"
  )
}
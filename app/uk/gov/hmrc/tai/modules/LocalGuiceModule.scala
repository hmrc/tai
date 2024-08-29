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

package uk.gov.hmrc.tai.modules

import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthorisedFunctions
import uk.gov.hmrc.tai.auth.MicroserviceAuthorisedFunctions
import uk.gov.hmrc.tai.config.ApplicationStartUp
import uk.gov.hmrc.tai.connectors._
import uk.gov.hmrc.tai.service._

class LocalGuiceModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    val hipEnabled: Boolean = configuration.getOptional[Boolean]("hip.enabled").getOrElse(false)
    val employmentBindings = if (hipEnabled) {
      Seq(
        bind[EmploymentDetailsConnector].qualifiedWith("default").to[DefaultEmploymentDetailsConnector],
        bind[EmploymentService].to[EmploymentServiceImpl]
      )
    } else {
      Seq(
        bind[EmploymentDetailsConnector].qualifiedWith("default").to[DefaultEmploymentDetailsConnectorNps],
        bind[EmploymentService].to[EmploymentServiceNpsImpl]
      )
    }

    Seq(
      bind[ApplicationStartUp].toSelf.eagerly(),
      bind[AuthorisedFunctions].to[MicroserviceAuthorisedFunctions].eagerly(),
      bind[LockService].to[LockServiceImpl],
      bind[RtiConnector].to[CachingRtiConnector],
      bind[RtiConnector].qualifiedWith("default").to[DefaultRtiConnector],
      bind[IabdConnector].to[CachingIabdConnector],
      bind[IabdConnector].qualifiedWith("default").to[DefaultIabdConnector],
      bind[TaxCodeHistoryConnector].to[CachingTaxCodeHistoryConnector],
      bind[TaxCodeHistoryConnector].qualifiedWith("default").to[DefaultTaxCodeHistoryConnector],
      bind[EmploymentDetailsConnector].to[CachingEmploymentDetailsConnector],
      bind[TaxAccountConnector].to[CachingTaxAccountConnector],
      bind[TaxAccountConnector].qualifiedWith("default").to[DefaultTaxAccountConnector]
    ) ++ employmentBindings
  }
}

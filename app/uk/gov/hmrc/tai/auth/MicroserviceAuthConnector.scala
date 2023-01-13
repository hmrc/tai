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

package uk.gov.hmrc.tai.auth

import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import javax.inject.{Inject, Singleton}

// $COVERAGE-OFF$ No proper implementation to test
@Singleton
class MicroserviceAuthConnector @Inject()(
  val environment: Environment,
  val conf: Configuration,
  val WSHttp: HttpClient,
  servicesConfig: ServicesConfig)
    extends PlayAuthConnector {
  lazy val serviceUrl: String = servicesConfig.baseUrl("auth")
  lazy val http: HttpClient = WSHttp
}
// $COVERAGE-ON$

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

package uk.gov.hmrc.tai.connectors
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.Injecting
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.util.WireMockHelper

import scala.concurrent.ExecutionContext
import scala.util.Random

trait ConnectorBaseSpec extends PlaySpec with MockitoSugar with WireMockHelper with Injecting {

  val nino: Nino = new Generator(new Random).nextNino
  implicit val hc: HeaderCarrier = HeaderCarrier()

  lazy val auditor: Auditor = inject[Auditor]
  lazy val metrics: Metrics = inject[Metrics]
  lazy val httpClient: HttpClient = inject[HttpClient]
  implicit lazy val ec: ExecutionContext = inject[ExecutionContext]
}

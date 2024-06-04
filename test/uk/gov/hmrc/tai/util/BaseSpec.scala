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

package uk.gov.hmrc.tai.util

import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.Application
import play.api.cache.AsyncCacheApi
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Injecting
import play.api.inject.bind
import play.api.mvc.ControllerComponents
import play.api.test.Helpers.stubControllerComponents
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.tai.connectors.cache.CacheId
import uk.gov.hmrc.tai.controllers.FakeTaiPlayApplication
import uk.gov.hmrc.tai.controllers.auth.AuthJourney

import scala.concurrent.ExecutionContext
import scala.util.Random

trait BaseSpec
    extends PlaySpec with MockitoSugar with FakeTaiPlayApplication with ScalaFutures with Injecting
    with BeforeAndAfterEach {

  implicit lazy val ec: ExecutionContext = inject[ExecutionContext]
  val responseBody: String = ""

  lazy val fakeAsyncCacheApi = new FakeAsyncCacheApi()

  lazy val loggedInAuthenticationAuthJourney: AuthJourney = FakeAuthJourney
  lazy val cc: ControllerComponents = stubControllerComponents()
  val sessionIdValue: String = "some session id"
  implicit lazy val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(sessionIdValue)))
  val nino: Nino = new Generator(Random).nextNino
  val cacheId: CacheId = CacheId(nino)
  val cacheIdNoSession: CacheId = CacheId.noSession(nino)

  override implicit lazy val app: Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[AsyncCacheApi].toInstance(fakeAsyncCacheApi)
      )
      .build()

}

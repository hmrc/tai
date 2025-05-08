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

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Assertion, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Application
import play.api.cache.AsyncCacheApi
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.Helpers.stubControllerComponents
import play.api.test.{FakeRequest, Injecting}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.{BadGatewayException, BadRequestException, HeaderCarrier, InternalServerException, NotFoundException, SessionId}
import uk.gov.hmrc.tai.config.CustomErrorHandler
import uk.gov.hmrc.tai.connectors.cache.CacheId
import uk.gov.hmrc.tai.controllers.FakeTaiPlayApplication
import uk.gov.hmrc.tai.controllers.auth.AuthJourney

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Random, Try}

trait BaseSpec
    extends PlaySpec with MockitoSugar with FakeTaiPlayApplication with ScalaFutures with Injecting
    with BeforeAndAfterEach {

  implicit lazy val ec: ExecutionContext = inject[ExecutionContext]
  val responseBody: String = ""

  lazy val fakeAsyncCacheApi: AsyncCacheApi = new FakeAsyncCacheApi()
  lazy val loggedInAuthenticationAuthJourney: AuthJourney = FakeAuthJourney
  lazy val cc: ControllerComponents = stubControllerComponents()

  val sessionIdValue: String = "some session id"
  implicit lazy val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(sessionIdValue)))
  val nino: Nino = new Generator(Random).nextNino
  val otherNino: Nino = new Generator(Random).nextNino
  val cacheId: CacheId = CacheId(nino)
  val cacheIdNoSession: CacheId = CacheId.noSession(nino)

  override implicit lazy val app: Application =
    GuiceApplicationBuilder()
      .overrides(bind[AsyncCacheApi].toInstance(fakeAsyncCacheApi))
      .build()

  private lazy val customErrorHandler: CustomErrorHandler = app.injector.instanceOf[CustomErrorHandler]

  protected val badRequestException = new BadRequestException("message")
  protected val notFoundException: NotFoundException = new NotFoundException("Error")
  protected val badGatewayException: BadGatewayException = new BadGatewayException("Error")
  protected val internalServerException: InternalServerException = new InternalServerException("Bad gateway")

  protected def checkControllerResponse(ex: Throwable, futureResult: Future[Result], expStatus: Int): Assertion = {
    val result = Try(Await.result(futureResult, Duration.Inf))
    assert(result == Failure(ex), s" - controller result must be an exception of type ${ex.toString}")
    val errorHandlerResult = Await
      .result(customErrorHandler.onServerError(FakeRequest(), ex), Duration.Inf)
      .header
      .status
    assert(
      errorHandlerResult == expStatus,
      s" - exception thrown by controller must be processed by error handler as status $expStatus"
    )
  }

}

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

package uk.gov.hmrc.tai.connectors

import cats.data.EitherT
import org.mockito.ArgumentMatchersSugar.eqTo
import org.mockito.MockitoSugar
import org.scalactic.source.Position
import org.scalatest.Assertion
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.PlaySpec
import play.api.Application
import play.api.cache.AsyncCacheApi
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Injecting
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainBytes, PlainContent, PlainText}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.{HeaderCarrier, HttpException, RequestId, SessionId}
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.model.admin.{RtiCallToggle, TaxCodeHistoryFromIfToggle}
import uk.gov.hmrc.tai.service.LockService
import uk.gov.hmrc.tai.util.{FakeAsyncCacheApi, WireMockHelper}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.Random

trait ConnectorBaseSpec
    extends PlaySpec with MockitoSugar with WireMockHelper with ScalaFutures with Injecting with IntegrationPatience {

//  implicit val fakeEncrypterDecrypter: Encrypter with Decrypter = new Encrypter with Decrypter {
//    override def encrypt(plain: PlainContent): Crypted = plain match {
//      case PlainText(t) =>
//        println("\nEncrypt " + t)
//        Crypted(t)
//      case PlainBytes(t) => Crypted(t.mkString("Array(", ", ", ")"))
//    }
//
//    override def decrypt(reversiblyEncrypted: Crypted): PlainText = {
//      println("\nRever:" + reversiblyEncrypted)
//      PlainText(reversiblyEncrypted.value)
//    }
//
//    override def decryptAsBytes(reversiblyEncrypted: Crypted): PlainBytes = PlainBytes(
//      reversiblyEncrypted.value.getBytes
//    )
//  }

  val nino: Nino = new Generator(new Random).nextNino

  val sessionId = "testSessionId"
  val requestId = "testRequestId"

  lazy val mockFeatureFlagService: FeatureFlagService = mock[FeatureFlagService]
  lazy val fakeAsyncCacheApi = new FakeAsyncCacheApi()

  protected def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .configure(
        "microservice.services.des-hod.port"                -> server.port(),
        "microservice.services.des-hod.host"                -> "127.0.0.1",
        "microservice.services.nps-hod.port"                -> server.port(),
        "microservice.services.nps-hod.host"                -> "127.0.0.1",
        "microservice.services.citizen-details.port"        -> server.port(),
        "microservice.services.paye.port"                   -> server.port(),
        "microservice.services.file-upload.port"            -> server.port(),
        "microservice.services.file-upload-frontend.port"   -> server.port(),
        "microservice.services.pdf-generator-service.port"  -> server.port(),
        "microservice.services.nps-hod.originatorId"        -> npsOriginatorId,
        "microservice.services.des-hod.originatorId"        -> desOriginatorId,
        "microservice.services.des-hod.da-pta.originatorId" -> desPtaOriginatorId,
        "microservice.services.if-hod.port"                 -> server.port(),
        "microservice.services.if-hod.host"                 -> "127.0.0.1",
        "microservice.services.if-hod.authorizationToken"   -> "ifAuthorization",
        "microservice.services.des-hod.authorizationToken"  -> "desAuthorization"
      )
      .overrides(
        bind[FeatureFlagService].toInstance(mockFeatureFlagService),
        bind[AsyncCacheApi].toInstance(fakeAsyncCacheApi)
      )

  implicit lazy val app: Application = localGuiceApplicationBuilder().build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockFeatureFlagService)
    when(mockFeatureFlagService.getAsEitherT(eqTo[FeatureFlagName](RtiCallToggle))).thenReturn(
      EitherT.rightT(FeatureFlag(RtiCallToggle, isEnabled = false))
    )
    when(mockFeatureFlagService.get(eqTo[FeatureFlagName](TaxCodeHistoryFromIfToggle))).thenReturn(
      Future.successful(FeatureFlag(TaxCodeHistoryFromIfToggle, isEnabled = false))
    )
  }

  implicit val hc: HeaderCarrier = HeaderCarrier(
    sessionId = Some(SessionId(sessionId)),
    requestId = Some(RequestId(requestId))
  )

  implicit lazy val ec: ExecutionContext = inject[ExecutionContext]

  def assertConnectorException[A <: HttpException](call: Future[_], code: Int, message: String)(implicit
    classTag: ClassTag[A],
    pos: Position
  ): Assertion = {
    val ex = intercept[A](Await.result(call, 5.seconds))
    ex.responseCode mustBe code
    ex.message mustBe message
  }

  class FakeLockService extends LockService {
    override def sessionId(implicit hc: HeaderCarrier): String =
      "some session id"

    override def takeLock[L](owner: String)(implicit hc: HeaderCarrier): EitherT[Future, L, Boolean] =
      EitherT.rightT(true)

    override def releaseLock[L](owner: String)(implicit hc: HeaderCarrier): Future[Unit] =
      Future.successful(())
  }
}

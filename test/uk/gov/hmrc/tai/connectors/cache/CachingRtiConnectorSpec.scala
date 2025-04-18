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

package uk.gov.hmrc.tai.connectors.cache

import cats.data.EitherT
import cats.implicits.*
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, spy, times, verify, when}
import play.api.Application
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Format, Reads, Writes}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.AuthorisedFunctions
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongo.cache.DataKey
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.auth.MicroserviceAuthorisedFunctions
import uk.gov.hmrc.tai.connectors.*
import uk.gov.hmrc.tai.model.domain.{AnnualAccount, Available, FourWeekly, Payment}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.cache.TaiSessionCacheRepository
import uk.gov.hmrc.tai.service.{LockService, SensitiveFormatService}

import java.time.LocalDate
import scala.concurrent.Future

class CachingRtiConnectorSpec extends ConnectorBaseSpec {
  private val mockEncryptionService = mock[SensitiveFormatService]
  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .disable[uk.gov.hmrc.tai.modules.LocalGuiceModule]
    .overrides(
      bind[AuthorisedFunctions].to[MicroserviceAuthorisedFunctions],
      bind[RtiConnector].to[CachingRtiConnector],
      bind[RtiConnector].qualifiedWith("default").toInstance(mockRtiConnector),
      bind[TaiSessionCacheRepository].toInstance(mockSessionCacheRepository),
      bind[LockService].toInstance(spyLockService),
      bind[FeatureFlagService].toInstance(mockFeatureFlagService),
      bind[IabdConnector].to[DefaultIabdConnector],
      bind[TaxCodeHistoryConnector].to[DefaultTaxCodeHistoryConnector],
      bind[EmploymentDetailsConnector].to[DefaultEmploymentDetailsConnector],
      bind[TaxAccountConnector].to[DefaultTaxAccountConnector],
      bind[SensitiveFormatService].toInstance(mockEncryptionService)
    )
    .build()
  lazy val repository: MongoLockRepository = app.injector.instanceOf[MongoLockRepository]

  lazy val mockRtiConnector: RtiConnector = mock[RtiConnector]
  lazy val mockSessionCacheRepository: TaiSessionCacheRepository = mock[TaiSessionCacheRepository]
  lazy val spyLockService: FakeLockService = spy(new FakeLockService)

  override implicit val hc: HeaderCarrier = HeaderCarrier()

  override def beforeEach(): Unit = {
    reset(mockRtiConnector, mockSessionCacheRepository, spyLockService, mockEncryptionService)
    when(mockEncryptionService.sensitiveFormatFromReadsWritesJsArray[Seq[AnnualAccount]])
      .thenReturn(Format(implicitly[Reads[Seq[AnnualAccount]]], implicitly[Writes[Seq[AnnualAccount]]]))
  }

  def connector: CachingRtiConnector = app.injector.instanceOf[CachingRtiConnector]

  implicit val userRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  val payment: Payment = Payment(LocalDate.now(), 0, 0, 0, 0, 0, 0, FourWeekly, None)
  val annualAccount: AnnualAccount = AnnualAccount(0, TaxYear(2020), Available, Seq(payment), Seq.empty)

  "Calling CachingRtiConnector.getPaymentsForYearAsEitherT" must {
    "return a Right Seq[AnnualAccount] object" when {
      "no value is cached" in {
        val expected = Seq(annualAccount)

        when(mockSessionCacheRepository.getFromSession[Seq[AnnualAccount]](DataKey(any[String]()))(any(), any()))
          .thenReturn(Future.successful(None))

        when(
          mockSessionCacheRepository.putSession[Seq[AnnualAccount]](DataKey(any[String]()), any())(any(), any(), any())
        )
          .thenReturn(Future.successful(("", "")))

        when(mockRtiConnector.getPaymentsForYear(any(), any())(any(), any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](expected))

        val result = connector.getPaymentsForYear(nino, TaxYear()).value.futureValue

        result mustBe Right(expected)

        verify(mockSessionCacheRepository, times(1))
          .getFromSession[Seq[AnnualAccount]](DataKey(any[String]()))(any(), any())

        verify(mockSessionCacheRepository, times(1))
          .putSession[Seq[AnnualAccount]](DataKey(any[String]()), any())(any(), any(), any())

        verify(mockRtiConnector, times(1)).getPaymentsForYear(any(), any())(any(), any())
        verify(spyLockService, times(1)).takeLock(any())(any())
        verify(spyLockService, times(1)).releaseLock(any())(any())

      }

      "a value is cached" in {
        val expected = Seq(annualAccount)

        when(mockSessionCacheRepository.getFromSession[Seq[AnnualAccount]](DataKey(any[String]()))(any(), any()))
          .thenReturn(Future.successful(Some(expected)))

        when(mockRtiConnector.getPaymentsForYear(any(), any())(any(), any()))
          .thenReturn(null)

        when(
          mockSessionCacheRepository.putSession[Seq[AnnualAccount]](DataKey(any[String]()), any())(any(), any(), any())
        )
          .thenReturn(null)

        val result = connector.getPaymentsForYear(nino, TaxYear()).value.futureValue

        result mustBe Right(expected)

        verify(mockSessionCacheRepository, times(1))
          .getFromSession[Seq[AnnualAccount]](DataKey(any[String]()))(any(), any())

        verify(mockSessionCacheRepository, times(0))
          .putSession[Seq[AnnualAccount]](DataKey(any[String]()), any())(any(), any(), any())

        verify(mockRtiConnector, times(0)).getPaymentsForYear(any(), any())(any(), any())
        verify(spyLockService, times(1)).takeLock(any())(any())
        verify(spyLockService, times(1)).releaseLock(any())(any())
        verify(mockEncryptionService, times(1)).sensitiveFormatFromReadsWritesJsArray[Seq[AnnualAccount]](any(), any())
      }
    }

    "return a Left RtiPaymentsForYearError object" in {
      when(mockRtiConnector.getPaymentsForYear(any(), any())(any(), any()))
        .thenReturn(EitherT.leftT[Future, Seq[AnnualAccount]](UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)))
      when(mockSessionCacheRepository.getFromSession[Seq[AnnualAccount]](DataKey(any[String]()))(any(), any()))
        .thenReturn(Future.successful(None))

      when(
        mockSessionCacheRepository.putSession[Seq[AnnualAccount]](DataKey(any[String]()), any())(any(), any(), any())
      )
        .thenReturn(Future.successful(("", "")))

      val result = connector.getPaymentsForYear(nino, TaxYear()).value.futureValue
      result mustBe a[Left[_, _]]
      verify(mockRtiConnector, times(1)).getPaymentsForYear(any(), any())(any(), any())
      verify(spyLockService, times(1)).takeLock(any())(any())
      verify(spyLockService, times(1)).releaseLock(any())(any())
    }

    "returns an exception and the lock is released" when {
      "future failed" in {
        val errorMessage = "Error message"
        when(mockRtiConnector.getPaymentsForYear(any(), any())(any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, Seq[AnnualAccount]](Future.failed(new Exception(errorMessage)))
          )

        when(mockSessionCacheRepository.getFromSession[Seq[AnnualAccount]](DataKey(any[String]()))(any(), any()))
          .thenReturn(Future.successful(None))

        when(
          mockSessionCacheRepository.putSession[Seq[AnnualAccount]](DataKey(any[String]()), any())(any(), any(), any())
        )
          .thenReturn(Future.successful(("", "")))

        val result = connector.getPaymentsForYear(nino, TaxYear()).value
        whenReady(result.failed) { ex =>
          ex.getMessage mustBe errorMessage
        }

        verify(mockRtiConnector, times(1)).getPaymentsForYear(any(), any())(any(), any())
        verify(spyLockService, times(1)).takeLock(any())(any())
        verify(spyLockService, times(1)).releaseLock(any())(any())

      }
    }
  }
}

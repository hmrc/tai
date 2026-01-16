/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.tai.service

import cats.effect.unsafe.implicits.global
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.tai.config.CacheConfig
import uk.gov.hmrc.tai.repositories.cache.TaiSessionCacheRepository
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class CacheServiceSpec extends BaseSpec {
  private val mockTaiSessionCacheRepository = mock[TaiSessionCacheRepository]
  private val mockCacheConfig = mock[CacheConfig]
  private val sut: CacheService = new CacheService(mockTaiSessionCacheRepository, mockCacheConfig)
  private val key: String = "key"
  private val dummyValue: String = "dummy"
  private val secondDummyValue: String = "dummy2"
  private val upstreamErrorResponse = UpstreamErrorResponse("", 500, 500)
  private val upstreamErrorResponseWrapper: UpstreamErrorResponse = UpstreamErrorResponse("", 500, 500)

  private val leftUpstreamErrorResponse: Left[UpstreamErrorResponse, String] =
    Left[UpstreamErrorResponse, String](upstreamErrorResponseWrapper)

  private val rightUpstreamErrorResponseWrapper: Right[UpstreamErrorResponse, String] =
    Right[UpstreamErrorResponse, String](secondDummyValue)
  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockTaiSessionCacheRepository)
    reset(mockCacheConfig)
    when(mockCacheConfig.cacheErrorInSecondsTTL).thenReturn(60L)

    ()
  }

  "cacheEither" must {
    "return right block value when not in session repository and save to session repository" in {
      when(mockTaiSessionCacheRepository.getEitherFromSession(any())(any(), any())).thenReturn(Future.successful(None))
      when(mockTaiSessionCacheRepository.putSession(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(Tuple2("", "")))
      val block = Future.successful[Either[UpstreamErrorResponse, String]](Right(dummyValue))
      whenReady(sut.cacheEither(key)(block).unsafeToFuture()) { result =>
        result mustBe Right(dummyValue)
        verify(mockTaiSessionCacheRepository, times(1)).getEitherFromSession(any())(any(), any())
        verify(mockTaiSessionCacheRepository, times(1)).putSession(
          any(),
          ArgumentMatchers.eq(Right[UpstreamErrorResponse, String](dummyValue))
        )(any(), any(), any())
        verify(mockCacheConfig, times(0)).cacheErrorInSecondsTTL
      }
    }
    "return right cached block value when in session repository and don't re-save to session repository" in {
      when(mockTaiSessionCacheRepository.getEitherFromSession(any())(any(), any()))
        .thenReturn(Future.successful(Some(rightUpstreamErrorResponseWrapper)))
      val block = Future.successful[Either[UpstreamErrorResponse, String]](Right(dummyValue))
      whenReady(sut.cacheEither(key)(block).unsafeToFuture()) { result =>
        result mustBe Right(secondDummyValue)
        verify(mockTaiSessionCacheRepository, times(1)).getEitherFromSession(any())(any(), any())
        verify(mockTaiSessionCacheRepository, times(0)).putSession(any(), any())(any(), any(), any())
        verify(mockCacheConfig, times(0)).cacheErrorInSecondsTTL
      }
    }

    "treat cache read failure (ecryption/deserialisation error) as cache miss and re-execute block" in {
      when(mockTaiSessionCacheRepository.getEitherFromSession(any())(any(), any()))
        .thenReturn(Future.successful(None))
      when(mockTaiSessionCacheRepository.putSession(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(Tuple2("", "")))

      val block = Future.successful[Either[UpstreamErrorResponse, String]](Right(dummyValue))

      whenReady(sut.cacheEither(key)(block).unsafeToFuture()) { result =>
        result mustBe Right(dummyValue)
        verify(mockTaiSessionCacheRepository, times(1)).getEitherFromSession(any())(any(), any())
        verify(mockTaiSessionCacheRepository, times(1))
          .putSession(any(), ArgumentMatchers.eq(Right[UpstreamErrorResponse, String](dummyValue)))(any(), any(), any())
      }
    }

    "return left (error) from block when not in session repository and save to session repository if appConfig.cacheErrorInSecondsTTL > 0" in {
      when(mockTaiSessionCacheRepository.getEitherFromSession(any())(any(), any())).thenReturn(Future.successful(None))
      when(mockTaiSessionCacheRepository.putSession(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(Tuple2("", "")))
      val block = Future.successful[Either[UpstreamErrorResponse, String]](leftUpstreamErrorResponse)
      whenReady(sut.cacheEither(key)(block).unsafeToFuture()) { result =>
        result mustBe leftUpstreamErrorResponse
        val putArgCaptor: ArgumentCaptor[Either[UpstreamErrorResponse, String]] =
          ArgumentCaptor.forClass(classOf[Either[UpstreamErrorResponse, String]])
        verify(mockTaiSessionCacheRepository, times(1)).getEitherFromSession(any())(any(), any())
        verify(mockTaiSessionCacheRepository, times(1)).putSession(any(), putArgCaptor.capture())(any(), any(), any())
        putArgCaptor.getValue.swap.map(ex =>
          UpstreamErrorResponse(ex.message, ex.statusCode, ex.reportAs)
        ) mustBe Right(upstreamErrorResponse)
        verify(mockCacheConfig, times(1)).cacheErrorInSecondsTTL
      }
    }

    "return left (error) from block when not in session repository and save to session repository" in {
      when(mockTaiSessionCacheRepository.getEitherFromSession(any())(any(), any())).thenReturn(Future.successful(None))
      when(mockTaiSessionCacheRepository.putSession(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(Tuple2("", "")))
      val block = Future.successful[Either[UpstreamErrorResponse, String]](leftUpstreamErrorResponse)
      whenReady(sut.cacheEither(key)(block).unsafeToFuture()) { result =>
        result mustBe leftUpstreamErrorResponse
        val putArgCaptor: ArgumentCaptor[Either[UpstreamErrorResponse, String]] =
          ArgumentCaptor.forClass(classOf[Either[UpstreamErrorResponse, String]])
        verify(mockTaiSessionCacheRepository, times(1)).getEitherFromSession(any())(any(), any())
        verify(mockTaiSessionCacheRepository, times(1)).putSession(any(), putArgCaptor.capture())(any(), any(), any())
        putArgCaptor.getValue.swap.map(ex =>
          UpstreamErrorResponse(ex.message, ex.statusCode, ex.reportAs)
        ) mustBe Right(upstreamErrorResponse)
        verify(mockCacheConfig, times(1)).cacheErrorInSecondsTTL
      }
    }
    "return left (error) from block when not in session repository and NOT save to session repository when error ttl zero" in {
      when(mockCacheConfig.cacheErrorInSecondsTTL).thenReturn(0L)
      when(mockTaiSessionCacheRepository.getEitherFromSession(any())(any(), any())).thenReturn(Future.successful(None))
      when(mockTaiSessionCacheRepository.putSession(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(Tuple2("", "")))
      val block = Future.successful[Either[UpstreamErrorResponse, String]](leftUpstreamErrorResponse)
      whenReady(sut.cacheEither(key)(block).unsafeToFuture()) { result =>
        result mustBe leftUpstreamErrorResponse
        verify(mockTaiSessionCacheRepository, times(1)).getEitherFromSession(any())(any(), any())
        verify(mockTaiSessionCacheRepository, times(0)).putSession(any(), any())(any(), any(), any())
        verify(mockCacheConfig, times(1)).cacheErrorInSecondsTTL
      }
    }

    "return left (error) from session repository when in session repository and don't re-save to session repository" in {
      when(mockTaiSessionCacheRepository.getEitherFromSession(any())(any(), any()))
        .thenReturn(Future.successful(Some(leftUpstreamErrorResponse)))
      val block = Future.successful[Either[UpstreamErrorResponse, String]](leftUpstreamErrorResponse)
      whenReady(sut.cacheEither(key)(block).unsafeToFuture()) { result =>
        result mustBe leftUpstreamErrorResponse
        verify(mockTaiSessionCacheRepository, times(1)).getEitherFromSession(any())(any(), any())
        verify(mockTaiSessionCacheRepository, times(0)).putSession(any(), any())(any(), any(), any())
        verify(mockCacheConfig, times(0)).cacheErrorInSecondsTTL
      }
    }
  }

}

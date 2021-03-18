/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.tai.repositories

import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import uk.gov.hmrc.tai.connectors.{CacheConnector, CompanyCarConnector}
import uk.gov.hmrc.tai.model.domain.benefits.CompanyCarBenefit
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class CompanyCarBenefitRepositorySpec extends BaseSpec {

  "carBenefit" when {
    "there is car benefit in the cache as an empty list" must {
      "return empty list" in {
        val carBenefitSeq = Nil

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.find[Seq[CompanyCarBenefit]](any(), any())(any()))
          .thenReturn(Future.successful(Some(carBenefitSeq)))

        val sut = createSUT(mockCacheConnector, mock[CompanyCarConnector])
        Await.result(sut.carBenefit(nino, TaxYear(2017)), 5 seconds) mustBe carBenefitSeq
      }
    }

    "there are multiple car benefits in the cache" must {
      "return these multiple car benefits" in {
        val carBenefitSeqWithVersion = Seq(
          CompanyCarBenefit(10, 10, Nil, Some(sampleNinoVersion)),
          CompanyCarBenefit(20, 20, Nil, Some(sampleNinoVersion)))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.find[Seq[CompanyCarBenefit]](any(), any())(any()))
          .thenReturn(Future.successful(Some(carBenefitSeqWithVersion)))

        val sut = createSUT(mockCacheConnector, mock[CompanyCarConnector])
        Await.result(sut.carBenefit(nino, TaxYear(2017)), 5 seconds) mustBe carBenefitSeqWithVersion
      }
    }

    "there is no car benefit in the cache" must {
      "return the empty list coming from the company car service and save it in the cache" in {
        val carBenefitSeqFromCache = None
        val carBenefitFromCompanyCarService = Nil

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.find[Seq[CompanyCarBenefit]](any(), any())(any()))
          .thenReturn(Future.successful(carBenefitSeqFromCache))
        when(mockCacheConnector.createOrUpdate[Seq[CompanyCarBenefit]](any(), any(), any())(any()))
          .thenReturn(Future.successful(carBenefitFromCompanyCarService))

        val mockCompanyCarConnector = mock[CompanyCarConnector]
        when(mockCompanyCarConnector.carBenefits(any(), any())(any()))
          .thenReturn(Future.successful(carBenefitFromCompanyCarService))

        val sut = createSUT(mockCacheConnector, mockCompanyCarConnector)
        Await.result(sut.carBenefit(nino, TaxYear(2017)), 5 seconds) mustBe carBenefitFromCompanyCarService

        verify(mockCacheConnector, times(1))
          .createOrUpdate[Seq[CompanyCarBenefit]](
            meq(cacheId),
            meq(carBenefitFromCompanyCarService),
            meq(sut.CarBenefitKey))(any())
      }

      "return the non-empty list coming from the company car service and save it in the cache" in {
        val carBenefitSeqFromCache = None
        val carBenefitSeq = Seq(CompanyCarBenefit(10, 10, Nil))
        val carBenefitSeqWithVersion = Seq(CompanyCarBenefit(10, 10, Nil, Some(sampleNinoVersion)))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.find[Seq[CompanyCarBenefit]](any(), any())(any()))
          .thenReturn(Future.successful(carBenefitSeqFromCache))
        when(mockCacheConnector.createOrUpdate[Seq[CompanyCarBenefit]](any(), any(), any())(any()))
          .thenReturn(Future.successful(carBenefitSeqWithVersion))

        val mockCompanyCarConnector = mock[CompanyCarConnector]
        when(mockCompanyCarConnector.carBenefits(any(), any())(any()))
          .thenReturn(Future.successful(carBenefitSeq))

        val sut = createSUT(mockCacheConnector, mockCompanyCarConnector)
        Await.result(sut.carBenefit(nino, TaxYear(2017)), 5 seconds) mustBe carBenefitSeqWithVersion

        verify(mockCacheConnector, times(1))
          .createOrUpdate[Seq[CompanyCarBenefit]](meq(cacheId), meq(carBenefitSeqWithVersion), meq(sut.CarBenefitKey))(
            any())
      }
    }
  }

  private val sampleNinoVersion = 4

  private def createSUT(cacheConnector: CacheConnector, companyCarConnector: CompanyCarConnector) =
    new CompanyCarBenefitRepository(cacheConnector, companyCarConnector) {
      when(companyCarConnector.ninoVersion(any())(any()))
        .thenReturn(Future.successful(sampleNinoVersion))
    }
}

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

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.tai.connectors.{CacheConnector, CacheId, CompanyCarConnector}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.model.domain.benefits.CompanyCarBenefit
import uk.gov.hmrc.tai.util.MongoConstants

@Singleton
class CompanyCarBenefitRepository @Inject()(cacheConnector: CacheConnector, companyCarConnector: CompanyCarConnector)(
  implicit ec: ExecutionContext)
    extends MongoConstants {

  def carBenefit(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[CompanyCarBenefit]] = {
    val cacheId = CacheId(nino)

    cacheConnector.find[Seq[CompanyCarBenefit]](cacheId, CarBenefitKey) flatMap {
      case None => {
        val companyCarBenefits = companyCarConnector.carBenefits(nino, taxYear)
        val version = companyCarConnector.ninoVersion(nino)

        val companyCarBenefitsWithVersion = for {
          cc  <- companyCarBenefits
          ver <- version
        } yield cc.map(cc => CompanyCarBenefit(cc.employmentSeqNo, cc.grossAmount, cc.companyCars, Some(ver)))

        companyCarBenefitsWithVersion.flatMap { result =>
          cacheConnector.createOrUpdate(cacheId, result, CarBenefitKey).map(_ => result)
        }
      }
      case Some(seq) => Future.successful(seq)
    }
  }
}

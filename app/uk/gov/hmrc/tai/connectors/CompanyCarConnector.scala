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

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.Reads
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.model.domain.benefits.CompanyCarBenefit
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.transformation.CompanyCarBenefitTransformer.*

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CompanyCarConnector @Inject() (httpHandler: HttpHandler, urls: PayeUrls)(implicit ec: ExecutionContext) {

  def carBenefits(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[CompanyCarBenefit]] =
    httpHandler.getFromApi(urls.carBenefitsForYearUrl(nino, taxYear), APITypes.CompanyCarAPI, Seq.empty) map { json =>
      json.as[Seq[CompanyCarBenefit]](Reads.seq(companyCarBenefitReadsFromHod))
    }

  def ninoVersion(nino: Nino)(implicit hc: HeaderCarrier): Future[Int] =
    httpHandler.getFromApi(urls.ninoVersionUrl(nino), APITypes.CompanyCarAPI, Seq.empty) map { json =>
      json.as[Int]
    }

}

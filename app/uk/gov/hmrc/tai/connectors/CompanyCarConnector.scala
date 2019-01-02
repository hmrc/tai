/*
 * Copyright 2019 HM Revenue & Customs
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
import uk.gov.hmrc.tai.model.domain.formatters.CompanyCarBenefitFormatters
import uk.gov.hmrc.tai.model.domain.benefits.{CompanyCarBenefit, WithdrawCarAndFuel}
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.tai.TaxYear

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future

@Singleton
class CompanyCarConnector @Inject()(httpHandler: HttpHandler,
                                    urls: PayeUrls) extends CompanyCarBenefitFormatters {

  def carBenefits(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier):Future[Seq[CompanyCarBenefit]] = {
    httpHandler.getFromApi(urls.carBenefitsForYearUrl(nino, taxYear), APITypes.CompanyCarAPI) map { json=>
      json.as[Seq[CompanyCarBenefit]](Reads.seq(companyCarBenefitReads))
    }
  }

  def ninoVersion(nino: Nino)(implicit hc: HeaderCarrier): Future[Int] = {
    httpHandler.getFromApi(urls.ninoVersionUrl(nino), APITypes.CompanyCarAPI) map{
      json => json.as[Int]
    }
  }

  def withdrawCarBenefit(nino: Nino,
                         taxYear: TaxYear,
                         employmentSequenceNumber: Int,
                         carSequenceNumber: Int,
                         postData: WithdrawCarAndFuel)(implicit hc: HeaderCarrier): Future[String] = {

    val url = urls.removeCarBenefitUrl(nino, taxYear, employmentSequenceNumber, carSequenceNumber)
    httpHandler.postToApi[WithdrawCarAndFuel](url, postData, APITypes.CompanyCarAPI)(hc, companyCarRemoveWrites) map {
      httpResponse =>
        val json = httpResponse.json
        (json \ "transaction" \ "oid").as[String]
    }
  }
}
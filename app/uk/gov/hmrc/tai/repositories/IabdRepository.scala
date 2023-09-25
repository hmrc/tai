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

package uk.gov.hmrc.tai.repositories

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.tai.connectors.cache.{Caching, IabdConnector}
import uk.gov.hmrc.tai.model.domain.formatters.IabdHodFormatters
import uk.gov.hmrc.tai.model.domain.response.HodUpdateResponse
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.MongoConstants

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IabdRepository @Inject()(cache: Caching, iabdConnector: IabdConnector)(implicit ec: ExecutionContext)
    extends MongoConstants with IabdHodFormatters {

  def iabds(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue] = {
    cache.cacheFromApi(
      nino,
      s"$IabdMongoKey${taxYear.year}",
      iabdConnector.iabds(nino: Nino, taxYear: TaxYear).map(_.filter(_.`type`.contains(NewEstimatedPay))).map(Json.toJson(_)) recover {
        case _: NotFoundException => Json.toJson(Json.obj("error" -> "NOT_FOUND"))
      }).map { json =>
      val responseNotFound = (json \ "error").asOpt[String].contains("NOT_FOUND")
      if (responseNotFound) {
        throw new NotFoundException(s"No iadbs found for year $taxYear")
      } else {
        json.as[JsValue](iabdEstimatedPayReads)
      }
    }
    /*.recover {
        case _: NotFoundException => throw new NotFoundException(s"No iadbs found for year $taxYear")
      })*/
  }

  def updateTaxCodeAmount(nino: Nino, taxYear: TaxYear, version: Int, employmentId: Int, iabdType: Int, amount: Int)(
    implicit hc: HeaderCarrier): Future[HodUpdateResponse] =
    iabdConnector.updateTaxCodeAmount(nino, taxYear, employmentId, version, iabdType, amount)
}

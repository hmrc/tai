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
import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, _}
import uk.gov.hmrc.tai.config.{HipConfig, NpsConfig}
import uk.gov.hmrc.tai.connectors.cache.CachingConnector

import java.util.UUID
import scala.concurrent.Future

@Singleton
class CachingEmploymentDetailsConnector @Inject() (
  @Named("default") underlying: EmploymentDetailsConnector,
  config: NpsConfig,
  cachingConnector: CachingConnector
) extends EmploymentDetailsConnector {

  override val originatorId: String = config.originatorId
  override val baseUrl: String = config.baseURL
  override def getEmploymentDetailsAsEitherT(nino: Nino, year: Int)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, HodResponse] =
    cachingConnector.cacheEitherT(s"employment-details-$nino-$year") {
      underlying.getEmploymentDetailsAsEitherT(nino, year)
    }
}

class DefaultEmploymentDetailsConnector @Inject() (httpHandler: HttpHandler, config: HipConfig)
    extends EmploymentDetailsConnector {

  override val originatorId: String = config.originatorId
  override val baseUrl: String = config.baseURL
  def getEmploymentDetailsAsEitherT(nino: Nino, year: Int)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, HodResponse] = {
    val urlToRead = hipPathUrl(nino, s"tax-year/$year/employment-details")
    httpHandler.getFromApiAsEitherT(urlToRead, basicNpsHeaders(hc))
  }
}

class DefaultEmploymentDetailsConnectorNps @Inject() (httpHandler: HttpHandler, config: NpsConfig)
    extends EmploymentDetailsConnector {

  override val originatorId: String = config.originatorId
  override val baseUrl: String = config.baseURL
  def getEmploymentDetailsAsEitherT(nino: Nino, year: Int)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, HodResponse] = {
    val urlToRead = npsPathUrl(nino, s"employment/$year")
    httpHandler.getFromApiAsEitherT(urlToRead, basicNpsHeaders(hc))
  }
}

trait EmploymentDetailsConnector {

  val originatorId: String
  val baseUrl: String
  def npsPathUrl(nino: Nino, path: String) = s"$baseUrl/person/$nino/$path"
  def hipPathUrl(nino: Nino, path: String) = s"$baseUrl/employment/employee/$nino/$path"

  def basicNpsHeaders(hc: HeaderCarrier): Seq[(String, String)] =
    Seq(
      "Gov-Uk-Originator-Id" -> originatorId,
      HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
      "CorrelationId"        -> UUID.randomUUID().toString
    )

  def getEmploymentDetailsAsEitherT(nino: Nino, year: Int)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, HodResponse]
}

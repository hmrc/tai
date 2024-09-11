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
import play.api.http.MimeTypes
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.config.{HipConfig, NpsConfig}
import uk.gov.hmrc.tai.connectors.cache.CachingConnector
import uk.gov.hmrc.tai.model.HodResponse
import uk.gov.hmrc.tai.model.admin.HipToggleEmploymentDetails
import uk.gov.hmrc.tai.service.SensitiveFormatService

import java.nio.charset.StandardCharsets
import java.util.{Base64, UUID}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CachingEmploymentDetailsConnector @Inject() (
  @Named("default") underlying: EmploymentDetailsConnector,
  cachingConnector: CachingConnector,
  sensitiveFormatService: SensitiveFormatService
) extends EmploymentDetailsConnector {

  override def getEmploymentDetailsAsEitherT(nino: Nino, year: Int)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, HodResponse] =
    cachingConnector.cacheEitherT(s"employment-details-$nino-$year") {
      underlying.getEmploymentDetailsAsEitherT(nino, year)
    }(sensitiveFormatService.sensitiveFormatFromReadsWrites[HodResponse], implicitly)
}

class DefaultEmploymentDetailsConnector @Inject() (
  httpHandler: HttpHandler,
  npsConfig: NpsConfig,
  hipConfig: HipConfig,
  featureFlagService: FeatureFlagService
)(implicit ec: ExecutionContext)
    extends EmploymentDetailsConnector {

  def getEmploymentDetailsAsEitherT(nino: Nino, year: Int)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, HodResponse] =
    EitherT(featureFlagService.get(HipToggleEmploymentDetails).flatMap { toggle =>
      val (baseUrl, originatorId, extraInfo) =
        if (toggle.isEnabled) {
          (hipConfig.baseURL, hipConfig.originatorId, Some(Tuple2(hipConfig.clientId, hipConfig.clientSecret)))
        } else {
          (npsConfig.baseURL, npsConfig.originatorId, None)
        }

      def pathUrl(nino: Nino): String = if (toggle.isEnabled) {
        s"$baseUrl/employment/employee/$nino/tax-year/$year/employment-details"
      } else {
        s"$baseUrl/person/$nino/employment/$year"
      }

      val urlToRead = pathUrl(nino)
      httpHandler.getFromApiAsEitherT(urlToRead, basicHeaders(originatorId, hc, extraInfo)).value
    })
}

trait EmploymentDetailsConnector {
  def basicHeaders(
    originatorId: String,
    hc: HeaderCarrier,
    hipExtraInfo: Option[(String, String)]
  ): Seq[(String, String)] = {
    val hipAuth = hipExtraInfo.fold[Seq[(String, String)]](Seq.empty) { case (clientId, clientSecret) =>
      val token = Base64.getEncoder.encodeToString(s"$clientId:$clientSecret".getBytes(StandardCharsets.UTF_8))
      Seq(
        HeaderNames.authorisation -> s"Basic $token"
      )
    }
    Seq(
      play.api.http.HeaderNames.CONTENT_TYPE -> MimeTypes.JSON,
      "Gov-Uk-Originator-Id"                 -> originatorId,
      HeaderNames.xSessionId                 -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId                 -> hc.requestId.fold("-")(_.value),
      "CorrelationId"                        -> UUID.randomUUID().toString
    ) ++ hipAuth
  }

  def getEmploymentDetailsAsEitherT(nino: Nino, year: Int)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, HodResponse]
}

/*
 * Copyright 2025 HM Revenue & Customs
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
import play.api.Logging
import play.api.http.Status
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.ETag
import uk.gov.hmrc.tai.model.domain.{Person, PersonFormatter}
import uk.gov.hmrc.tai.model.enums.APITypes

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CitizenDetailsConnector @Inject() (metrics: Metrics, httpClientV2: HttpClientV2, urls: CitizenDetailsUrls)(
  implicit ec: ExecutionContext
) extends BaseConnector with Logging {

  override val originatorId: String = ""

  def getPerson(nino: Nino)(implicit hc: HeaderCarrier): Future[Person] = {
    val api = APITypes.NpsPersonAPI
    val url = urls.designatoryDetailsUrl(nino)

    implicit val responseHandler: HttpReads[HttpResponse] = (_: String, url: String, httpResponse: HttpResponse) =>
      httpResponse.status match {
        case Status.OK | Status.LOCKED =>
          metrics.incrementSuccessCounter(api)
          httpResponse
        case _ =>
          logger.warn(
            s"HttpHandler - Error received with status: ${httpResponse.status} for url $url with message body ${httpResponse.body}"
          )
          metrics.incrementFailedCounter(api)
          throw new HttpException(httpResponse.body, httpResponse.status)
      }

    val timerContext = metrics.startTimer(api)

    val futureResponse = httpClientV2
      .get(url = url"$url")
      .execute[HttpResponse](responseHandler, ec)

    futureResponse.flatMap { httpResponse =>
      timerContext.stop()

      httpResponse.status match {
        case Status.OK =>
          val person = httpResponse.json.as[Person](PersonFormatter.personHodRead)
          Future.successful(person)
        case Status.LOCKED =>
          Future.successful(Person.createLockedUser(nino))
        case _ =>
          metrics.incrementFailedCounter(api)
          Future.failed(new HttpException(httpResponse.body, httpResponse.status))
      }
    }
  }

  def getEtag(nino: Nino)(implicit hc: HeaderCarrier): Future[Option[ETag]] = {

    val url = urls.etagUrl(nino)
    val futureResponse: Future[Either[UpstreamErrorResponse, HttpResponse]] = httpClientV2
      .get(url"$url")
      .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)

    futureResponse.flatMap {
      case Right(httpResponse) =>
        httpResponse.status match {
          case Status.OK =>
            Future.successful(httpResponse.json.asOpt[ETag])
          case errorStatus =>
            logger.error(s"[CitizenDetailsService.getEtag] Failed to get an ETag from citizen-details: $errorStatus")
            Future.successful(None)
        }
      case Left(error) =>
        logger.error(s"[CitizenDetailsService.getEtag] Upstream error occurred: ${error.statusCode} url: $url")
        Future.successful(None)
    }
  }
}

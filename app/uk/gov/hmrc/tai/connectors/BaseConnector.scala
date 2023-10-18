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

import play.api.Logging
import play.api.http.Status
import play.api.libs.json.Format
import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.enums.APITypes.APITypes

import scala.concurrent.{ExecutionContext, Future}

abstract class BaseConnector(metrics: Metrics, httpClient: HttpClient)(
  implicit ec: ExecutionContext
) extends RawResponseReads with Logging{

  def originatorId: String

  val defaultVersion: Int = -1

  def getVersionFromHttpHeader(httpResponse: HttpResponse): Int = {
    //todo: etag should be inserted in the case class with the data in order to avoid mis-use
    val npsVersion: Int = httpResponse.header("ETag").map(_.toInt).getOrElse(defaultVersion)
    npsVersion
  }

  @deprecated("this method will be removed. Use uk.gov.hmrc.tai.connectors.HttpHandler.getFromApi instead", "")
  def getFromNps[A](url: String, api: APITypes, headerCarrier: Seq[(String, String)])(implicit hc: HeaderCarrier, formats: Format[A]): Future[(A, Int)] = {
    val timerContext = metrics.startTimer(api)
    val futureResponse = httpClient.GET[HttpResponse](url = url, headers = headerCarrier)
    futureResponse.flatMap { httpResponse =>
      timerContext.stop()
      httpResponse.status match {
        case Status.OK => {
          metrics.incrementSuccessCounter(api)
          Future.successful((httpResponse.json.as[A], getVersionFromHttpHeader(httpResponse)))
        }

        case Status.NOT_FOUND => {
          logger.warn(
            s"NPSAPI - No DATA Found error returned from NPS for $api with status ${httpResponse.status} for url $url")
          metrics.incrementFailedCounter(api)
          Future.failed(new NotFoundException(httpResponse.body))
        }

        case Status.INTERNAL_SERVER_ERROR => {
          logger.warn(
            s"NPSAPI - Internal Server error returned from NPS for $api with status ${httpResponse.status} for url $url")
          metrics.incrementFailedCounter(api)
          Future.failed(new InternalServerException(httpResponse.body))
        }

        case Status.BAD_REQUEST => {
          logger.warn(
            s"NPSAPI - Bad request exception returned from NPS for $api with status ${httpResponse.status} for url $url")
          metrics.incrementFailedCounter(api)
          Future.failed(new BadRequestException(httpResponse.body))
        }

        case _ => {
          logger.warn(
            s"NPSAPI - A Server error returned from NPS for $api with status ${httpResponse.status} for url $url")
          metrics.incrementFailedCounter(api)
          Future.failed(new HttpException(httpResponse.body, httpResponse.status))
        }
      }
    }
  }

  @deprecated("this method will be removed. Use uk.gov.hmrc.tai.connectors.HttpHandler.getFromApi instead", "")
  def getFromDes[A](url: String, api: APITypes, headers: Seq[(String, String)])(implicit hc: HeaderCarrier, formats: Format[A]): Future[(A, Int)] = {
    val timerContext = metrics.startTimer(api)
    val futureResponse = httpClient.GET[HttpResponse](url = url, headers = headers)
    futureResponse.flatMap { httpResponse =>
      timerContext.stop()
      httpResponse.status match {
        case Status.OK => {
          metrics.incrementSuccessCounter(api)
          Future.successful((httpResponse.json.as[A], getVersionFromHttpHeader(httpResponse)))
        }

        case Status.NOT_FOUND => {
          logger.warn(s"DESAPI - No DATA Found error returned from DES HODS for $api and url $url")
          metrics.incrementFailedCounter(api)
          Future.failed(new NotFoundException(httpResponse.body))
        }

        case Status.INTERNAL_SERVER_ERROR => {
          logger.warn(s"DESAPI - Internal Server error returned from DES HODS for $api and url $url")
          metrics.incrementFailedCounter(api)
          Future.failed(new InternalServerException(httpResponse.body))
        }

        case Status.BAD_REQUEST => {
          logger.warn(s"DESAPI - Bad request exception returned from DES HODS for $api and url $url")
          metrics.incrementFailedCounter(api)
          Future.failed(new BadRequestException(httpResponse.body))
        }

        case _ => {
          logger.warn(s"DESAPI - A Server error returned from DES HODS for $api and url $url")
          metrics.incrementFailedCounter(api)
          Future.failed(new HttpException(httpResponse.body, httpResponse.status))
        }
      }
    }
  }

}

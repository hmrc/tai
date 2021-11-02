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

package uk.gov.hmrc.tai.connectors

import play.Logger
import play.api.http.Status
import play.api.libs.json.{Format, Writes}
import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.enums.APITypes.APITypes
import uk.gov.hmrc.tai.model.rti.{RtiData, RtiStatus}

import scala.concurrent.{ExecutionContext, Future}

abstract class BaseConnector(auditor: Auditor, metrics: Metrics, httpClient: HttpClient)(
  implicit ec: ExecutionContext
) extends RawResponseReads {

  def originatorId: String

  val defaultVersion: Int = -1

  def getVersionFromHttpHeader(httpResponse: HttpResponse): Int = {
    val npsVersion: Int = httpResponse.header("ETag").map(_.toInt).getOrElse(defaultVersion)
    npsVersion
  }

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
          Logger.warn(
            s"NPSAPI - No DATA Found error returned from NPS for $api with status ${httpResponse.status} for url $url")
          metrics.incrementFailedCounter(api)
          Future.failed(new NotFoundException(httpResponse.body))
        }

        case Status.INTERNAL_SERVER_ERROR => {
          Logger.warn(
            s"NPSAPI - Internal Server error returned from NPS for $api with status ${httpResponse.status} for url $url")
          metrics.incrementFailedCounter(api)
          Future.failed(new InternalServerException(httpResponse.body))
        }

        case Status.BAD_REQUEST => {
          Logger.warn(
            s"NPSAPI - Bad request exception returned from NPS for $api with status ${httpResponse.status} for url $url")
          metrics.incrementFailedCounter(api)
          Future.failed(new BadRequestException(httpResponse.body))
        }

        case _ => {
          Logger.warn(
            s"NPSAPI - A Server error returned from NPS for $api with status ${httpResponse.status} for url $url")
          metrics.incrementFailedCounter(api)
          Future.failed(new HttpException(httpResponse.body, httpResponse.status))
        }
      }
    }
  }

  def postToNps[A](url: String, api: APITypes, postData: A, headers: Seq[(String, String)])(
    implicit hc: HeaderCarrier,
    writes: Writes[A]): Future[HttpResponse] = {
    val timerContext = metrics.startTimer(api)
    val futureResponse = httpClient.POST(url, postData, headers)
    futureResponse.flatMap { httpResponse =>
      timerContext.stop()
      httpResponse.status match {
        case (Status.OK | Status.NO_CONTENT | Status.ACCEPTED) => {
          metrics.incrementSuccessCounter(api)
          Future.successful(httpResponse)
        }
        case _ => {
          Logger.warn(
            s"NPSAPI - A server error returned from NPS HODS in postToNps with status " +
              httpResponse.status + " url " + url)
          metrics.incrementFailedCounter(api)
          Future.failed(new HttpException(httpResponse.body, httpResponse.status))
        }
      }
    }
  }

  def getFromRTIWithStatus[A](url: String, api: APITypes, reqNino: String, headers: Seq[(String, String)])(
    implicit hc: HeaderCarrier,
    formats: Format[A]): Future[(Option[RtiData], RtiStatus)] = {
    val timerContext = metrics.startTimer(api)
    val futureResponse = httpClient.GET[HttpResponse](url = url, headers = headers)
    futureResponse.flatMap { res =>
      timerContext.stop()
      res.status match {
        case Status.OK => {
          metrics.incrementSuccessCounter(api)

          val rtiData = res.json.as[RtiData]
          if (reqNino != rtiData.nino) {
            Logger.warn(s"RTIAPI - Incorrect Payload returned from RTI HODS for $reqNino")

            auditor.sendDataEvent(
              "RTI returned incorrect account",
              Map(
                "request Nino"      -> reqNino,
                "rti response Nino" -> rtiData.nino,
                "tax year"          -> rtiData.taxYear.twoDigitRange,
                "request Id"        -> rtiData.requestId)
            )

            Future.successful((None, RtiStatus(res.status, "Incorrect RTI Payload")))
          } else {
            Future.successful((Some(rtiData), RtiStatus(res.status, "Success")))
          }
        }
        case Status.BAD_REQUEST => {
          Logger.warn(s"RTIAPI - Bad Request error returned from RTI HODS for $reqNino and url $url")
          Future.successful((None, RtiStatus(res.status, res.body)))
        }
        case Status.NOT_FOUND => {
          Logger.warn(s"RTIAPI - No DATA Found error returned from RTI HODS for $reqNino and url $url")
          Future.successful((None, RtiStatus(res.status, res.body)))
        }
        case Status.INTERNAL_SERVER_ERROR => {
          Logger.warn(s"RTIAPI - Internal Server error returned from RTI HODS for $reqNino for url $url")
          Future.successful((None, RtiStatus(res.status, res.body)))
        }
        case _ => {
          Logger.warn(s"RTIAPI - An error returned from RTI HODS for $reqNino for url $url")
          Future.successful((None, RtiStatus(res.status, res.body)))
        }
      }
    }
  }

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
          Logger.warn(s"DESAPI - No DATA Found error returned from DES HODS for $api and url $url")
          metrics.incrementFailedCounter(api)
          Future.failed(new NotFoundException(httpResponse.body))
        }

        case Status.INTERNAL_SERVER_ERROR => {
          Logger.warn(s"DESAPI - Internal Server error returned from DES HODS for $api and url $url")
          metrics.incrementFailedCounter(api)
          Future.failed(new InternalServerException(httpResponse.body))
        }

        case Status.BAD_REQUEST => {
          Logger.warn(s"DESAPI - Bad request exception returned from DES HODS for $api and url $url")
          metrics.incrementFailedCounter(api)
          Future.failed(new BadRequestException(httpResponse.body))
        }

        case _ => {
          Logger.warn(s"DESAPI - A Server error returned from DES HODS for $api and url $url")
          metrics.incrementFailedCounter(api)
          Future.failed(new HttpException(httpResponse.body, httpResponse.status))
        }
      }
    }
  }

  def postToDes[A](url: String, api: APITypes, postData: A, headers: Seq[(String, String)])(
    implicit hc: HeaderCarrier,
    writes: Writes[A]): Future[HttpResponse] = {
    val timerContext = metrics.startTimer(api)
    val futureResponse = httpClient.POST(url = url, body = postData, headers = headers)
    futureResponse.flatMap { httpResponse =>
      timerContext.stop()
      httpResponse.status match {
        case (Status.OK | Status.NO_CONTENT | Status.ACCEPTED) => {
          metrics.incrementSuccessCounter(api)
          Future.successful(httpResponse)
        }
        case _ => {
          Logger.warn(
            s"DESAPI - A server error returned from DES HODS in postToDes with status ${httpResponse.status}, " +
              s"url $url")
          metrics.incrementFailedCounter(api)
          Future.failed(new HttpException(httpResponse.body, httpResponse.status))
        }
      }
    }
  }
}

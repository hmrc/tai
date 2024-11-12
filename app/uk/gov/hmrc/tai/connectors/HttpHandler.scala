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
import com.google.inject.{Inject, Singleton}
import play.api.Logging
import play.api.http.Status
import play.api.http.Status.{ACCEPTED, CREATED, NO_CONTENT, OK}
import play.api.libs.json.{JsValue, Writes}
import play.api.libs.ws.WSRequest
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.HttpReadsInstances.readEitherOf
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.HodResponse
import uk.gov.hmrc.tai.model.enums.APITypes._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

@Singleton
class HttpHandler @Inject() (metrics: Metrics, httpClient: HttpClient, httpClientV2: HttpClientV2)(implicit
  ec: ExecutionContext
) extends Logging {

  def getFromApiAsEitherT(url: String, headers: Seq[(String, String)])(implicit
    hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, HodResponse] =
    EitherT(httpClient.GET[Either[UpstreamErrorResponse, HttpResponse]](url = url, headers = headers))
      .map { response =>
        HodResponse(response.json, response.header("ETag").map(_.toInt))
      }

  def getFromApi(url: String, api: APITypes, headers: Seq[(String, String)], timeoutInMilliseconds: Option[Int] = None)(
    implicit hc: HeaderCarrier
  ): Future[JsValue] = {

    val timerContext = metrics.startTimer(api)

    implicit val responseHandler: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
      override def read(method: String, url: String, response: HttpResponse): HttpResponse =
        response.status match {
          case Status.OK => response
          case Status.NOT_FOUND =>
            logger.warn(s"HttpHandler - No DATA Found error returned from $api for url $url")
            throw new NotFoundException(response.body)
          case Status.INTERNAL_SERVER_ERROR =>
            logger.warn(s"HttpHandler - Internal Server error returned from $api for url $url")
            throw new InternalServerException(response.body)
          case Status.BAD_REQUEST =>
            logger.warn(s"HttpHandler - Bad request exception returned from $api for url $url")
            throw new BadRequestException(response.body)
          case Status.LOCKED =>
            logger.warn(s"HttpHandler - Locked response returned from $api for url $url")
            throw new LockedException(response.body)
          case _ =>
            logger.warn(s"HttpHandler - A Server error returned from $api for url $url")
            throw new HttpException(response.body, response.status)
        }
    }

    (for {
      response <- httpClientV2
                    .get(url = url"$url")(hc.withExtraHeaders(headers: _*))
                    .transform { response: WSRequest =>
                      timeoutInMilliseconds.fold(response) { timeoutInMilliseconds =>
                        response.withRequestTimeout(timeoutInMilliseconds.milliseconds)
                      }
                    }
                    .execute[HttpResponse](responseHandler, ec)
      _ <- Future.successful(timerContext.stop())
      _ <- Future.successful(metrics.incrementSuccessCounter(api))
    } yield response.json) recover {
      case lockedException: LockedException =>
        timerContext.stop()
        metrics.incrementSuccessCounter(api)
        throw lockedException
      case ex: Exception =>
        timerContext.stop()
        metrics.incrementFailedCounter(api)
        throw ex
    }

  }

  def postToApi[I](url: String, data: I, api: APITypes, headers: Seq[(String, String)])(implicit
    hc: HeaderCarrier,
    writes: Writes[I]
  ): Future[HttpResponse] = {

    val rawHttpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
      override def read(method: String, url: String, response: HttpResponse): HttpResponse = response
    }

    httpClient.POST[I, HttpResponse](url, data, headers)(writes, rawHttpReads, hc, ec) map { httpResponse =>
      httpResponse status match {
        case OK | CREATED | ACCEPTED | NO_CONTENT =>
          metrics.incrementSuccessCounter(api)
          httpResponse
        case _ =>
          logger.warn(
            s"HttpHandler - Error received with status: ${httpResponse.status} for url $url with message body ${httpResponse.body}"
          )
          metrics.incrementFailedCounter(api)
          throw new HttpException(httpResponse.body, httpResponse.status)
      }
    }
  }

  def putToApi(
    url: String,
    data: JsValue,
    api: APITypes,
    headers: Seq[(String, String)],
    timeoutInMilliseconds: Option[Int] = None
  )(implicit
    hc: HeaderCarrier
  ): Future[HttpResponse] = {
    val timerContext = metrics.startTimer(api)

    implicit val responseHandler: HttpReads[HttpResponse] = (_: String, url: String, httpResponse: HttpResponse) =>
      httpResponse.status match {
        case OK | CREATED | ACCEPTED | NO_CONTENT =>
          metrics.incrementSuccessCounter(api)
          httpResponse
        case _ =>
          logger.warn(
            s"HttpHandler - Error received with status: ${httpResponse.status} for url $url with message body ${httpResponse.body}"
          )
          metrics.incrementFailedCounter(api)
          throw new HttpException(httpResponse.body, httpResponse.status)
      }

    (for {
      response <- httpClientV2
                    .put(url = url"$url")(hc.withExtraHeaders(headers: _*))
                    .withBody(data)
                    .transform { response: WSRequest =>
                      timeoutInMilliseconds.fold(response) { timeoutInMilliseconds =>
                        response.withRequestTimeout(timeoutInMilliseconds.milliseconds)
                      }
                    }
                    .execute[HttpResponse](responseHandler, ec)
      _ <- Future.successful(timerContext.stop())
      _ <- Future.successful(metrics.incrementSuccessCounter(api))
    } yield response) recover {
      case lockedException: LockedException =>
        timerContext.stop()
        metrics.incrementSuccessCounter(api)
        throw lockedException
      case ex: Exception =>
        timerContext.stop()
        metrics.incrementFailedCounter(api)
        throw ex
    }

  }

  def putToApiHttpClientV1[I](url: String, data: I, api: APITypes, headers: Seq[(String, String)])(implicit
    hc: HeaderCarrier,
    writes: Writes[I]
  ): Future[HttpResponse] = {

    val rawHttpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
      override def read(method: String, url: String, response: HttpResponse): HttpResponse = response
    }

    httpClient.PUT[I, HttpResponse](url, data, headers)(writes, rawHttpReads, hc, ec) map { httpResponse =>
      httpResponse status match {
        case OK | CREATED | ACCEPTED | NO_CONTENT =>
          metrics.incrementSuccessCounter(api)
          httpResponse
        case _ =>
          logger.warn(
            s"HttpHandler - Error received with status: ${httpResponse.status} for url $url with message body ${httpResponse.body}"
          )
          metrics.incrementFailedCounter(api)
          throw new HttpException(httpResponse.body, httpResponse.status)
      }
    }
  }
}

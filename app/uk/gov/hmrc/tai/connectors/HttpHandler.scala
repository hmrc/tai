/*
 * Copyright 2020 HM Revenue & Customs
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
import com.typesafe.scalalogging.LazyLogging
import play.api.http.Status
import play.api.http.Status._
import play.api.libs.json.{JsValue, Writes}
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.enums.APITypes._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

@Singleton
class HttpHandler @Inject()(metrics: Metrics, httpClient: HttpClient)(implicit ec: ExecutionContext)
    extends LazyLogging {

  def getFromApi(url: String, api: APITypes)(implicit hc: HeaderCarrier): Future[JsValue] = {

    val timerContext = metrics.startTimer(api)

    implicit val responseHandler = new HttpReads[HttpResponse] {
      override def read(method: String, url: String, response: HttpResponse): HttpResponse =
        response.status match {
          case Status.OK =>
            Try(response) match {
              case Success(data) => data
              case Failure(e)    => throw new RuntimeException("Unable to parse response")
            }
          case Status.NOT_FOUND => {
            logger.warn(s"HttpHandler - No DATA Found error returned from $api for url $url")
            throw new NotFoundException(response.body)
          }
          case Status.INTERNAL_SERVER_ERROR => {
            logger.warn(s"HttpHandler - Internal Server error returned from $api for url $url")
            throw new InternalServerException(response.body)
          }
          case Status.BAD_REQUEST => {
            logger.warn(s"HttpHandler - Bad request exception returned from $api for url $url")
            throw new BadRequestException(response.body)
          }
          case Status.LOCKED => {
            logger.warn(s"HttpHandler - Locked response returned from $api for url $url")
            throw new LockedException(response.body)
          }
          case _ => {
            logger.warn(s"HttpHandler - A Server error returned from $api for url $url")
            throw new HttpException(response.body, response.status)
          }
        }
    }

    (for {
      response <- httpClient.GET[HttpResponse](url)(responseHandler, hc, ec)
      _        <- Future.successful(timerContext.stop())
      _        <- Future.successful(metrics.incrementSuccessCounter(api))
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

  def getFromApiV2(url: String, api: APITypes)(implicit hc: HeaderCarrier): Future[HttpResponse] = {

    val timerContext = metrics.startTimer(api)
    def loggerMessage(code: Int): String = s"HttpHandler received $code from $url"

    httpClient.GET[HttpResponse](url) map { response =>
      response.status match {
        case OK =>
          timerContext.stop()
          metrics.incrementSuccessCounter(api)
          response
        case LOCKED =>
          timerContext.stop()
          metrics.incrementSuccessCounter(api)
          logger.warn(loggerMessage(LOCKED))
          HttpResponse(LOCKED, response.body)
        case code =>
          timerContext.stop()
          metrics.incrementFailedCounter(api)
          logger.error(loggerMessage(code))
          HttpResponse(code, response.body)
      }
    } recover {
      case e =>
        val errorMessage = s"Exception in HttpHandler: $e"
        timerContext.stop()
        metrics.incrementFailedCounter(api)
        logger.error(errorMessage, e)
        HttpResponse(INTERNAL_SERVER_ERROR, errorMessage)
    }
  }

  def postToApi[I](url: String, data: I, api: APITypes)(
    implicit hc: HeaderCarrier,
    writes: Writes[I]): Future[HttpResponse] = {

    val rawHttpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
      override def read(method: String, url: String, response: HttpResponse): HttpResponse = response
    }

    httpClient.POST[I, HttpResponse](url, data)(writes, rawHttpReads, hc, ec) map { httpResponse =>
      httpResponse status match {
        case OK | CREATED | ACCEPTED | NO_CONTENT => {
          metrics.incrementSuccessCounter(api)
          httpResponse
        }
        case _ => {
          logger.warn(
            s"HttpHandler - Error received with status: ${httpResponse.status} for url $url with message body ${httpResponse.body}")
          metrics.incrementFailedCounter(api)
          throw new HttpException(httpResponse.body, httpResponse.status)
        }
      }
    }
  }
}

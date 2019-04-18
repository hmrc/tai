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

import com.codahale.metrics.Timer
import com.google.inject.{Inject, Singleton}
import play.Logger
import play.api.http.Status
import play.api.http.Status.{ACCEPTED, CREATED, NO_CONTENT, OK}
import play.api.libs.json.{JsValue, Writes}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.enums.APITypes._

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

@Singleton
class HttpHandler @Inject()(metrics: Metrics, httpClient: HttpClient) {

  def getFromApi(url: String, api: APITypes)(implicit hc: HeaderCarrier): Future[JsValue] = {

    val timerContext = metrics.startTimer(api)

    implicit val responseHandler = new HttpReads[HttpResponse] {
      override def read(method: String, url: String, response: HttpResponse): HttpResponse = {
        response.status match {
          case Status.OK => Try(response) match {
            case Success(data) => {
              timerContext.stop()
              metrics.incrementSuccessCounter(api)
              data
            }
            case Failure(e) => {
              handleFailure(timerContext, api)
              throw new RuntimeException("Unable to parse response")
            }
          }
          case Status.NOT_FOUND => {
            Logger.warn(s"HttpHandler - No DATA Found error returned from $api")
            handleFailure(timerContext, api)
            throw new NotFoundException(response.body)
          }
          case Status.INTERNAL_SERVER_ERROR => {
            Logger.warn(s"HttpHandler - Internal Server error returned from $api")
            handleFailure(timerContext, api)
            throw new InternalServerException(response.body)
          }
          case Status.BAD_REQUEST => {
            Logger.warn(s"HttpHandler - Bad request exception returned from $api")
            handleFailure(timerContext, api)
            throw new BadRequestException(response.body)
          }
          case Status.LOCKED => {
            Logger.warn(s"HttpHandler - Locked response returned from $api")
            timerContext.stop()
            metrics.incrementSuccessCounter(api)
            throw new LockedException(response.body)
          }
          case _ => {
            Logger.warn(s"HttpHandler - A Server error returned from $api")
            handleFailure(timerContext, api)
            throw new HttpException(response.body, response.status)
          }
        }
      }
    }

    httpClient.GET[HttpResponse](url).map(_.json)
  }

  def handleFailure(timerContext:Timer.Context, api:APITypes) = {
    timerContext.stop()
    metrics.incrementFailedCounter(api)
  }

  def postToApi[I](url: String, data: I, api: APITypes)(implicit hc: HeaderCarrier, writes: Writes[I]): Future[HttpResponse] = {

    val rawHttpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
      override def read(method: String, url: String, response: HttpResponse): HttpResponse = response
    }

    httpClient.POST[I, HttpResponse](url, data)(writes, rawHttpReads, hc, fromLoggingDetails) map { httpResponse =>
      httpResponse status match {
        case OK | CREATED | ACCEPTED | NO_CONTENT => {
          metrics.incrementSuccessCounter(api)
          httpResponse
        }
        case _ => {
          Logger.warn(s"HttpHandler - Error received with status: ${httpResponse.status} and body: ${httpResponse.body}")
          metrics.incrementFailedCounter(api)
          throw new HttpException(httpResponse.body, httpResponse.status)
        }
      }
    }
  }
}
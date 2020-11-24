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
import play.api.http.Status._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.TaiConstants

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BbsiConnector @Inject()(metrics: Metrics, http: HttpClient, urls: BbsiUrls, config: DesConfig)(
  implicit ec: ExecutionContext)
    extends LazyLogging {

  private val api = APITypes.BbsiAPI

  private def createHeader: HeaderCarrier =
    HeaderCarrier(
      extraHeaders = Seq(
        "Environment"   -> config.environment,
        "Authorization" -> s"Bearer ${config.authorization}",
        "Content-Type"  -> TaiConstants.contentType))

  def bankAccounts(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[HttpResponse] = {

    implicit val hc: HeaderCarrier = createHeader
    val timerContext = metrics.startTimer(api)

    def stopTimerAndFailMetric(): Unit = {
      timerContext.stop()
      metrics.incrementFailedCounter(api)
    }

    http.GET[HttpResponse](urls.bbsiUrl(nino, taxYear)) map { response =>
      response.status match {
        case OK =>
          timerContext.stop()
          metrics.incrementSuccessCounter(api)
          response
        case _ =>
          stopTimerAndFailMetric()
          logger.error(response.body)
          response
      }
    } recover {
      case e: HttpException =>
        stopTimerAndFailMetric()
        logger.error(e.message, e)
        HttpResponse(e.responseCode, e.message)
      case e: UpstreamErrorResponse =>
        stopTimerAndFailMetric()
        logger.error(e.message, e)
        HttpResponse(e.statusCode, e.message)
      case e =>
        val errorMessage = s"Exception in HttpHandler: $e"
        stopTimerAndFailMetric()
        logger.error(errorMessage, e)
        HttpResponse(INTERNAL_SERVER_ERROR, errorMessage)
    }
  }
}

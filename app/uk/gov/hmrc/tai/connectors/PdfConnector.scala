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

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.Status
import play.api.libs.ws.WSBodyWritables.writeableOf_urlEncodedForm
import play.api.libs.ws.WSClient
import uk.gov.hmrc.http.HttpException
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.enums.APITypes

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PdfConnector @Inject() (metrics: Metrics, wsClient: WSClient, urls: PdfUrls)(implicit ec: ExecutionContext) {

  private val logger: Logger = Logger(getClass.getName)

  def generatePdf(html: String): Future[Array[Byte]] = {
    val timerContext = metrics.startTimer(APITypes.PdfServiceAPI)

    val result = wsClient.url(urls.generatePdfUrl).post(Map("html" -> Seq(html)))

    result.map { response =>
      timerContext.stop()
      response.status match {
        case Status.OK =>
          metrics.incrementSuccessCounter(APITypes.PdfServiceAPI)
          response.bodyAsBytes.toArray
        case _ =>
          logger.warn(s"PdfConnector - A Server error was received from ${APITypes.PdfServiceAPI}")
          metrics.incrementFailedCounter(APITypes.PdfServiceAPI)

          throw new HttpException(response.body, response.status)
      }
    }
  }
}

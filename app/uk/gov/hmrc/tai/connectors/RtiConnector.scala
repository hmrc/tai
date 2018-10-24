/*
 * Copyright 2018 HM Revenue & Customs
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
import play.Logger
import play.api.http.Status
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.JsValue
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.rti._
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.Future

@Singleton
class RtiConnector @Inject()(httpClient: HttpClient,
                             metrics: Metrics,
                             auditor: Auditor,
                             rtiConfig: DesConfig,
                             urls: RtiUrls) extends BaseConnector(auditor, metrics, httpClient) {


  override val originatorId = rtiConfig.originatorId

  def withoutSuffix(nino: Nino): String = {
    val BASIC_NINO_LENGTH = 8
    nino.value.take(BASIC_NINO_LENGTH)
  }

  def createHeader: HeaderCarrier = HeaderCarrier(extraHeaders =
    Seq("Environment" -> rtiConfig.environment,
      "Authorization" -> rtiConfig.authorization,
      "Gov-Uk-Originator-Id" -> originatorId))

  def getRTI(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[(Option[RtiData], RtiStatus)] = {
    implicit val hc: HeaderCarrier = createHeader
    val ninoWithoutSuffix = withoutSuffix(nino)
    getFromRTIWithStatus[RtiData](
      urls.paymentsForYearUrl(ninoWithoutSuffix, taxYear),
      APITypes.RTIAPI,
      ninoWithoutSuffix
    )(hc, formatRtiData)
  }

  def getRTIDetails(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue] = {
    implicit val hc: HeaderCarrier = createHeader
    val timerContext = metrics.startTimer(APITypes.RTIAPI)
    val ninoWithoutSuffix = withoutSuffix(nino)
    val futureResponse = httpClient.GET[HttpResponse](urls.paymentsForYearUrl(ninoWithoutSuffix, taxYear))
    futureResponse.flatMap {
      res =>
        timerContext.stop()
        res.status match {
          case Status.OK =>
            metrics.incrementSuccessCounter(APITypes.RTIAPI)
            val rtiData = res.json
            Future.successful(rtiData)
          case _ =>
            Logger.warn(s"RTIAPI - ${res.status} error returned from RTI HODS for $ninoWithoutSuffix")
            Future.failed(new HttpException(res.body, res.status))
        }
    }
  }
}

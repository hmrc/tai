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
import play.Logger
import play.api.http.Status.OK
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.{DesConfig, RtiToggleConfig}
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.formatters.EmploymentHodFormatters
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.rti._
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.Future

@Singleton
class RtiConnector @Inject()(
  httpClient: HttpClient,
  metrics: Metrics,
  auditor: Auditor,
  rtiConfig: DesConfig,
  urls: RtiUrls,
  rtiToggle: RtiToggleConfig)
    extends BaseConnector(auditor, metrics, httpClient) {

  override val originatorId = rtiConfig.originatorId

  def withoutSuffix(nino: Nino): String = {
    val BASIC_NINO_LENGTH = 8
    nino.value.take(BASIC_NINO_LENGTH)
  }

  def createHeader: HeaderCarrier =
    HeaderCarrier(
      extraHeaders = Seq(
        "Environment"          -> rtiConfig.environment,
        "Authorization"        -> rtiConfig.authorization,
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

  //TODO logger error and raise ticket
  def getPaymentsForYear(nino: Nino, taxYear: TaxYear)(
    implicit hc: HeaderCarrier): Future[Either[UnavailableRealTimeStatus, Seq[AnnualAccount]]] = {
    implicit val hc: HeaderCarrier = createHeader

    if (rtiToggle.rtiEnabled) {
      val timerContext = metrics.startTimer(APITypes.RTIAPI)
      val ninoWithoutSuffix = withoutSuffix(nino)
      val futureResponse = httpClient.GET[HttpResponse](urls.paymentsForYearUrl(ninoWithoutSuffix, taxYear))
      futureResponse.flatMap { res =>
        timerContext.stop()
        res.status match {
          case OK =>
            metrics.incrementSuccessCounter(APITypes.RTIAPI)
            val rtiData = res.json
            val annualAccounts = rtiData.as[Seq[AnnualAccount]](EmploymentHodFormatters.annualAccountHodReads)
            Future.successful(Right(annualAccounts))
          case _ =>
            Logger.warn(s"RTIAPI - ${res.status} error returned from RTI HODS for $ninoWithoutSuffix")

            val rtiStatus = res.status match {
              case 404 => Unavailable
              case _   => TemporarilyUnavailable
            }

            Future.successful(Left(rtiStatus))
        }
      }
    } else {
      Future.successful(Left(TemporarilyUnavailable))
    }
  }
}

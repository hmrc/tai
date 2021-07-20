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

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.Status._
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

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class RtiConnector @Inject()(
  httpClient: HttpClient,
  metrics: Metrics,
  auditor: Auditor,
  rtiConfig: DesConfig,
  urls: RtiUrls,
  rtiToggle: RtiToggleConfig)(implicit ec: ExecutionContext)
    extends BaseConnector(auditor, metrics, httpClient) {

  override val originatorId = rtiConfig.originatorId
  val logger = Logger(this.getClass)

  def withoutSuffix(nino: Nino): String = {
    val BASIC_NINO_LENGTH = 8
    nino.value.take(BASIC_NINO_LENGTH)
  }

  private def createHeader(implicit hc: HeaderCarrier): Seq[(String, String)] =
      Seq(
        "Environment"          -> rtiConfig.environment,
        "Authorization"        -> rtiConfig.authorization,
        "Gov-Uk-Originator-Id" -> originatorId,
        HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
        HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
        "CorrelationId"        -> UUID.randomUUID().toString
      )

  def getRTI(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[(Option[RtiData], RtiStatus)] = {
    val ninoWithoutSuffix = withoutSuffix(nino)
    getFromRTIWithStatus[RtiData](
      url = urls.paymentsForYearUrl(ninoWithoutSuffix, taxYear),
      api = APITypes.RTIAPI,
      reqNino = ninoWithoutSuffix,
      headers = createHeader
    )
  }

  def getPaymentsForYear(nino: Nino, taxYear: TaxYear)(
    implicit hc: HeaderCarrier): Future[Either[RtiPaymentsForYearError, Seq[AnnualAccount]]] =
    if (rtiToggle.rtiEnabled) {
      val NGINX_TIMEOUT = 499
      val timerContext = metrics.startTimer(APITypes.RTIAPI)
      val ninoWithoutSuffix = withoutSuffix(nino)
      val futureResponse = httpClient.GET[HttpResponse](url = urls.paymentsForYearUrl(ninoWithoutSuffix, taxYear), headers = createHeader)
      futureResponse map { res =>
        timerContext.stop()
        res.status match {
          case OK => {
            metrics.incrementSuccessCounter(APITypes.RTIAPI)
            val rtiData = res.json
            val annualAccounts = rtiData.as[Seq[AnnualAccount]](EmploymentHodFormatters.annualAccountHodReads)
            Right(annualAccounts)
          }
          case NOT_FOUND => {
            metrics.incrementSuccessCounter(APITypes.RTIAPI)
            Left(ResourceNotFoundError)
          }
          case BAD_REQUEST => {
            metrics.incrementFailedCounter(APITypes.RTIAPI)
            logger.error(s"RTIAPI - Bad request error received: ${res.body}")
            Left(BadRequestError)
          }
          case SERVICE_UNAVAILABLE => {
            metrics.incrementFailedCounter(APITypes.RTIAPI)
            logger.warn(s"RTIAPI - Service unavailable error received")
            Left(ServiceUnavailableError)
          }
          case INTERNAL_SERVER_ERROR => {
            metrics.incrementFailedCounter(APITypes.RTIAPI)
            logger.error(s"RTIAPI - Internal Server error received: ${res.body}")
            Left(ServerError)
          }
          case BAD_GATEWAY => {
            metrics.incrementFailedCounter(APITypes.RTIAPI)
            Left(BadGatewayError)
          }
          case GATEWAY_TIMEOUT | NGINX_TIMEOUT => {
            metrics.incrementFailedCounter(APITypes.RTIAPI)
            Left(TimeoutError)
          }
          case _ => {
            logger.error(s"RTIAPI - ${res.status} error returned from RTI HODS")
            metrics.incrementFailedCounter(APITypes.RTIAPI)
            Left(UnhandledStatusError)
          }
        }
      } recover {
        case _: GatewayTimeoutException => {
          metrics.incrementFailedCounter(APITypes.RTIAPI)
          timerContext.stop()
          Left(TimeoutError)
        }
        case _: BadGatewayException => {
          metrics.incrementFailedCounter(APITypes.RTIAPI)
          timerContext.stop()
          Left(BadGatewayError)
        }
        case NonFatal(e) => {
          metrics.incrementFailedCounter(APITypes.RTIAPI)
          timerContext.stop()
          throw e
        }
      }
    } else {
      Future.successful(Left(ServiceUnavailableError))
    }
}

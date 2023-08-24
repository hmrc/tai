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
import cats.implicits._
import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.Format
import play.api.mvc.Request
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{BadGatewayException, GatewayTimeoutException, HeaderCarrier, HeaderNames, HttpClient, HttpResponse}
import uk.gov.hmrc.mongo.cache.DataKey
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.config.{DesConfig, MongoConfig}
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.admin.RtiCallToggle
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.formatters.{EmploymentHodFormatters, EmploymentMongoFormatters}
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.cache.TaiSessionCacheRepository
import uk.gov.hmrc.tai.service.LockService

import java.util.UUID
import javax.inject.Named
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait RtiConnector extends RawResponseReads {
  def getPaymentsForYear(nino: Nino, taxYear: TaxYear)(implicit
                                                       hc: HeaderCarrier,
                                                       request: Request[_]
  ): EitherT[Future, RtiPaymentsForYearError, Seq[AnnualAccount]]

  def withoutSuffix(nino: Nino): String = {
    val BASIC_NINO_LENGTH = 8
    nino.value.take(BASIC_NINO_LENGTH)
  }

  def createHeader(rtiConfig: DesConfig)(implicit hc: HeaderCarrier): Seq[(String, String)] =
    Seq(
      "Environment"          -> rtiConfig.environment,
      "Authorization"        -> rtiConfig.authorization,
      "Gov-Uk-Originator-Id" -> rtiConfig.originatorId,
      HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
      "CorrelationId"        -> UUID.randomUUID().toString
    )
}

@Singleton
class CachingRtiConnector @Inject() (
                                      @Named("default") underlying: RtiConnector,
                                      sessionCacheRepository: TaiSessionCacheRepository,
                                      lockService: LockService,
                                      appConfig: MongoConfig
                                                         )(implicit ec: ExecutionContext)
  extends RtiConnector with EmploymentMongoFormatters {

  private def cache[L, A: Format](key: String)
                                 (f: => EitherT[Future, L, A])
                                 (implicit hc: HeaderCarrier): EitherT[Future, L, A] = {

    def fetchAndCache: EitherT[Future, L, A] =
      for {
        result <- f
        _      <- EitherT[Future, L, (String, String)](
            sessionCacheRepository
              .putSession[A](DataKey[A](key), result)
              .map(Right(_))
          )
      } yield result

    def recursiveReadAndUpdate(count: Int = 0): EitherT[Future, L, A] = {
      lockService.takeLock[L](key).flatMap {
        case true =>
          EitherT(
          sessionCacheRepository
            .getFromSession[A](DataKey[A](key))
            .map {
              case None =>
                fetchAndCache
              case Some(value) =>
                EitherT.rightT[Future, L](value)
            }.map(_.value).flatten) recoverWith {
            case NonFatal(_) =>
          fetchAndCache
        }
        case false =>
          if (count < appConfig.mongoLockTTL / 0.5 ) {
            Thread.sleep(500)
            recursiveReadAndUpdate(count + 1)
          } else {
            throw new RuntimeException("Oops, cannot acquire lock")
          }
      }
    }

    EitherT(recursiveReadAndUpdate().transform { result =>
      lockService.releaseLock(key)
      result
    }.value.recover {
      case ex =>
        lockService.releaseLock(key)
        throw ex
    })
  }

  def getPaymentsForYear(nino: Nino, taxYear: TaxYear)(implicit
                                    hc: HeaderCarrier,
                                    request: Request[_]
                                   ): EitherT[Future, RtiPaymentsForYearError, Seq[AnnualAccount]] =
    cache(s"getPaymentsForYear${taxYear.year}") {
      underlying.getPaymentsForYear(nino: Nino, taxYear: TaxYear)
    }
}


@Singleton
class DefaultRtiConnector @Inject()(
  httpClient: HttpClient,
  metrics: Metrics,
  rtiConfig: DesConfig,
  urls: RtiUrls,
  featureFlagService: FeatureFlagService)(implicit ec: ExecutionContext) extends RtiConnector {

  val logger = Logger(this.getClass)

  def getPaymentsForYear(nino: Nino, taxYear: TaxYear)(
    implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, RtiPaymentsForYearError, Seq[AnnualAccount]] = {

    EitherT[Future, RtiPaymentsForYearError, Seq[AnnualAccount]](
      featureFlagService.get(RtiCallToggle).flatMap { toggle =>
        if (toggle.isEnabled && taxYear.year < TaxYear().next.year) {
            logger.info(s"RTIAPI - call for the year: $taxYear}")
            val NGINX_TIMEOUT = 499
            val timerContext = metrics.startTimer(APITypes.RTIAPI)
            val ninoWithoutSuffix = withoutSuffix(nino)
            val futureResponse =
              httpClient.GET[HttpResponse](url = urls.paymentsForYearUrl(ninoWithoutSuffix, taxYear), headers = createHeader(rtiConfig))

            futureResponse map { res =>
              timerContext.stop()
              res.status match {
                case OK =>
                  metrics.incrementSuccessCounter(APITypes.RTIAPI)
                  val rtiData = res.json
                  val annualAccounts = rtiData.as[Seq[AnnualAccount]](EmploymentHodFormatters.annualAccountHodReads)
                  Right(annualAccounts)
                case NOT_FOUND =>
                  metrics.incrementSuccessCounter(APITypes.RTIAPI)
                  Right(Seq.empty)
                case BAD_REQUEST =>
                  metrics.incrementFailedCounter(APITypes.RTIAPI)
                  logger.error(s"RTIAPI - Bad request error received: ${res.body}")
                  Left(BadRequestError)
                case SERVICE_UNAVAILABLE =>
                  metrics.incrementFailedCounter(APITypes.RTIAPI)
                  logger.warn(s"RTIAPI - Service unavailable error received")
                  Left(ServiceUnavailableError)
                case INTERNAL_SERVER_ERROR =>
                  metrics.incrementFailedCounter(APITypes.RTIAPI)
                  logger.error(s"RTIAPI - Internal Server error received: ${res.body}")
                  Left(ServerError)
                case BAD_GATEWAY =>
                  metrics.incrementFailedCounter(APITypes.RTIAPI)
                  Left(BadGatewayError)
                case GATEWAY_TIMEOUT | NGINX_TIMEOUT =>
                  metrics.incrementFailedCounter(APITypes.RTIAPI)
                  Left(TimeoutError)
                case _ =>
                  logger.error(s"RTIAPI - ${res.status} error returned from RTI HODS")
                  metrics.incrementFailedCounter(APITypes.RTIAPI)
                  Left(UnhandledStatusError)
              }
            } recover {
              case _: GatewayTimeoutException =>
                metrics.incrementFailedCounter(APITypes.RTIAPI)
                timerContext.stop()
                Left(TimeoutError)
              case _: BadGatewayException =>
                metrics.incrementFailedCounter(APITypes.RTIAPI)
                timerContext.stop()
                Left(BadGatewayError)
              case NonFatal(e) =>
                metrics.incrementFailedCounter(APITypes.RTIAPI)
                timerContext.stop()
                logger.error(e.getMessage, e)
                throw e
            }
          } else {
          logger.info(s"RTIAPI - SKIP RTI call for year: $taxYear}")
          Future.successful(Left(ServiceUnavailableError))
        }
      }
    )
  }
}

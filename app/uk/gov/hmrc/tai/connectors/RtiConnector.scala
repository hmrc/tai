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
import cats.effect.unsafe.implicits.global
import cats.effect.IO
import cats.implicits._
import com.google.inject.{Inject, Singleton}
import play.api.{Logger, Logging}
import play.api.http.Status._
import play.api.libs.json.Format
import play.api.mvc.Request
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReadsInstances.readEitherOf
import uk.gov.hmrc.http.{BadGatewayException, GatewayTimeoutException, HeaderCarrier, HeaderNames, HttpClient, HttpException, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.mongo.cache.DataKey
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.config.{DesConfig, RtiConfig}
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.admin.RtiCallToggle
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.formatters.{EmploymentHodFormatters, EmploymentMongoFormatters}
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.cache.TaiSessionCacheRepository
import uk.gov.hmrc.tai.service.LockService
import uk.gov.hmrc.tai.util.IORetryExtension.Retryable
import uk.gov.hmrc.tai.util.LockedException

import java.util.UUID
import javax.inject.Named
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait RtiConnector extends RawResponseReads {
  def getPaymentsForYearAsEitherT(nino: Nino, taxYear: TaxYear)(implicit
                                                       hc: HeaderCarrier,
                                                       request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Seq[AnnualAccount]]

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
                                      appConfig: RtiConfig
                                                         )(implicit ec: ExecutionContext)
  extends RtiConnector with EmploymentMongoFormatters with Logging {

  private def cache[L, A: Format](key: String)
                                 (f: => EitherT[Future, L, A])
                                 (implicit hc: HeaderCarrier): EitherT[Future, L, A] = {

    def fetchAndCache: IO[Either[L, A]] =
      IO.fromFuture(IO((for {
        result <- f
        _      <- EitherT[Future, L, (String, String)](
            sessionCacheRepository
              .putSession[A](DataKey[A](key), result)
              .map(Right(_))
          )
      } yield result).value))

    def readAndUpdate: IO[Either[L, A]] = {
      IO.fromFuture(IO(lockService.takeLock[L](key).value)).flatMap {
        case Right(true) =>
          IO.fromFuture(IO(sessionCacheRepository
              .getFromSession[A](DataKey[A](key))))
              .flatMap {
                case None =>
                  fetchAndCache
                case Some(value) =>
                  IO(Right(value): Either[L, A])
              }
        case Right(false) =>
          throw new LockedException(s"Lock for $key could not be acquired")
        case Left(error) => IO(Left(error))
      }
    }

    EitherT(readAndUpdate.simpleRetry(appConfig.hodRetryMaximum, appConfig.hodRetryDelayInMillis.millis).unsafeToFuture().flatMap { result =>
      lockService.releaseLock(key).map { _ =>
        result
      }.recover {
        case NonFatal(ex) =>
          logger.error(ex.getMessage, ex)
          result
      }
    }.recoverWith {
      case NonFatal(ex) =>
        lockService.releaseLock(key).map { _ =>
          throw ex
        }
    })
  }

  def getPaymentsForYearAsEitherT(nino: Nino, taxYear: TaxYear)(implicit
                                                       hc: HeaderCarrier,
                                                       request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Seq[AnnualAccount]] =
    cache(s"getPaymentsForYear-$nino-${taxYear.year}") {
      underlying.getPaymentsForYearAsEitherT(nino: Nino, taxYear: TaxYear)
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
    implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, RtiPaymentsForYearError, Seq[AnnualAccount]] =
    EitherT[Future, RtiPaymentsForYearError, Seq[AnnualAccount]](
      getPaymentsForYearHandler(nino, taxYear)
    )

  def getPaymentsForYearAsEitherT(nino: Nino, taxYear: TaxYear)(
    implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, UpstreamErrorResponse, Seq[AnnualAccount]] =
      getPaymentsForYearHandlerAsEitherT(nino, taxYear)

  private def getPaymentsForYearHandler(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[Either[RtiPaymentsForYearError, Seq[AnnualAccount]]] =
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

  private def getPaymentsForYearHandlerAsEitherT(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, Seq[AnnualAccount]] =
    featureFlagService.getAsEitherT(RtiCallToggle).flatMap { toggle =>
      if (toggle.isEnabled && taxYear.year < TaxYear().next.year) {
        logger.info(s"RTIAPI - call for the year: $taxYear}")
        val ninoWithoutSuffix = withoutSuffix(nino)
        val futureResponse =
          httpClient.GET[Either[UpstreamErrorResponse, HttpResponse]](url = urls.paymentsForYearUrl(ninoWithoutSuffix, taxYear), headers = createHeader(rtiConfig))
        EitherT(futureResponse.map {
            case Right(httpResponse) =>
              Right(httpResponse.json.as[Seq[AnnualAccount]](EmploymentHodFormatters.annualAccountHodReads))
            case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _)) =>
              Right(Seq.empty)
            case Left(error) =>
              logger.error(s"RTIAPI - ${error.statusCode} error returned from RTI HODS with message ${error.getMessage()}")
            Left(error)
          }.recover {
          case error: HttpException =>
            Left(UpstreamErrorResponse(error.message, BAD_GATEWAY))
        })
      } else {
        logger.info(s"RTIAPI - SKIP RTI call for year: $taxYear}")
        EitherT.leftT(UpstreamErrorResponse(s"RTIAPI - SKIP RTI call for year: $taxYear}", 444))
      }
    }
}

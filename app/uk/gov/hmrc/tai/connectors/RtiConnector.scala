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
import cats.implicits.*
import com.google.inject.{Inject, Singleton}
import play.api.http.Status.*
import play.api.mvc.Request
import play.api.{Logger, Logging}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits.{readEitherOf, readRaw}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HeaderNames, HttpException, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.model.admin.RtiCallToggle
import uk.gov.hmrc.tai.model.domain.*
import uk.gov.hmrc.tai.model.domain.AnnualAccount.{annualAccountHodReads, format}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.{CacheService, LockService, RetryService, SensitiveFormatService}
import uk.gov.hmrc.tai.util.LockedException

import java.util.UUID
import javax.inject.Named
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

trait RtiConnector extends RawResponseReads {
  def getPaymentsForYear(nino: Nino, taxYear: TaxYear)(implicit
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
  lockService: LockService,
  sensitiveFormatService: SensitiveFormatService,
  retryService: RetryService,
  cacheService: CacheService
) extends RtiConnector with Logging {
  import cacheService.cacheEither
  import lockService.withLock
  import retryService.withRetry

  private val lockedException: PartialFunction[Throwable, Boolean] = { case _: LockedException => true }

  def getPaymentsForYear(nino: Nino, taxYear: TaxYear)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Seq[AnnualAccount]] = {
    val key = s"getPaymentsForYear-$nino-${taxYear.year}"
    EitherT(
      withRetry(exceptionsToRetry = lockedException) {
        withLock(key) {
          cacheEither(key)(
            underlying.getPaymentsForYear(nino: Nino, taxYear: TaxYear).value
          )(implicitly, sensitiveFormatService.sensitiveFormatFromReadsWritesJsArray[Seq[AnnualAccount]])
        }
      }
    )
  }
}

@Singleton
class DefaultRtiConnector @Inject() (
  httpClientV2: HttpClientV2,
  rtiConfig: DesConfig,
  urls: RtiUrls,
  featureFlagService: FeatureFlagService
)(implicit ec: ExecutionContext)
    extends RtiConnector {
  val logger: Logger = Logger(this.getClass)

  def getPaymentsForYear(nino: Nino, taxYear: TaxYear)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Seq[AnnualAccount]] =
    getPaymentsForYearHandler(nino, taxYear)

  private def getPaymentsForYearHandler(nino: Nino, taxYear: TaxYear)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, Seq[AnnualAccount]] =
    featureFlagService.getAsEitherT(RtiCallToggle).flatMap { toggle =>
      (toggle.isEnabled, taxYear.year < TaxYear().next.year) match {
        case (false, _) =>
          EitherT.rightT(
            Seq(
              AnnualAccount(
                sequenceNumber = 0,
                taxYear = taxYear,
                rtiStatus = TemporarilyUnavailable
              )
            )
          )
        case (true, false) =>
          EitherT.leftT(UpstreamErrorResponse(s"RTIAPI - SKIP RTI call for year: $taxYear}", 444))
        case (true, true) =>
          logger.info(s"RTIAPI - call for the year: $taxYear}")
          val ninoWithoutSuffix = withoutSuffix(nino)

          val futureResponse: Future[Either[UpstreamErrorResponse, HttpResponse]] = httpClientV2
            .get(url"${urls.paymentsForYearUrl(ninoWithoutSuffix, taxYear)}")(
              hc.withExtraHeaders(createHeader(rtiConfig): _*)
            )
            .transform(_.withRequestTimeout(rtiConfig.timeoutInMilliseconds.milliseconds))
            .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)
          EitherT(
            futureResponse
              .map {
                case Right(httpResponse) =>
                  Right(httpResponse.json.as[Seq[AnnualAccount]](annualAccountHodReads))
                case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _)) =>
                  Right(Seq.empty)
                case Left(error) =>
                  logger.error(
                    s"RTIAPI - ${error.statusCode} error returned from RTI HODS with message ${error.getMessage}"
                  )
                  Left(error)
              }
              .recover { case error: HttpException =>
                Left(UpstreamErrorResponse(error.message, BAD_GATEWAY))
              }
          )
      }
    }
}

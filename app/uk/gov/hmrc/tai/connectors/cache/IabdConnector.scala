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

package uk.gov.hmrc.tai.connectors.cache

import cats.data.OptionT
import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{Format, JsValue, Json}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, _}
import uk.gov.hmrc.tai.config.NpsConfig
import uk.gov.hmrc.tai.connectors.{HttpHandler, IabdUrls}
import uk.gov.hmrc.tai.model.domain.response.{HodUpdateFailure, HodUpdateResponse, HodUpdateSuccess}
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.{IabdUpdateAmount, IabdUpdateAmountFormats}
import uk.gov.hmrc.tai.repositories.cache.APICacheRepository
import uk.gov.hmrc.tai.util.HodsSource.NpsSource

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class CachingIabdConnector @Inject()(@Named("default") underlying: IabdConnector,
                                     cache: APICacheRepository)(implicit ec: ExecutionContext)
  extends IabdConnector {

  type CacheType[A] = Future[A]

  private def invalidate[A](nino: String, taxYear: Int)(f: => Future[A]): Future[A] =
    for {result <- f
         _ <- Future
           .sequence(Seq(
             cache.invalidate(s"iabds-$nino-$taxYear"),
           )).fallbackTo(Future.successful(None))
         } yield result

  private def cache[A: Format](key: String): OptionT[Future, A] =
    OptionT(cache.get(key).map(_.flatMap(_.asOpt[A])) recoverWith { case NonFatal(_) =>
      Future.successful(None)
    })

  override def iabds(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): CacheType[JsValue] = {

    val cacheKey = s"iabds-${nino.nino}-${taxYear.year}"

    cache[JsValue](cacheKey).foldF(underlying.iabds(nino, taxYear)
      .map { data =>
        cache.set(cacheKey, data)
        data
      }
    )(some => Future.successful(some))
  }

  override def updateTaxCodeAmount(nino: Nino, taxYear: TaxYear, employmentId: Int, version: Int, iabdType: Int, amount: Int)(
    implicit hc: HeaderCarrier): Future[HodUpdateResponse] = {
    underlying.updateTaxCodeAmount(nino, taxYear, employmentId, version, iabdType, amount).flatMap { response =>
      invalidate(nino.nino, taxYear.year) {
        Future.successful(response)
      }
    }
  }
}

class DefaultIabdConnector @Inject()(httpHandler: HttpHandler,
                                     npsConfig: NpsConfig,
                                     iabdUrls: IabdUrls,
                                     IabdUpdateAmountFormats: IabdUpdateAmountFormats)(implicit ec: ExecutionContext)
  extends IabdConnector {

  override def iabds(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue] =
    if (taxYear > TaxYear()) {
      Future.successful(Json.arr())
    } else {
      val hodHeaders = Seq(
        HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
        HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
        "Gov-Uk-Originator-Id" -> npsConfig.originatorId,
        "CorrelationId" -> UUID.randomUUID().toString
      )
      val urlNps = iabdUrls.npsIabdUrl(nino, taxYear)
      httpHandler.getFromApi(urlNps, APITypes.NpsIabdAllAPI, hodHeaders)
    }

  private def getUuid = UUID.randomUUID().toString

  private def sessionOrUUID(implicit hc: HeaderCarrier): String =
    hc.sessionId match {
      case Some(sessionId) => sessionId.value
      case None => UUID.randomUUID().toString.replace("-", "")
    }

  private def headersForUpdate(hc: HeaderCarrier, version: Int, txId: String, originatorId: String): Seq[(String, String)] =
    Seq(
      HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
      "ETag" -> version.toString,
      "X-TXID" -> txId,
      "Gov-Uk-Originator-Id" -> originatorId,
      "CorrelationId" -> getUuid
    )

  override def updateTaxCodeAmount(nino: Nino, taxYear: TaxYear, employmentId: Int, version: Int, iabdType: Int, amount: Int)(
    implicit hc: HeaderCarrier): Future[HodUpdateResponse] = {
    val url = iabdUrls.npsIabdEmploymentUrl(nino, taxYear, iabdType)
    val amountList = List(IabdUpdateAmount(employmentSequenceNumber = employmentId, grossAmount = amount, source = Some(NpsSource)))
    val requestHeader = headersForUpdate(hc, version, sessionOrUUID, npsConfig.originatorId)

    httpHandler.postToApi[List[IabdUpdateAmount]](url, amountList, APITypes.NpsIabdUpdateEstPayManualAPI, requestHeader)(
      implicitly, IabdUpdateAmountFormats.formatList
    ).map { _ => HodUpdateSuccess }.recover { case _ => HodUpdateFailure }
  }
}

trait IabdConnector {

  def iabds(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue]

  def updateTaxCodeAmount(nino: Nino, taxYear: TaxYear, employmentId: Int, version: Int, iabdType: Int, amount: Int)(
    implicit hc: HeaderCarrier): Future[HodUpdateResponse]
}

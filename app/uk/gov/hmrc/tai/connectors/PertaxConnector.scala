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

package uk.gov.hmrc.benefits.connectors

import cats.data.EitherT
import com.google.inject.Inject
import play.api.Logging
import play.api.http.HeaderNames
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.tai.config.PertaxConfig
import uk.gov.hmrc.tai.connectors.HttpClientResponse
import uk.gov.hmrc.tai.model.PertaxResponse

import scala.concurrent.{ExecutionContext, Future}

class PertaxConnector @Inject() (
  http: HttpClientV2,
  httpClientResponse: HttpClientResponse,
  pertaxConfig: PertaxConfig
) extends Logging {

  private val pertaxUrl = pertaxConfig.pertaxUrl

  def pertaxPostAuthorise(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, UpstreamErrorResponse, PertaxResponse] =
    httpClientResponse
      .read(
        http
          .post(url"$pertaxUrl/pertax/authorise")
          .setHeader(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json")
          .execute[Either[UpstreamErrorResponse, HttpResponse]]
      )
      .map(_.json.as[PertaxResponse])

}

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
import play.api.http.Status
import play.api.http.Status._
import play.api.libs.json.Format
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.ETag
import uk.gov.hmrc.tai.model.domain.{Person, PersonFormatter}
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.nps.PersonDetails

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CitizenDetailsConnector @Inject()(
  metrics: Metrics,
  httpClient: HttpClient,
  auditor: Auditor,
  urls: CitizenDetailsUrls)(implicit ec: ExecutionContext)
    extends BaseConnector(auditor, metrics, httpClient) {

  override val originatorId: String = ""

  def getPerson(nino: Nino)(implicit hc: HeaderCarrier): Future[Person] = {
    val api = APITypes.NpsPersonAPI
    val url = urls.designatoryDetailsUrl(nino)

    val timerContext = metrics.startTimer(api)
    val futureResponse = httpClient.GET[HttpResponse](url)
    futureResponse.flatMap { httpResponse =>
      timerContext.stop()
      httpResponse.status match {
        case Status.OK =>
          metrics.incrementSuccessCounter(api)
          val person = httpResponse.json.as[Person](PersonFormatter.personHodRead)
          Future.successful(person)
        case Status.LOCKED =>
          metrics.incrementSuccessCounter(api)
          Future.successful(Person.createLockedUser(nino))
        case _ => {
          Logger.warn(s"Calling person details from citizen details failed: " + httpResponse.status + " url " + url)
          metrics.incrementFailedCounter(api)
          Future.failed(new HttpException(httpResponse.body, httpResponse.status))
        }
      }
    }
  }

  def getPersonDetails(nino: Nino)(implicit hc: HeaderCarrier, formats: Format[PersonDetails]): Future[PersonDetails] =
    getPersonDetailsFromCitizenDetails(urls.designatoryDetailsUrl(nino), nino, APITypes.NpsPersonAPI)

  def getEtag(nino: Nino)(implicit hc: HeaderCarrier): Future[Option[ETag]] =
    httpClient.GET(urls.etagUrl(nino)) flatMap { response =>
      response.status match {
        case OK =>
          Future.successful(response.json.asOpt[ETag])
        case errorStatus =>
          Logger.error(s"[CitizenDetailsService.getEtag] Failed to get an ETag from citizen-details: $errorStatus")
          Future.successful(None)
      }
    }
}

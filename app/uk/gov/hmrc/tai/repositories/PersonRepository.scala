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

package uk.gov.hmrc.tai.repositories

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.connectors.{CacheId, CitizenDetailsConnector}
import uk.gov.hmrc.tai.model.domain.{Person, PersonFormatter}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PersonRepository @Inject()(cacheRepository: CacheRepository, citizenDetailsConnector: CitizenDetailsConnector)(
  implicit ec: ExecutionContext) {

  private val PersonMongoKey = "PersonData"

  def getPerson(nino: Nino)(implicit hc: HeaderCarrier): Future[Person] = {
    val cacheId = CacheId(nino)
    getPersonFromStorage(cacheId).flatMap { person: Option[Person] =>
      person match {
        case Some(person) => Future.successful(person)
        case _                     => getPersonFromAPI(nino, cacheId)
      }
    }
  }

  private[repositories] def getPersonFromStorage(cacheId: CacheId): Future[Option[Person]] =
    cacheRepository.find(cacheId, PersonMongoKey)(PersonFormatter.personMongoFormat)

  private[repositories] def getPersonFromAPI(nino: Nino, cacheId: CacheId)(implicit hc: HeaderCarrier): Future[Person] =
    citizenDetailsConnector.getPerson(nino).flatMap { person =>
      cacheRepository.createOrUpdate(cacheId, person, PersonMongoKey)(PersonFormatter.personMongoFormat)
    }
}

/*
 * Copyright 2019 HM Revenue & Customs
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

import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNull, Json}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.connectors.{CacheConnector, IabdConnector}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.MongoConstants

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

class IabdRepositorySpec extends PlaySpec with MockitoSugar with MongoConstants {

  "IABD repository" must {
    "return json" when {
      "data is present in cache" in {
        val cacheConnector = mock[CacheConnector]
        when(cacheConnector.findJson(any(), Matchers.eq(s"$IabdMongoKey${TaxYear().year}"))).thenReturn(Future.successful(Some(jsonAfterFormat)))
        val sut = createSUT(cacheConnector)

        val result = Await.result(sut.iabds(nino, TaxYear()), 5.seconds)

        result mustBe jsonAfterFormat
      }

      "data is not present in cache" in {
        val cacheConnector = mock[CacheConnector]
        val iabdConnector = mock[IabdConnector]
        when(cacheConnector.findJson(any(), Matchers.eq(s"$IabdMongoKey${TaxYear().year}"))).thenReturn(Future.successful(None))
        when(cacheConnector.createOrUpdateJson(any(), any(), any())).
          thenReturn(Future.successful(jsonAfterFormat))
        when(iabdConnector.iabds(any(), any())(any())).thenReturn(Future.successful(jsonFromIabdApi))
        val sut = createSUT(cacheConnector, iabdConnector)

        val result = Await.result(sut.iabds(nino, TaxYear()), 5.seconds)

        result mustBe jsonAfterFormat
      }
    }
  }

  private implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("testSession")))
  private val nino = new Generator(new Random).nextNino

  private val jsonFromIabdApi = Json.arr(
    Json.obj(
      "nino" -> nino.withoutSuffix,
      "taxYear" -> 2017,
      "type" -> 10,
      "source" -> 15,
      "grossAmount" -> JsNull,
      "receiptDate" -> JsNull,
      "captureDate" -> "10/04/2017",
      "typeDescription" -> "Total gift aid Payments",
      "netAmount" -> 100
    ),
    Json.obj(
      "nino" -> nino.withoutSuffix,
      "employmentSequenceNumber" -> 1,
      "taxYear" -> 2017,
      "type" -> 27,
      "source" -> 15,
      "grossAmount" -> JsNull,
      "receiptDate" -> JsNull,
      "captureDate" -> "10/04/2017",
      "typeDescription" -> "Total gift aid Payments",
      "netAmount" -> 100
    )
  )

  private val jsonAfterFormat = Json.arr(
    Json.obj(
      "nino" -> nino.withoutSuffix,
      "employmentSequenceNumber" -> 1,
      "source" -> 15,
      "type" -> 27
    )
  )

  private def createSUT(
                         cacheConnector: CacheConnector = mock[CacheConnector],
                         iabdConnector: IabdConnector = mock[IabdConnector]) = {
    new IabdRepository(cacheConnector, iabdConnector)
  }

}

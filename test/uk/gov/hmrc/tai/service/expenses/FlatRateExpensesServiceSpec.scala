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

package uk.gov.hmrc.tai.service.expenses

import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNull, Json}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.connectors.{DesConnector, IabdConnector}
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model.IabdUpdateExpensesData
import uk.gov.hmrc.tai.model.nps2.IabdType
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class FlatRateExpensesServiceSpec extends PlaySpec
  with MockitoSugar
  with MockAuthenticationPredicate with ScalaFutures {

  private implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("TEST")))

  private val mockDesConnector = mock[DesConnector]
  private val mockIabdConnector = mock[IabdConnector]

  private val service = new FlatRateExpensesService(desConnector = mockDesConnector, iabdConnector = mockIabdConnector)

  private val nino = new Generator(new Random).nextNino
  private val iabdUpdateExpensesData = IabdUpdateExpensesData(201800001, 100)
  private val iabd = IabdType.FlatRateJobExpenses
  private val validJson = Json.arr(
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
    )
  )

  "updateFlatRateExpensesData" must {

    "return 200" when {
      "success response from des connector" in {
        when(mockDesConnector.updateExpensesDataToDes(any(),any(),any(),any(),any(),any())(any()))
          .thenReturn(Future.successful(HttpResponse(200)))

        Await.result(service.updateFlatRateExpensesData(nino, TaxYear(), 1, iabdUpdateExpensesData), 5 seconds)
          .status mustBe 200
      }
    }

    "return 500" when {
      "failure response from des connector" in {
        when(mockDesConnector.updateExpensesDataToDes(any(),any(),any(),any(),any(),any())(any()))
          .thenReturn(Future.successful(HttpResponse(500)))

        Await.result(service.updateFlatRateExpensesData(nino, TaxYear(), 1, iabdUpdateExpensesData), 5 seconds)
          .status mustBe 500
      }
    }
  }

  "getFlatRateExpensesData" must {

    "return JsValue" when {
      "success response from iabd connector" in {
        when(mockIabdConnector.iabdByType(any(),any(),any())(any()))
          .thenReturn(Future.successful(validJson))

        val result = service.getFlatRateExpenses(nino, TaxYear(), iabd)

        whenReady(result){
          _ mustBe validJson
        }
      }
    }

    "return exception" when {
      "failed response from iabd connector" in {
        when(mockIabdConnector.iabdByType(any(),any(),any())(any()))
          .thenReturn(Future.failed(new Exception))

        val result = service.getFlatRateExpenses(nino, TaxYear(), iabd)

        intercept[Exception] {
          whenReady(result) {
            _ mustBe an[Exception]
          }
        }
      }
    }
  }
}

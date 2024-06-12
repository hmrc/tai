/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.tai.service

import org.mockito.ArgumentMatchers.{any, eq => meq}
import play.api.libs.json.{JsArray, JsNull, Json}
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.tai.connectors.IabdConnector
import uk.gov.hmrc.tai.model.domain.formatters.IabdDetails
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.BaseSpec

import java.time.LocalDate
import scala.concurrent.Future

class IabdServiceSpec extends BaseSpec {

  private val mockIabdConnector = mock[IabdConnector]
  private def createSut(iabdConnector: IabdConnector = mock[IabdConnector]) =
    new IabdService(iabdConnector)

  "retrieveIabdDetails" must {
    "return a sequence of IabdDetails" when {
      "provided with valid nino and tax year" in {
        val iabdJson = Json.arr(
          Json.obj(
            "nino"            -> "BR5600244",
            "taxYear"         -> 2017,
            "type"            -> 10,
            "source"          -> 15,
            "grossAmount"     -> JsNull,
            "receiptDate"     -> JsNull,
            "captureDate"     -> "10/04/2017",
            "typeDescription" -> "Total gift aid Payments",
            "netAmount"       -> 100
          ),
          Json.obj(
            "nino"                     -> "KX8600231",
            "employmentSequenceNumber" -> 2,
            "taxYear"                  -> 2017,
            "type"                     -> 27,
            "source"                   -> 15,
            "grossAmount"              -> JsNull,
            "receiptDate"              -> "10/04/2017",
            "captureDate"              -> "10/04/2017",
            "typeDescription"          -> "Total gift aid Payments",
            "netAmount"                -> 100
          )
        )

        when(mockIabdConnector.iabds(any(), any())(any())).thenReturn(Future.successful(iabdJson))

        val sut = createSut(mockIabdConnector)
        val result = sut.retrieveIabdDetails(nino, TaxYear()).futureValue

        result mustBe Seq(
          IabdDetails(
            Some("KX8600231"),
            Some(2),
            Some(15),
            Some(27),
            Some(LocalDate.parse("2017-04-10")),
            Some(LocalDate.parse("2017-04-10"))
          )
        )
      }
    }

    "throw NotFoundException " when {
      "empty sequence of IabdDetails is received from connector" in {
        when(mockIabdConnector.iabds(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(JsArray(Seq.empty)))

        val sut = createSut(mockIabdConnector)
        val result = sut.retrieveIabdDetails(nino, TaxYear())

        whenReady(result.failed) { ex =>
          ex mustBe a[NotFoundException]
        }
      }
    }
  }
}

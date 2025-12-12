/*
 * Copyright 2025 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.libs.json.{JsArray, JsNull, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.tai.connectors.IabdConnector
import uk.gov.hmrc.tai.model.domain.IabdDetails
import uk.gov.hmrc.tai.model.domain.response.{HodUpdateFailure, HodUpdateSuccess, IncomeUpdateFailed, IncomeUpdateSuccess}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.BaseSpec

import java.time.LocalDate
import scala.concurrent.Future

class IabdServiceSpec extends BaseSpec {

  private val mockIabdConnector = mock[IabdConnector]
  implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  private def createSut(iabdConnector: IabdConnector = mock[IabdConnector]) =
    new IabdService(iabdConnector)

  "retrieveIabdDetails" must {
    "return a sequence of IabdDetails (filtered to 027 when no type param provided)" when {
      "provided with valid nino and tax year from HIP" in {
        val iabdJson = Json.arr(
          Json.obj(
            "nationalInsuranceNumber" -> nino.value,
            "taxYear"                 -> 2017,
            "type"                    -> "Balancing Charge (027)",
            "source"                  -> "Annual Coding",
            "grossAmount"             -> JsNull,
            "receiptDate"             -> JsNull,
            "captureDate"             -> "2017-04-10",
            "typeDescription"         -> "Total gift aid Payments",
            "netAmount"               -> 100
          ),
          Json.obj(
            "nationalInsuranceNumber"  -> nino.value,
            "employmentSequenceNumber" -> 2,
            "taxYear"                  -> 2017,
            "type"                     -> "New Estimated Pay (027)",
            "source"                   -> "EMAIL",
            "grossAmount"              -> JsNull,
            "receiptDate"              -> "2017-04-10",
            "captureDate"              -> "2017-04-10",
            "typeDescription"          -> "Total gift aid Payments",
            "netAmount"                -> 100
          )
        )

      val json = Json.parse(
        s"""{
           |   "iabdDetails": ${Json.stringify(iabdJson)}
           |}""".stripMargin
      )

      when(mockIabdConnector.iabds(any(), any(), any())(any()))
        .thenReturn(Future.successful(json))

      val sut = createSut(mockIabdConnector)
      val result = sut.retrieveIabdDetails(nino, TaxYear()).futureValue

        result mustBe Seq(
          IabdDetails(
            nino = Some(nino.value.take(8)),
            employmentSequenceNumber = None,
            source = Some(26),
            `type` = Some(27),
            receiptDate = None,
            captureDate = Some(LocalDate.parse("2017-04-10")),
            grossAmount = None,
            netAmount = Some(100)
          ),
          IabdDetails(
            nino = Some(nino.value.take(8)),
            employmentSequenceNumber = Some(2),
            source = Some(17),
            `type` = Some(27),
            receiptDate = Some(LocalDate.parse("2017-04-10")),
            captureDate = Some(LocalDate.parse("2017-04-10")),
            grossAmount = None,
            netAmount = Some(100)
          )
        )
      }

      "not filter when an explicit type is supplied (service returns whatever the connector gives)" in {
        val iabdJson = Json.arr(
          Json.obj(
            "nationalInsuranceNumber"  -> "AA111111A",
            "employmentSequenceNumber" -> 1,
            "taxYear"                  -> 2017,
            "type"                     -> "New Estimated Pay (027)",
            "source"                   -> "EMAIL",
            "grossAmount"              -> 1234,
            "receiptDate"              -> "2017-04-10",
            "captureDate"              -> "2017-04-10",
            "typeDescription"          -> "New Estimated Pay",
            "netAmount"                -> 1234
          ),
          Json.obj(
            "nationalInsuranceNumber"  -> "AA222222A",
            "employmentSequenceNumber" -> 2,
            "taxYear"                  -> 2017,
            "type"                     -> "Some Other (999)",
            "source"                   -> "LETTER",
            "grossAmount"              -> 2222,
            "receiptDate"              -> "2017-05-01",
            "captureDate"              -> "2017-05-02",
            "typeDescription"          -> "Other",
            "netAmount"                -> 2222
          )
        )

        val json = Json.parse(
          s"""{
             |   "iabdDetails": ${Json.stringify(iabdJson)}
             |}""".stripMargin
        )

        when(mockIabdConnector.iabds(any(), any(), any())(any()))
          .thenReturn(Future.successful(json))

        val sut = createSut(mockIabdConnector)
        val result = sut.retrieveIabdDetails(nino, TaxYear(), iabdType = Some("New Estimated Pay (027)")).futureValue

        result mustBe Seq(
          IabdDetails(
            employmentSequenceNumber = Some(1),
            source = Some(17),
            `type` = Some(27),
            receiptDate = Some(LocalDate.parse("2017-04-10")),
            captureDate = Some(LocalDate.parse("2017-04-10")),
            grossAmount = Some(BigDecimal(1234))
          ),
          IabdDetails(
            employmentSequenceNumber = Some(2),
            source = Some(16),
            `type` = Some(999),
            receiptDate = Some(LocalDate.parse("2017-05-01")),
            captureDate = Some(LocalDate.parse("2017-05-02")),
            grossAmount = Some(BigDecimal(2222))
          )
        )
      }
    }

    "return empty when payload is empty" in {
      val json = Json.obj("correlationId" -> "")

      when(mockIabdConnector.iabds(any(), any(), any())(any()))
        .thenReturn(Future.successful(json))

      val sut = createSut(mockIabdConnector)
      val result = sut.retrieveIabdDetails(nino, TaxYear()).futureValue

      result mustBe Seq.empty
    }

    "return empty sequence when next tax year is requested" in {
      when(mockIabdConnector.iabds(any(), any(), any())(any()))
        .thenReturn(Future.successful(JsArray.empty))

      val sut = createSut(mockIabdConnector)
      val result = sut.retrieveIabdDetails(nino, TaxYear().next).futureValue

      result mustBe Seq.empty[IabdDetails]
    }
  }

  "updateTaxCodeAmount" must {
    "return IncomeUpdateSuccess when the update is successful" in {
      when(mockIabdConnector.updateTaxCodeAmount(any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(HodUpdateSuccess))

      val sut = createSut(mockIabdConnector)
      val result = sut.updateTaxCodeAmount(nino, TaxYear(), employmentId = 1, version = 1, amount = 5000).futureValue

      result mustBe IncomeUpdateSuccess
    }

    "return IncomeUpdateFailed when the update fails" in {
      when(mockIabdConnector.updateTaxCodeAmount(any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(HodUpdateFailure))

      val sut = createSut(mockIabdConnector)
      val result = sut.updateTaxCodeAmount(nino, TaxYear(), employmentId = 1, version = 1, amount = 5000).futureValue

      result mustBe IncomeUpdateFailed(s"Hod update failed for ${TaxYear().year} update")
    }
  }
}

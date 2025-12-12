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

package uk.gov.hmrc.tai.controllers

import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.{reset, when}
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status}
import uk.gov.hmrc.tai.model.domain.IabdDetails
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.IabdService
import uk.gov.hmrc.tai.util.BaseSpec

import java.time.LocalDate
import scala.concurrent.Future

class IabdControllerSpec extends BaseSpec {

  private val mockIabdService: IabdService = mock[IabdService]

  private val sut = new IabdController(
    loggedInAuthenticationAuthJourney,
    mockIabdService,
    cc
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockIabdService)
  }

  "getIabds" must {
    "return IABDs for the given NINO (no query param)" in {
      when(mockIabdService.retrieveIabdDetails(any(), any(), meq(None))(any())).thenReturn(
        Future.successful(
          Seq(
            IabdDetails(
              employmentSequenceNumber = Some(1),
              source = Some(10),
              `type` = Some(11),
              receiptDate = Some(LocalDate.parse("2025-01-01")),
              captureDate = Some(LocalDate.parse("2025-01-01")),
              grossAmount = Some(BigDecimal(1234.5))
            ),
            IabdDetails(
              employmentSequenceNumber = Some(1),
              source = Some(10),
              `type` = Some(11),
              receiptDate = Some(LocalDate.parse("2025-01-01")),
              captureDate = Some(LocalDate.parse("2025-01-01")),
              grossAmount = Some(BigDecimal(1234.5))
            )
          )
        )
      )

      val result = sut.getIabds(nino, TaxYear())(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.parse(
        """{"data":{"iabdDetails": [
          |{"employmentSequenceNumber":1,"type":11,"receiptDate":"2025-01-01","captureDate":"2025-01-01","grossAmount":1234.5},
          |{"employmentSequenceNumber":1,"type":11,"receiptDate":"2025-01-01","captureDate":"2025-01-01","grossAmount":1234.5}
          |]},"links":[]}""".stripMargin
      )
    }

    "forward the optional type query param to the service" in {
      val q = "New Estimated Pay (027)"

      when(mockIabdService.retrieveIabdDetails(any(), any(), meq(Some(q)))(any()))
        .thenReturn(
          Future.successful(
            Seq(
              IabdDetails(
                employmentSequenceNumber = Some(9),
                source = None,
                `type` = Some(27),
                receiptDate = Some(LocalDate.parse("2025-02-02")),
                captureDate = Some(LocalDate.parse("2025-02-03")),
                grossAmount = Some(BigDecimal(9999))
              )
            )
          )
        )

      val req = FakeRequest("GET", s"/tai/${nino.nino}/iabds/years/${TaxYear().year}?type=$q")
      val result = sut.getIabds(nino, TaxYear())(req)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.parse(
        """{"data":{"iabdDetails": [
          |{"employmentSequenceNumber":9,"type":27,"receiptDate":"2025-02-02","captureDate":"2025-02-03","grossAmount":9999}
          |]},"links":[]}""".stripMargin
      )
    }
  }
}

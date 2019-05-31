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

package uk.gov.hmrc.tai.audit

import org.mockito.Matchers
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext

import scala.concurrent.ExecutionContext.Implicits.global

class AuditorSpec extends PlaySpec with MockitoSugar {

  "Auditable" should {
    "send audit event" when {
      "send data event method has called with default value" in {
        implicit val hc = HeaderCarrier()

        val mockAudit = mock[AuditConnector]

        val auditor = createSut(mockAudit)

        auditor.sendDataEvent("Test-tx", detail = Map.empty)

        verify(mockAudit, times(1)).sendExplicitAudit(any(): String, any(): Map[String, String])(any(), any())
      }

      "send data event method has called with custom values 2" in {
        implicit val hc = HeaderCarrier()

        val mockAudit = mock[AuditConnector]

        val auditor = createSut(mockAudit)

        val detail = Map(
          "ABC" -> "XYZ",
          "PQR" -> "DEF"
        )

        auditor.sendDataEvent("Test-tx", detail)

        verify(mockAudit, times(1)).sendExplicitAudit(Matchers.eq("Test-tx"), Matchers.eq(detail))(any(),any())
      }
    }
  }

  private def createSut(audit: AuditConnector) =
    new Auditor(audit)
}
/*
 * Copyright 2018 HM Revenue & Customs
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

import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import uk.gov.hmrc.play.audit.model.{Audit, DataEvent}
import uk.gov.hmrc.http.HeaderCarrier

class AuditorSpec extends PlaySpec with MockitoSugar {

  "Auditable" should {
    "send audit event" when {
      "send data event method has called with default value" in {
        implicit val hc = HeaderCarrier()

        val func: (DataEvent) => Unit = mock[(DataEvent) => Unit]
        when(func.apply(any()))
          .thenReturn(())

        val mockAudit = mock[Audit]
        when(mockAudit.sendDataEvent)
          .thenReturn(func)

        val sut = createSut(mockAudit)

        sut.sendDataEvent("Test-tx", detail = Map.empty)

        verify(mockAudit, times(1)).sendDataEvent
      }

      "send data event method has called with custom values" in {
        implicit val hc = HeaderCarrier()

        val captor: ArgumentCaptor[DataEvent] = ArgumentCaptor.forClass(classOf[DataEvent])

        val func: (DataEvent) => Unit = mock[(DataEvent) => Unit]
        when(func.apply(any()))
          .thenReturn(())

        val mockAudit = mock[Audit]
        when(mockAudit.sendDataEvent)
          .thenReturn(func)

        val sut = createSut(mockAudit)

        sut.sendDataEvent(
          "Test-tx",
          detail = Map(
            "ABC" -> "XYZ",
            "PQR" -> "DEF"))

        verify(mockAudit, times(1)).sendDataEvent
        verify(func, times(1)).apply(captor.capture())

        val dataEvent = captor.getValue
        dataEvent.detail("ABC") mustBe "XYZ"
        dataEvent.detail("PQR") mustBe "DEF"
      }
    }
  }

  private def createSut(audit: Audit) =
    new Auditor(audit)
}
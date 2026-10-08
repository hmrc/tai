/*
 * Copyright 2026 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.{never, reset, verify}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.model.dms.DmsCallback
import uk.gov.hmrc.tai.util.BaseSpec

class DmsSubmissionServiceSpec extends BaseSpec {

  private val mockAuditor = mock[Auditor]

  private val sut = new DmsSubmissionService(mockAuditor)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuditor)
  }

  "handleCallback" must {

    "audit a processed submission" in {
      sut.handleCallback(DmsCallback("dms-1", "Processed")).futureValue

      verify(mockAuditor).sendDataEvent(
        meq("DmsSubmissionProcessed"),
        meq(Map("dmsSubmissionId" -> "dms-1"))
      )(any())
    }

    "audit a failed submission with its reason" in {
      sut.handleCallback(DmsCallback("dms-1", "Failed", Some("bad file"), Some("Unknown"))).futureValue

      verify(mockAuditor).sendDataEvent(
        meq("DmsSubmissionFailed"),
        meq(Map("dmsSubmissionId" -> "dms-1", "failureType" -> "Unknown", "failureReason" -> "bad file"))
      )(any())
    }

    "cope with a failed submission that has no reason" in {
      sut.handleCallback(DmsCallback("dms-1", "Failed")).futureValue

      verify(mockAuditor).sendDataEvent(
        meq("DmsSubmissionFailed"),
        meq(Map("dmsSubmissionId" -> "dms-1", "failureType" -> "", "failureReason" -> ""))
      )(any())
    }

    "ignore a status it does not recognise" in {
      sut.handleCallback(DmsCallback("dms-1", "SOMETHING_ELSE")).futureValue

      verify(mockAuditor, never).sendDataEvent(any(), any())(any())
    }
  }
}

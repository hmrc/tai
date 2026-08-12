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

package uk.gov.hmrc.tai.repositories.cache

import uk.gov.hmrc.http.{HeaderCarrier, RequestId}
import uk.gov.hmrc.tai.util.BaseSpec

class RequestCacheIdSpec extends BaseSpec {

  val sut: HeaderCarrier => String = RequestCacheId.run

  "RequestCacheId" must {
    "return the request id" in {
      sut(HeaderCarrier(requestId = Some(RequestId("request-id")))) mustBe "request-id"
    }

    "return a random request id when request id is missing" in {
      val result = sut(HeaderCarrier())

      result must fullyMatch regex
        "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"
    }
  }
}

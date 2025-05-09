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

import cats.effect.IO
import uk.gov.hmrc.tai.config.RtiConfig
import uk.gov.hmrc.tai.util.BaseSpec

class RetryServiceSpec extends BaseSpec {
  private val mockRtiConfig: RtiConfig = mock[RtiConfig]
  private val sut: RetryService = new RetryService(mockRtiConfig)
  "retry" must {
    "work" in {
      val x = IO("")
      whenReady(sut.retry[String](x)) { result =>
        result mustBe ""
      }
    }
  }
}

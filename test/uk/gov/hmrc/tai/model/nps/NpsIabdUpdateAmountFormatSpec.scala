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

package uk.gov.hmrc.tai.model.nps

import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNumber, JsValue, Json}
import uk.gov.hmrc.tai.config.FeatureTogglesConfig

class IabdUpdateAmountFormatspec extends PlaySpec
  with MockitoSugar {

  "Json writes to DES" must {
    "output the correct employment Sequence Number field name" when {
      "des updates are enabled" in {
        val mockConfig = mock[FeatureTogglesConfig]
        when(mockConfig.desUpdateEnabled).thenReturn(true)

        val sut = new IabdUpdateAmountFormats(mockConfig)
        val j: JsValue = Json.toJson(IabdUpdateAmount(12, 1000, Some(800), Some("2017-06-07"), Some(39)))(sut.formats)

        (j \ "employmentSeqNo").get mustBe JsNumber(12)
      }
    }
  }

  "Json writes to NPS" must {
    "output the correct employment Sequence Number field name" when {
      "des updates are disabled" in {
        val mockConfig = mock[FeatureTogglesConfig]
        when(mockConfig.desUpdateEnabled).thenReturn(false)

        val sut = new IabdUpdateAmountFormats(mockConfig)
        val j: JsValue = Json.toJson(IabdUpdateAmount(12, 1000, Some(800), Some("2017-06-07"), Some(39)))(sut.formats)

        (j \ "employmentSequenceNumber").get mustBe JsNumber(12)
      }
    }
  }
}
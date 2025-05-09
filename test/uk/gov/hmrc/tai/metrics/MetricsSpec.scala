/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.tai.metrics

import com.codahale.metrics.{Counter, MetricRegistry, Timer}
import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.enums.APITypes

class MetricsSpec extends PlaySpec with MockitoSugar {

  "startTimer" must {
    "return a timer context" when {
      "given an api lable" in {
        val mockMetricRegistry = mock[MetricRegistry]
        when(mockMetricRegistry.timer(any()))
          .thenReturn(new Timer())

        val sut = new Metrics(mockMetricRegistry)

        val timer: Timer.Context = sut.startTimer(APITypes.BbsiAPI)
        timer.stop()
        timer.close()

        verify(mockMetricRegistry, times(1))
          .timer(meq("bbsi-timer"))
      }
    }
  }
  "incrementSuccessCounter" must {
    "increment the given success metric" in {
      val mockCounter = mock[Counter]
      val mockMetricRegistry = mock[MetricRegistry]
      when(mockMetricRegistry.counter(meq("company-car-success-counter")))
        .thenReturn(mockCounter)

      val sut = new Metrics(mockMetricRegistry)

      sut.incrementSuccessCounter(APITypes.CompanyCarAPI)

      verify(mockCounter, times(1)).inc()
    }
  }
}

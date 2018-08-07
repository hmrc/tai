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

package uk.gov.hmrc.tai.service

import org.joda.time.LocalDate
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.tai.connectors.TaxCodeChangeConnector
import uk.gov.hmrc.tai.model.{TaxCodeHistory, TaxCodeRecord}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

class TaxCodeChangeServiceSpec extends PlaySpec with MockitoSugar {

  "hasTaxCodeChanged" should {

    "return true" when {
      "there has been a tax code change" in {
        val testNino = new Generator(new Random).nextNino
        val taxCodeHistory =
          TaxCodeHistory(
            testNino.nino,
            Some(Seq(
              TaxCodeRecord(taxCode="1185L", employerName="employer2", operatedTaxCode="operated", p2Date=LocalDate.now()),
              TaxCodeRecord(taxCode="1080L", employerName="employer1", operatedTaxCode="operated", p2Date=LocalDate.now().minusMonths(1))
            ))
          )

        val mockConnector = mock[TaxCodeChangeConnector]
        val service = new TaxCodeChangeServiceImpl(mockConnector)

        when(mockConnector.taxCodeHistory(any(), any())).thenReturn(Future.successful(taxCodeHistory))

        Await.result(service.hasTaxCodeChanged(testNino), 1.seconds) mustEqual true
      }
    }

    "return false" when {
      "tax code change is not returned" in {
        val testNino = new Generator(new Random).nextNino
        val mockConnector = mock[TaxCodeChangeConnector]
        when(mockConnector.taxCodeHistory(any(), any())).thenReturn(Future.successful(TaxCodeHistory(testNino.nino, None)))
        val service = new TaxCodeChangeServiceImpl(mockConnector)

        Await.result(service.hasTaxCodeChanged(testNino), 1.seconds) mustEqual false
      }

      "tax code change is empty" in {
        val testNino = new Generator(new Random).nextNino
        val mockConnector = mock[TaxCodeChangeConnector]
        when(mockConnector.taxCodeHistory(any(), any())).thenReturn(Future.successful(TaxCodeHistory(testNino.nino, Some(Seq.empty[TaxCodeRecord]))))
        val service = new TaxCodeChangeServiceImpl(mockConnector)

        Await.result(service.hasTaxCodeChanged(testNino), 1.seconds) mustEqual false
      }

      "tax code changes are not in this year" in {
        val testNino = new Generator(new Random).nextNino
        val taxCodeHistory =
          TaxCodeHistory(
            testNino.nino,
            Some(Seq(
              TaxCodeRecord(taxCode="1185L", employerName="employer2", operatedTaxCode="operated", p2Date=LocalDate.now().minusYears(1))
            ))
          )

        val mockConnector = mock[TaxCodeChangeConnector]
        val service = new TaxCodeChangeServiceImpl(mockConnector)

        when(mockConnector.taxCodeHistory(any(), any())).thenReturn(Future.successful(taxCodeHistory))

        Await.result(service.hasTaxCodeChanged(testNino), 1.seconds) mustEqual false
      }

    }
  }


}

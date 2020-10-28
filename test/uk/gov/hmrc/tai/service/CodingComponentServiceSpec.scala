/*
 * Copyright 2020 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.CodingComponentRepository
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class CodingComponentServiceSpec extends BaseSpec {

  "codingComponents" must {
    "return a list of tax components" when {
      "repository returns json with more than one tax components" in {
        val mockIabdRepository = mock[CodingComponentRepository]

        when(mockIabdRepository.codingComponents(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(codingComponentList))

        val service = testCodingComponentService(mockIabdRepository)

        val result = Await.result(service.codingComponents(nino, TaxYear()), 5 seconds)
        result mustBe codingComponentList
      }
    }
  }

  "codingComponentsForId" should {
    "return Sequence of Coding Components from the coding component repository" in {
      val mockIabdRepository = mock[CodingComponentRepository]

      val expected = Seq(CodingComponent(PersonalAllowancePA, Some(123), 12345, "some description"))

      when(mockIabdRepository.codingComponentsForTaxCodeId(meq(nino), meq(1))(any()))
        .thenReturn(Future.successful(expected))

      val service = testCodingComponentService(mockIabdRepository)

      val result = Await.result(service.codingComponentsForTaxCodeId(nino, 1), 5.seconds)

      result mustBe expected
    }
  }

  private val codingComponentList = Seq[CodingComponent](
    CodingComponent(PersonalAllowancePA, Some(123), 12345, "some description"),
    CodingComponent(CarFuelBenefit, Some(124), 6666, "some other description"),
    CodingComponent(Commission, Some(125), 777, "some other description"),
    CodingComponent(BalancingCharge, Some(126), 999, "some other description"),
    CodingComponent(NonCodedIncome, Some(1), 100, "Non-Coded-Income")
  )

  private def testCodingComponentService(iabdRepository: CodingComponentRepository) =
    new CodingComponentService(iabdRepository)
}

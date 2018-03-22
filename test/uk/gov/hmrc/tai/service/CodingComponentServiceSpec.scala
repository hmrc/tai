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

import org.mockito.Matchers
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.CodingComponentRepository

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class CodingComponentServiceSpec extends PlaySpec with MockitoSugar {

  "codingComponents" must {
    "return a list of tax components" when {
      "repository returns json with more than one tax components" in {
        val mockIabdRepository = mock[CodingComponentRepository]

        when(mockIabdRepository.codingComponents(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
          .thenReturn(Future.successful(codingComponentList))

        val sut = createSUT(mockIabdRepository)

        val result = Await.result(sut.codingComponents(nino, TaxYear()), 5 seconds)
        result mustBe codingComponentList
      }
    }
  }

  val codingComponentList = Seq[CodingComponent](
    CodingComponent(PersonalAllowancePA, Some(123), 12345, "some description"),
    CodingComponent(CarFuelBenefit, Some(124), 66666, "some other description"),
    CodingComponent(Commission, Some(125), 777, "some other description"),
    CodingComponent(BalancingCharge, Some(126), 999, "some other description"),
    CodingComponent(NonCodedIncome, Some(1), 100, "Non-Coded-Income")
  )

  private val nino: Nino = new Generator(new Random).nextNino

  private implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("testSession")))

  private def createSUT(iabdRepository: CodingComponentRepository) = new CodingComponentService(iabdRepository)
}
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

package uk.gov.hmrc.tai.controllers

import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.when
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{status, *}
import uk.gov.hmrc.http.UnauthorizedException
import uk.gov.hmrc.tai.controllers.auth.AuthJourney
import uk.gov.hmrc.tai.model.domain.*
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.CodingComponentService
import uk.gov.hmrc.tai.util.{BaseSpec, NpsExceptions}

import scala.concurrent.Future

class CodingComponentControllerSpec extends BaseSpec with NpsExceptions {

  "codingComponentsForYear" must {
    "return OK with sequence of coding components" when {
      "coding component service returns a sequence of coding components" in {
        val codingComponentSeq = Seq(
          CodingComponent(EmployerProvidedServices, Some(12), 12321, "Some Description"),
          CodingComponent(PersonalPensionPayments, Some(31), 12345, "Some Description Some")
        )

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(meq(nino), meq(TaxYear().next))(any()))
          .thenReturn(Future.successful(codingComponentSeq))

        val sut = createSUT(mockCodingComponentService)
        val result = sut.codingComponentsForYear(nino, TaxYear().next)(FakeRequest())
        status(result) mustBe OK
        val expectedJson = Json.obj(
          "data" -> Json.arr(
            Json.obj(
              "componentType" -> "EmployerProvidedServices",
              "employmentId"  -> 12,
              "amount"        -> 12321,
              "description"   -> "Some Description",
              "iabdCategory"  -> "Benefit"
            ),
            Json.obj(
              "componentType" -> "PersonalPensionPayments",
              "employmentId"  -> 31,
              "amount"        -> 12345,
              "description"   -> "Some Description Some",
              "iabdCategory"  -> "Allowance"
            )
          ),
          "links" -> Json.arr()
        )
        contentAsJson(result) mustBe expectedJson
      }
    }

    "throw an exception" when {
      "an exception is thrown by the handler which is not a BadRequestException" in {
        val mockCodingComponentService = mock[CodingComponentService]
        val sut = createSUT(mockCodingComponentService)
        when(mockCodingComponentService.codingComponents(meq(nino), meq(TaxYear().next))(any()))
          .thenReturn(Future.failed(new UnauthorizedException("")))

        val result = sut.codingComponentsForYear(nino, TaxYear().next)(FakeRequest()).failed.futureValue

        result mustBe a[UnauthorizedException]
      }
    }

    "return a bad request" when {
      "a BadRequestException is thrown" in {
        val mockCodingComponentService = mock[CodingComponentService]
        val sut = createSUT(mockCodingComponentService)
        when(mockCodingComponentService.codingComponents(meq(nino), meq(TaxYear().next))(any()))
          .thenReturn(Future.failed(badRequestException))

        checkControllerResponse(
          badRequestException,
          sut.codingComponentsForYear(nino, TaxYear().next)(FakeRequest()),
          BAD_REQUEST
        )
      }
    }

    "return a not found request" when {
      "a NotFoundException is thrown" in {
        val mockCodingComponentService = mock[CodingComponentService]
        val sut = createSUT(mockCodingComponentService)

        when(mockCodingComponentService.codingComponents(meq(nino), meq(TaxYear().next))(any()))
          .thenReturn(Future.failed(notFoundException))

        checkControllerResponse(
          notFoundException,
          sut.codingComponentsForYear(nino, TaxYear().next)(FakeRequest()),
          NOT_FOUND
        )

      }
    }
  }

  private def createSUT(
    codingComponentService: CodingComponentService,
    predicate: AuthJourney = loggedInAuthenticationAuthJourney
  ) =
    new CodingComponentController(predicate, codingComponentService, cc)
}

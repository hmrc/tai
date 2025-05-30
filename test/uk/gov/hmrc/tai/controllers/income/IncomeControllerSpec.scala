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

package uk.gov.hmrc.tai.controllers.income

import cats.data.EitherT
import cats.instances.future.*
import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.when
import play.api.libs.json.*
import play.api.test.Helpers.*
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.tai.config.CustomErrorHandler
import uk.gov.hmrc.tai.controllers.auth.AuthJourney
import uk.gov.hmrc.tai.model.domain.*
import uk.gov.hmrc.tai.model.domain.income.*
import uk.gov.hmrc.tai.model.domain.requests.UpdateTaxCodeIncomeRequest
import uk.gov.hmrc.tai.model.domain.response.{IncomeUpdateFailed, IncomeUpdateResponse, InvalidAmount}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.IncomeService
import uk.gov.hmrc.tai.util.BaseSpec

import java.time.LocalDate
import scala.concurrent.Future

class IncomeControllerSpec extends BaseSpec {

  val employmentId = 1
  val expectedJsonEmpty: JsObject = Json.obj(
    "data"  -> Json.arr(),
    "links" -> Json.arr()
  )

  val mockIncomeService: IncomeService = mock[IncomeService]

  private val untaxedInterest =
    UntaxedInterest(UntaxedInterestIncome, None, 123, "Untaxed Interest")

  val taxCodeIncomes: Seq[TaxCodeIncome] = Seq(
    TaxCodeIncome(
      PensionIncome,
      Some(1),
      BigDecimal(1100),
      PensionIncome.toString,
      "1150L",
      "Employer1",
      Week1Month1BasisOperation,
      Live,
      BigDecimal(0),
      BigDecimal(0),
      BigDecimal(0)
    ),
    TaxCodeIncome(
      EmploymentIncome,
      Some(2),
      BigDecimal(0),
      EmploymentIncome.toString,
      "1100L",
      "Employer2",
      OtherBasisOperation,
      Live,
      BigDecimal(321.12),
      BigDecimal(0),
      BigDecimal(0)
    ),
    TaxCodeIncome(
      EmploymentIncome,
      Some(3),
      BigDecimal(0),
      EmploymentIncome.toString,
      "1100L",
      "Employer2",
      OtherBasisOperation,
      Live,
      BigDecimal(321.12),
      BigDecimal(0),
      BigDecimal(0)
    )
  )

  "untaxedInterest" must {
    "return OK with untaxed interest" when {
      "untaxed interest is returned by income service" in {
        when(mockIncomeService.untaxedInterest(any())(any()))
          .thenReturn(Future.successful(Some(untaxedInterest)))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.untaxedInterest(nino)(FakeRequest())

        val expectedJson = Json.obj(
          "data" -> Json.obj(
            "incomeComponentType" -> "UntaxedInterestIncome",
            "amount"              -> 123,
            "description"         -> "Untaxed Interest"
          ),
          "links" -> Json.arr()
        )

        contentAsJson(result) mustBe expectedJson
      }
    }

    "return Not Found" when {
      "None is returned by income service" in {
        when(mockIncomeService.untaxedInterest(any())(any()))
          .thenReturn(Future.successful(None))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.untaxedInterest(nino)(FakeRequest())

        status(result) mustBe NOT_FOUND
      }

      "a Not Found Exception occurs" in {

        when(mockIncomeService.untaxedInterest(any())(any())).thenReturn(Future.failed(notFoundException))
        val SUT = createSUT(mockIncomeService)
        checkControllerResponse(notFoundException, SUT.untaxedInterest(nino)(FakeRequest()), NOT_FOUND)
      }
    }
  }

  "taxCodeIncomesForYear" must {

    "return Not Found" when {
      "Nil is returned by income service" in {
        when(mockIncomeService.taxCodeIncomes(any(), meq(TaxYear().next))(any(), any()))
          .thenReturn(Future.successful(Seq.empty[TaxCodeIncome]))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.taxCodeIncomesForYear(nino, TaxYear().next)(FakeRequest())

        status(result) mustBe NOT_FOUND
      }

      "a Not Found Exception occurs" in {
        when(mockIncomeService.taxCodeIncomes(any(), meq(TaxYear().next))(any(), any()))
          .thenReturn(Future.failed(notFoundException))

        val SUT = createSUT(mockIncomeService)

        checkControllerResponse(
          notFoundException,
          SUT.taxCodeIncomesForYear(nino, TaxYear().next)(FakeRequest()),
          NOT_FOUND
        )
      }
    }

    "return Ok with tax code incomes" when {
      "a list of tax code incomes is returned by income service" in {
        val taxCodeIncomesNoPension = Seq(
          TaxCodeIncome(
            EmploymentIncome,
            Some(1),
            BigDecimal(1100),
            EmploymentIncome.toString,
            "1150L",
            "Employer1",
            Week1Month1BasisOperation,
            Live,
            BigDecimal(0),
            BigDecimal(0),
            BigDecimal(0)
          ),
          TaxCodeIncome(
            EmploymentIncome,
            Some(2),
            BigDecimal(0),
            EmploymentIncome.toString,
            "1100L",
            "Employer2",
            OtherBasisOperation,
            PotentiallyCeased,
            BigDecimal(321.12),
            BigDecimal(0),
            BigDecimal(0)
          )
        )

        when(mockIncomeService.taxCodeIncomes(any(), meq(TaxYear().next))(any(), any()))
          .thenReturn(Future.successful(taxCodeIncomesNoPension))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.taxCodeIncomesForYear(nino, TaxYear().next)(FakeRequest())

        val expectedJson = Json.obj(
          "data" -> Json.arr(
            Json.obj(
              "componentType"                 -> EmploymentIncome.toString,
              "employmentId"                  -> 1,
              "amount"                        -> 1100,
              "description"                   -> EmploymentIncome.toString,
              "taxCode"                       -> "1150LX",
              "name"                          -> "Employer1",
              "basisOperation"                -> "Week1Month1BasisOperation",
              "status"                        -> Live.toString,
              "inYearAdjustmentIntoCY"        -> 0,
              "totalInYearAdjustment"         -> 0,
              "inYearAdjustmentIntoCYPlusOne" -> 0
            ),
            Json.obj(
              "componentType"                 -> EmploymentIncome.toString,
              "employmentId"                  -> 2,
              "amount"                        -> 0,
              "description"                   -> EmploymentIncome.toString,
              "taxCode"                       -> "1100L",
              "name"                          -> "Employer2",
              "basisOperation"                -> "OtherBasisOperation",
              "status"                        -> PotentiallyCeased.toString,
              "inYearAdjustmentIntoCY"        -> 321.12,
              "totalInYearAdjustment"         -> 0,
              "inYearAdjustmentIntoCYPlusOne" -> 0
            )
          ),
          "links" -> Json.arr()
        )

        contentAsJson(result) mustBe expectedJson
      }
    }
  }

  "matchedTaxCodeIncomesForYear" must {

    val employment = Employment(
      "company name",
      Live,
      Some("888"),
      LocalDate.of(TaxYear().next.year, 5, 26),
      None,
      Nil,
      "",
      "",
      2,
      Some(100),
      hasPayrolledBenefit = false,
      receivingOccupationalPension = true,
      PensionIncome
    )

    "return tax code incomes and employments JSON" in {
      when(mockIncomeService.matchedTaxCodeIncomesForYear(any(), meq(TaxYear().next), any(), any())(any(), any()))
        .thenReturn(EitherT.rightT(Seq(IncomeSource(taxCodeIncomes(1), employment))))

      val sut = createSUT(incomeService = mockIncomeService, authentication = loggedInAuthenticationAuthJourney)
      val result = sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, Live)(FakeRequest())

      val expectedJson = Json.obj(
        "data"  -> Json.arr(Json.toJson(IncomeSource(taxCodeIncomes(1), employment))),
        "links" -> Json.arr()
      )

      contentAsJson(result) mustBe expectedJson
    }

    "return NotFound when a NotFoundException is thrown" in {

      when(mockIncomeService.matchedTaxCodeIncomesForYear(any(), meq(TaxYear().next), any(), any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Seq[IncomeSource]](Future.failed(notFoundException))
        )

      val SUT = createSUT(mockIncomeService)

      checkControllerResponse(
        notFoundException,
        SUT.matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, Live)(FakeRequest()),
        NOT_FOUND
      )
    }

    "return NotFound when a Not Found UpstreamErrorResponse occurs" in {

      when(mockIncomeService.matchedTaxCodeIncomesForYear(any(), meq(TaxYear().next), any(), any())(any(), any()))
        .thenReturn(EitherT.leftT(UpstreamErrorResponse("not found", NOT_FOUND)))

      val SUT = createSUT(mockIncomeService)
      val result = SUT.matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, Live)(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "return BadRequest when a Bad Request UpstreamErrorResponse occurs" in {

      when(mockIncomeService.matchedTaxCodeIncomesForYear(any(), meq(TaxYear().next), any(), any())(any(), any()))
        .thenReturn(EitherT.leftT(UpstreamErrorResponse("Bad request", BAD_REQUEST)))

      val SUT = createSUT(mockIncomeService)
      val result = SUT.matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, Live)(FakeRequest())

      status(result) mustBe BAD_REQUEST
    }

    "return BadRequest when a BadRequestException is thrown" in {
      when(mockIncomeService.matchedTaxCodeIncomesForYear(any(), meq(TaxYear().next), any(), any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Seq[IncomeSource]](Future.failed(badRequestException))
        )

      val SUT = createSUT(mockIncomeService)

      checkControllerResponse(
        badRequestException,
        SUT.matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, Live)(FakeRequest()),
        BAD_REQUEST
      )
    }
  }

  "nonMatchingCeasedEmployments" must {
    val employment = Employment(
      "company name",
      Live,
      Some("888"),
      LocalDate.of(TaxYear().next.year, 5, 26),
      None,
      Nil,
      "",
      "",
      2,
      Some(100),
      hasPayrolledBenefit = false,
      receivingOccupationalPension = true,
      PensionIncome
    )

    "return non matching ceased employments JSON" in {
      val employments =
        Seq(employment, employment.copy(sequenceNumber = 1, endDate = Some(LocalDate.of(TaxYear().next.year, 8, 10))))

      when(mockIncomeService.nonMatchingCeasedEmployments(any(), meq(TaxYear().next))(any(), any()))
        .thenReturn(EitherT.rightT(employments))

      val nextTaxYear = TaxYear().next
      val sut = createSUT(incomeService = mockIncomeService, authentication = loggedInAuthenticationAuthJourney)
      val result = sut.nonMatchingCeasedEmployments(nino, nextTaxYear)(FakeRequest())

      val expectedJson = Json.obj(
        "data"  -> employments,
        "links" -> Json.arr()
      )

      contentAsJson(result) mustBe expectedJson
    }

    "return NotFound when a Not Found UpstreamErrorResponse occurs" in {

      when(mockIncomeService.nonMatchingCeasedEmployments(any(), meq(TaxYear().next))(any(), any()))
        .thenReturn(EitherT.leftT(UpstreamErrorResponse("Not found", NOT_FOUND)))

      val SUT = createSUT(mockIncomeService)
      val result = SUT.nonMatchingCeasedEmployments(nino, TaxYear().next)(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "return NotFound when a NotFoundException is thrown" in {

      when(mockIncomeService.nonMatchingCeasedEmployments(any(), meq(TaxYear().next))(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Seq[Employment]](Future.failed(notFoundException))
        )

      val SUT = createSUT(mockIncomeService)

      checkControllerResponse(
        notFoundException,
        SUT.nonMatchingCeasedEmployments(nino, TaxYear().next)(FakeRequest()),
        NOT_FOUND
      )
    }

    "return BadRequest when a BadRequestException is thrown" in {

      when(mockIncomeService.nonMatchingCeasedEmployments(any(), meq(TaxYear().next))(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Seq[Employment]](Future.failed(badRequestException))
        )

      val SUT = createSUT(mockIncomeService)

      checkControllerResponse(
        badRequestException,
        SUT.nonMatchingCeasedEmployments(nino, TaxYear().next)(FakeRequest()),
        BAD_REQUEST
      )
    }

    "return BadRequest when a bad request UpstreamErrorResponse occurs" in {

      when(mockIncomeService.nonMatchingCeasedEmployments(any(), meq(TaxYear().next))(any(), any()))
        .thenReturn(EitherT.leftT(UpstreamErrorResponse("Bad request", BAD_REQUEST)))

      val SUT = createSUT(mockIncomeService)
      val result = SUT.nonMatchingCeasedEmployments(nino, TaxYear().next)(FakeRequest())

      status(result) mustBe BAD_REQUEST
    }
  }

  "incomes" must {
    "return Ok with income" when {
      "income returned by IncomeService" in {

        val income = uk.gov.hmrc.tai.model.domain.income.Incomes(
          Seq.empty[TaxCodeIncome],
          NonTaxCodeIncome(
            None,
            Seq(
              OtherNonTaxCodeIncome(Profit, None, 100, "Profit")
            )
          )
        )

        when(mockIncomeService.incomes(any(), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(income))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.income(nino, TaxYear())(FakeRequest())

        val expectedJson = Json.obj(
          "data" -> Json.obj(
            "taxCodeIncomes" -> JsArray(),
            "nonTaxCodeIncomes" -> Json.obj(
              "otherNonTaxCodeIncomes" -> Json.arr(
                Json.obj(
                  "incomeComponentType" -> "Profit",
                  "amount"              -> 100,
                  "description"         -> "Profit"
                )
              )
            )
          ),
          "links" -> Json.arr()
        )

        contentAsJson(result) mustBe expectedJson
      }
    }
  }

  "updateTaxCodeIncome" must {

    "return a bad request" when {
      "an invalid update amount is provided" in {
        val SUT = setup(Future.successful(InvalidAmount("")))

        val result = SUT.updateTaxCodeIncome(nino, TaxYear(), employmentId)(fakeTaxCodeIncomeRequest)

        status(result) mustBe BAD_REQUEST
      }
    }

    "return internal server error" when {

      "income update exception has been thrown" in {
        val SUT = setup(Future.successful(IncomeUpdateFailed("Failed")))

        val result = SUT.updateTaxCodeIncome(nino, TaxYear(), employmentId)(fakeTaxCodeIncomeRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR
      }

      "any exception has been thrown" in {
        val runTimeException = new RuntimeException("Error")
        val SUT = setup(Future.failed(runTimeException))

        checkControllerResponse(
          runTimeException,
          SUT.updateTaxCodeIncome(nino, TaxYear(), employmentId)(fakeTaxCodeIncomeRequest),
          INTERNAL_SERVER_ERROR
        )
      }
    }
  }

  private def createSUT(
    incomeService: IncomeService = mock[IncomeService],
    authentication: AuthJourney = loggedInAuthenticationAuthJourney
  ) =
    new IncomeController(incomeService, authentication, cc, inject[CustomErrorHandler])

  private def fakeTaxCodeIncomeRequest: FakeRequest[JsValue] = {
    val updateTaxCodeIncomeRequest = UpdateTaxCodeIncomeRequest(1234)
    FakeRequest("POST", "/", FakeHeaders(), Json.toJson(updateTaxCodeIncomeRequest))
      .withHeaders(("content-type", "application/json"))
  }

  private def setup(response: Future[IncomeUpdateResponse]): IncomeController = {
    val mockIncomeService: IncomeService = {
      val mockIncomeService: IncomeService = mock[IncomeService]
      when(mockIncomeService.updateTaxCodeIncome(any(), any(), any(), any())(any(), any())).thenReturn(response)
      mockIncomeService
    }
    createSUT(mockIncomeService)
  }

}

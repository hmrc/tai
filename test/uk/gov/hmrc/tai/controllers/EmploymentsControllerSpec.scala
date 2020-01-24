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

package uk.gov.hmrc.tai.controllers

import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.http.{BadRequestException, InternalServerException, NotFoundException}
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model.api.ApiResponse
import uk.gov.hmrc.tai.model.domain.{AddEmployment, Employment, EndEmployment, IncorrectEmployment}
import uk.gov.hmrc.tai.model.error.{EmploymentAccountStubbed, EmploymentNotFound}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.EmploymentService

import scala.concurrent.Future
import scala.language.postfixOps

class EmploymentsControllerSpec extends PlaySpec with MockitoSugar with MockAuthenticationPredicate {

  "employments" must {
    "return Ok" when {
      "called with a valid nino and year" in {
        val mockEmploymentService = mock[EmploymentService]
        when(mockEmploymentService.employments(any(), any())(any()))
          .thenReturn(Future.successful(Nil))

        val sut = new EmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate)

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe OK
      }
    }
    "return a valid API json response" when {
      "called with a valid nino and year" in {
        val mockEmploymentService = mock[EmploymentService]
        when(mockEmploymentService.employments(any(), any())(any()))
          .thenReturn(Future.successful(List(emp)))

        val sut = new EmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate)

        val jsonResult = Json.obj(
          "data" -> Json.obj("employments" -> Json.arr(Json.obj(
            "name"                         -> "company name",
            "payrollNumber"                -> "888",
            "startDate"                    -> "2017-05-26",
            "annualAccounts"               -> Json.arr(),
            "taxDistrictNumber"            -> "",
            "payeNumber"                   -> "",
            "sequenceNumber"               -> 2,
            "cessationPay"                 -> 100,
            "hasPayrolledBenefit"          -> false,
            "receivingOccupationalPension" -> true
          ))),
          "links" -> Json.arr()
        )

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        contentAsJson(result) mustBe jsonResult
      }
    }
    "return a non success http response" when {
      "the employments are not found" in {
        val mockEmploymentService = mock[EmploymentService]
        when(mockEmploymentService.employments(any(), any())(any()))
          .thenReturn(Future.failed(new NotFoundException("employment not found")))

        val sut = new EmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate)

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe NOT_FOUND
      }
      "the employments service returns a bad request exception" in {
        val mockEmploymentService = mock[EmploymentService]
        when(mockEmploymentService.employments(any(), any())(any()))
          .thenReturn(Future.failed(new BadRequestException("no employments recorded for this individual")))

        val sut = new EmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate)

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe BAD_REQUEST
        contentAsString(result) mustBe "no employments recorded for this individual"

      }
      "the employments service returns an error" in {
        val mockEmploymentService = mock[EmploymentService]
        when(mockEmploymentService.employments(any(), any())(any()))
          .thenReturn(Future.failed(new InternalServerException("employment service failed")))

        val sut = new EmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate)

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }
  }

  "employment" must {
    "return ok" when {
      "called with valid nino, year and id" in {
        val mockEmploymentService = mock[EmploymentService]
        when(mockEmploymentService.employment(any(), any())(any()))
          .thenReturn(Future.successful(Right(emp)))

        val sut = new EmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate)
        val result = sut.employment(nino, 2)(FakeRequest())

        val jsonResult = Json.obj(
          "data" -> Json.obj(
            "name"                         -> "company name",
            "payrollNumber"                -> "888",
            "startDate"                    -> "2017-05-26",
            "annualAccounts"               -> Json.arr(),
            "taxDistrictNumber"            -> "",
            "payeNumber"                   -> "",
            "sequenceNumber"               -> 2,
            "cessationPay"                 -> 100,
            "hasPayrolledBenefit"          -> false,
            "receivingOccupationalPension" -> true
          ),
          "links" -> Json.arr()
        )

        status(result) mustBe OK
        contentAsJson(result) mustBe jsonResult

        verify(mockEmploymentService, times(1)).employment(any(), Matchers.eq(2))(any())
      }
    }

    "return not found" when {
      "called with valid nino, year and id but id doesn't present" in {
        val mockEmploymentService = mock[EmploymentService]
        when(mockEmploymentService.employment(any(), any())(any()))
          .thenReturn(Future.successful(Left(EmploymentNotFound)))

        val sut = new EmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate)
        val result = sut.employment(nino, 3)(FakeRequest())

        status(result) mustBe NOT_FOUND
        verify(mockEmploymentService, times(1)).employment(any(), Matchers.eq(3))(any())
      }

      "throw not found exception" in {
        val mockEmploymentService = mock[EmploymentService]
        when(mockEmploymentService.employment(any(), any())(any()))
          .thenReturn(Future.failed(new NotFoundException("")))

        val sut = new EmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate)
        val result = sut.employment(nino, 3)(FakeRequest())

        status(result) mustBe NOT_FOUND
        verify(mockEmploymentService, times(1)).employment(any(), Matchers.eq(3))(any())
      }
    }

    "return internal server" when {
      "employment service throws an error" in {
        val mockEmploymentService = mock[EmploymentService]
        when(mockEmploymentService.employment(any(), any())(any()))
          .thenReturn(Future.failed(new InternalServerException("")))

        val sut = new EmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate)
        val result = sut.employment(nino, 3)(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        verify(mockEmploymentService, times(1)).employment(any(), Matchers.eq(3))(any())
      }
    }

    "return bad gateway" when {
      "RTI stubbed account exists" in {
        val mockEmploymentService = mock[EmploymentService]
        when(mockEmploymentService.employment(any(), any())(any()))
          .thenReturn(Future.successful(Left(EmploymentAccountStubbed)))

        val sut = new EmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate)
        val result = sut.employment(nino, 3)(FakeRequest())

        status(result) mustBe BAD_GATEWAY
        verify(mockEmploymentService, times(1)).employment(any(), Matchers.eq(3))(any())
      }
    }

  }

  "endEmployment" must {
    "return an envelope Id" when {
      "given a valid request" in {
        val employment = EndEmployment(new LocalDate("2017-05-05"), "Yes", Some("123456789"))
        val json = Json.toJson(employment)
        val envelopeId = "EnvelopeId"

        val mockEmploymentService = mock[EmploymentService]
        when(mockEmploymentService.endEmployment(any(), any(), any())(any()))
          .thenReturn(Future.successful(envelopeId))

        val sut = new EmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate)
        val result = sut.endEmployment(nino, 3)(
          FakeRequest("POST", "/", FakeHeaders(), json)
            .withHeaders(("content-type", "application/json")))

        status(result) mustBe OK
        contentAsJson(result).as[ApiResponse[String]] mustBe ApiResponse(envelopeId, Nil)
      }
    }
  }

  "addEmployment" must {
    "return envelop Id" when {
      "called with valid add employment request" in {
        val envelopeId = "envelopId"
        val employment = AddEmployment("employerName", new LocalDate("2017-05-05"), "1234", "Yes", Some("123456789"))
        val json = Json.toJson(employment)

        val mockEmploymentService = mock[EmploymentService]
        when(mockEmploymentService.addEmployment(Matchers.eq(nino), Matchers.eq(employment))(any()))
          .thenReturn(Future.successful(envelopeId))

        val sut = new EmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate)
        val result = sut.addEmployment(nino)(
          FakeRequest("POST", "/", FakeHeaders(), json)
            .withHeaders(("content-type", "application/json")))

        status(result) mustBe OK
        contentAsJson(result).as[ApiResponse[String]] mustBe ApiResponse(envelopeId, Nil)
      }
    }
  }

  "incorrectEmployment" must {
    "return an envelope Id" when {
      "called with valid incorrect employment request" in {
        val envelopeId = "envelopeId"
        val employment = IncorrectEmployment("whatYouToldUs", "Yes", Some("123123"))
        val id = 1

        val mockEmploymentService = mock[EmploymentService]
        when(
          mockEmploymentService.incorrectEmployment(Matchers.eq(nino), Matchers.eq(id), Matchers.eq(employment))(any()))
          .thenReturn(Future.successful(envelopeId))

        val sut = new EmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate)
        val result = sut.incorrectEmployment(nino, id)(
          FakeRequest("POST", "/", FakeHeaders(), Json.toJson(employment))
            .withHeaders(("content-type", "application/json")))

        status(result) mustBe OK
        contentAsJson(result).as[ApiResponse[String]] mustBe ApiResponse(envelopeId, Nil)
      }
    }
  }

  "updateEmploymentsForPreviousYear" must {
    "return an envelope Id" when {
      "called with valid incorrect employment request" in {
        val envelopeId = "envelopeId"
        val employment = IncorrectEmployment("whatYouToldUs", "Yes", Some("123123"))
        val taxYear = TaxYear(2016)

        val mockEmploymentService = mock[EmploymentService]
        when(
          mockEmploymentService
            .updatePreviousYearIncome(Matchers.eq(nino), Matchers.eq(taxYear), Matchers.eq(employment))(any()))
          .thenReturn(Future.successful(envelopeId))

        val sut = new EmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate)
        val result = sut.updatePreviousYearIncome(nino, taxYear)(
          FakeRequest("POST", "/", FakeHeaders(), Json.toJson(employment))
            .withHeaders(("content-type", "application/json")))

        status(result) mustBe OK
        contentAsJson(result).as[ApiResponse[String]] mustBe ApiResponse(envelopeId, Nil)
      }
    }
  }

  private val emp =
    Employment("company name", Some("888"), new LocalDate(2017, 5, 26), None, Nil, "", "", 2, Some(100), false, true)
}

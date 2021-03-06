/*
 * Copyright 2021 HM Revenue & Customs
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
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.http.{BadRequestException, InternalServerException, NotFoundException}
import uk.gov.hmrc.tai.model.api.ApiResponse
import uk.gov.hmrc.tai.model.domain.income.Live
import uk.gov.hmrc.tai.model.domain.{AddEmployment, Employment, EndEmployment, IncorrectEmployment}
import uk.gov.hmrc.tai.model.error.EmploymentNotFound
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.EmploymentService
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future
import scala.language.postfixOps

class EmploymentsControllerSpec extends BaseSpec {

  val emp =
    Employment(
      "company name",
      Live,
      Some("888"),
      new LocalDate(2017, 5, 26),
      None,
      Nil,
      "",
      "",
      2,
      Some(100),
      false,
      true)

  val mockEmploymentService: EmploymentService = mock[EmploymentService]

  val sut = new EmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate, cc)

  override protected def beforeEach(): Unit = {
    reset(mockEmploymentService)
    super.beforeEach()
  }

  "employments" must {
    "return Ok" when {
      "called with a valid nino and year" in {
        when(mockEmploymentService.employments(any(), any())(any()))
          .thenReturn(Future.successful(Nil))

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe OK
      }
    }
    "return a valid API json response" when {
      "called with a valid nino and year" in {
        when(mockEmploymentService.employments(any(), any())(any()))
          .thenReturn(Future.successful(List(emp)))

        val jsonResult = Json.obj(
          "data" -> Json.obj("employments" -> Json.arr(Json.obj(
            "name"                         -> "company name",
            "employmentStatus"             -> Live.toString,
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
        when(mockEmploymentService.employments(any(), any())(any()))
          .thenReturn(Future.failed(new NotFoundException("employment not found")))

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe NOT_FOUND
      }
      "the employments service returns a bad request exception" in {
        when(mockEmploymentService.employments(any(), any())(any()))
          .thenReturn(Future.failed(new BadRequestException("no employments recorded for this individual")))

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe BAD_REQUEST
        contentAsString(result) mustBe "no employments recorded for this individual"

      }
      "the employments service returns an error" in {
        when(mockEmploymentService.employments(any(), any())(any()))
          .thenReturn(Future.failed(new InternalServerException("employment service failed")))

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }
  }

  "employment" must {
    "return ok" when {
      "called with valid nino, year and id" in {
        when(mockEmploymentService.employment(any(), any())(any()))
          .thenReturn(Future.successful(Right(emp)))

        val result = sut.employment(nino, 2)(FakeRequest())

        val jsonResult = Json.obj(
          "data" -> Json.obj(
            "name"                         -> "company name",
            "employmentStatus"             -> Live.toString,
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

        verify(mockEmploymentService, times(1)).employment(any(), meq(2))(any())
      }
    }

    "return not found" when {
      "called with valid nino, year and id but id doesn't present" in {
        when(mockEmploymentService.employment(any(), any())(any()))
          .thenReturn(Future.successful(Left(EmploymentNotFound)))

        val result = sut.employment(nino, 3)(FakeRequest())

        status(result) mustBe NOT_FOUND
        verify(mockEmploymentService, times(1)).employment(any(), meq(3))(any())
      }

      "throw not found exception" in {
        when(mockEmploymentService.employment(any(), any())(any()))
          .thenReturn(Future.failed(new NotFoundException("")))

        val result = sut.employment(nino, 3)(FakeRequest())

        status(result) mustBe NOT_FOUND
        verify(mockEmploymentService, times(1)).employment(any(), meq(3))(any())
      }
    }

    "return internal server" when {
      "employment service throws an error" in {
        when(mockEmploymentService.employment(any(), any())(any()))
          .thenReturn(Future.failed(new InternalServerException("")))

        val result = sut.employment(nino, 3)(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        verify(mockEmploymentService, times(1)).employment(any(), meq(3))(any())
      }
    }
  }

  "endEmployment" must {
    "return an envelope Id" when {
      "given a valid request" in {
        val employment = EndEmployment(new LocalDate("2017-05-05"), "Yes", Some("123456789"))
        val json = Json.toJson(employment)
        val envelopeId = "EnvelopeId"

        when(mockEmploymentService.endEmployment(any(), any(), any())(any()))
          .thenReturn(Future.successful(envelopeId))

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

        when(mockEmploymentService.addEmployment(meq(nino), meq(employment))(any()))
          .thenReturn(Future.successful(envelopeId))

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

        when(mockEmploymentService.incorrectEmployment(meq(nino), meq(id), meq(employment))(any()))
          .thenReturn(Future.successful(envelopeId))

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

        when(
          mockEmploymentService
            .updatePreviousYearIncome(meq(nino), meq(taxYear), meq(employment))(any()))
          .thenReturn(Future.successful(envelopeId))

        val result = sut.updatePreviousYearIncome(nino, taxYear)(
          FakeRequest("POST", "/", FakeHeaders(), Json.toJson(employment))
            .withHeaders(("content-type", "application/json")))

        status(result) mustBe OK
        contentAsJson(result).as[ApiResponse[String]] mustBe ApiResponse(envelopeId, Nil)
      }
    }
  }
}

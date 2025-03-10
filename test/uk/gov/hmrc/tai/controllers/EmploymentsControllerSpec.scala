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

import cats.data.EitherT
import cats.instances.future.*
import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.http.{BadRequestException, NotFoundException, UpstreamErrorResponse}
import uk.gov.hmrc.tai.model.api.ApiResponse
import uk.gov.hmrc.tai.model.domain.*
import uk.gov.hmrc.tai.model.domain.income.Live
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.EmploymentService
import uk.gov.hmrc.tai.util.BaseSpec

import java.time.LocalDate
import scala.concurrent.Future

class EmploymentsControllerSpec extends BaseSpec {

  val emp: Employment =
    Employment(
      "company name",
      Live,
      Some("888"),
      LocalDate.of(2017, 5, 26),
      None,
      Nil,
      "",
      "",
      2,
      Some(100),
      hasPayrolledBenefit = false,
      receivingOccupationalPension = true
    )

  val mockEmploymentService: EmploymentService = mock[EmploymentService]

  val sut = new EmploymentsController(mockEmploymentService, loggedInAuthenticationAuthJourney, cc)

  override protected def beforeEach(): Unit = {
    reset(mockEmploymentService)
    super.beforeEach()
  }

  "employments" must {
    "return Ok" when {
      "called with a valid nino and year" in {
        when(mockEmploymentService.employmentsAsEitherT(any(), any())(any(), any()))
          .thenReturn(EitherT.rightT(Employments(Seq.empty, None)))

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe OK
      }
    }
    "return a valid API json response" when {
      "called with a valid nino and year" in {
        when(mockEmploymentService.employmentsAsEitherT(any(), any())(any(), any()))
          .thenReturn(EitherT.rightT(Employments(Seq(emp), None)))

        val jsonResult = Json.obj(
          "data" -> Json.obj(
            "employments" -> Json.arr(
              Json.obj(
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
              )
            )
          ),
          "links" -> Json.arr()
        )

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        contentAsJson(result) mustBe jsonResult
      }
    }
    "return a non success http response" when {
      "the employments service returns a not found UpstreamErrorResponse" in {
        when(mockEmploymentService.employmentsAsEitherT(any(), any())(any(), any()))
          .thenReturn(EitherT.leftT(UpstreamErrorResponse("Not found", NOT_FOUND)))

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe NOT_FOUND
      }

      "the employments service returns a not found exception" in {
        when(mockEmploymentService.employmentsAsEitherT(any(), any())(any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, Employments](Future.failed(new NotFoundException("message")))
          )

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe NOT_FOUND
      }

      "the employments service returns a bad request UpstreamErrorResponse" in {
        when(mockEmploymentService.employmentsAsEitherT(any(), any())(any(), any()))
          .thenReturn(EitherT.leftT(UpstreamErrorResponse("bad request", BAD_REQUEST)))

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe BAD_REQUEST
        contentAsString(result) mustBe "bad request"
      }

      "the employments service returns a bad request exception" in {
        when(mockEmploymentService.employmentsAsEitherT(any(), any())(any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, Employments](Future.failed(new BadRequestException("bad request")))
          )

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe BAD_REQUEST
        contentAsString(result) mustBe "bad request"
      }

      "the employments service returns a server error" in {
        when(mockEmploymentService.employmentsAsEitherT(any(), any())(any(), any()))
          .thenReturn(EitherT.leftT(UpstreamErrorResponse("server error", INTERNAL_SERVER_ERROR)))

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe BAD_GATEWAY
      }
      "the employments service returns other client errors" in {
        when(mockEmploymentService.employmentsAsEitherT(any(), any())(any(), any()))
          .thenReturn(EitherT.leftT(UpstreamErrorResponse("server error", LOCKED)))

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }
  }

  "employment" must {
    "return ok" when {
      "called with valid nino, year and id" in {
        when(mockEmploymentService.employmentAsEitherT(any(), any())(any(), any()))
          .thenReturn(EitherT.rightT(emp))

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

        verify(mockEmploymentService, times(1)).employmentAsEitherT(any(), meq(2))(any(), any())
      }
    }

    "return not found" when {
      "called with valid nino, year and id but id doesn't present" in {
        when(mockEmploymentService.employmentAsEitherT(any(), any())(any(), any()))
          .thenReturn(EitherT.leftT(UpstreamErrorResponse("not found", NOT_FOUND)))

        val result = sut.employment(nino, 3)(FakeRequest())

        status(result) mustBe NOT_FOUND
        verify(mockEmploymentService, times(1)).employmentAsEitherT(any(), meq(3))(any(), any())
      }

      "returns not found" in {
        when(mockEmploymentService.employmentAsEitherT(any(), any())(any(), any()))
          .thenReturn(EitherT.leftT(UpstreamErrorResponse("not found", NOT_FOUND)))

        val result = sut.employment(nino, 3)(FakeRequest())

        status(result) mustBe NOT_FOUND
        verify(mockEmploymentService, times(1)).employmentAsEitherT(any(), meq(3))(any(), any())
      }
    }

    "return internal server" when {
      "employment service returns a server error" in {
        when(mockEmploymentService.employmentAsEitherT(any(), any())(any(), any()))
          .thenReturn(EitherT.leftT(UpstreamErrorResponse("server error", INTERNAL_SERVER_ERROR)))

        val result = sut.employment(nino, 3)(FakeRequest())

        status(result) mustBe BAD_GATEWAY
        verify(mockEmploymentService, times(1)).employmentAsEitherT(any(), meq(3))(any(), any())
      }
      "employment service returns a client error" in {
        when(mockEmploymentService.employmentAsEitherT(any(), any())(any(), any()))
          .thenReturn(EitherT.leftT(UpstreamErrorResponse("server error", LOCKED)))

        val result = sut.employment(nino, 3)(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        verify(mockEmploymentService, times(1)).employmentAsEitherT(any(), meq(3))(any(), any())
      }
    }
  }

  "endEmployment" must {
    "return an envelope Id" when {
      "given a valid request" in {
        val employment = EndEmployment(LocalDate.parse("2017-05-05"), "Yes", Some("123456789"))
        val json = Json.toJson(employment)
        val envelopeId = "EnvelopeId"

        when(mockEmploymentService.endEmployment(any(), any(), any())(any(), any()))
          .thenReturn(EitherT.rightT(envelopeId))

        val result = sut.endEmployment(nino, 3)(
          FakeRequest("POST", "/", FakeHeaders(), json)
            .withHeaders(("content-type", "application/json"))
        )

        status(result) mustBe OK
        contentAsJson(result).as[ApiResponse[String]] mustBe ApiResponse(envelopeId, Nil)
      }
    }
  }

  "addEmployment" must {
    "return envelop Id" when {
      "called with valid add employment request" in {
        val envelopeId = "envelopId"
        val employment = AddEmployment("employerName", LocalDate.parse("2017-05-05"), "1234", "Yes", Some("123456789"))
        val json = Json.toJson(employment)

        when(mockEmploymentService.addEmployment(meq(nino), meq(employment))(any()))
          .thenReturn(Future.successful(envelopeId))

        val result = sut.addEmployment(nino)(
          FakeRequest("POST", "/", FakeHeaders(), json)
            .withHeaders(("content-type", "application/json"))
        )

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

        when(mockEmploymentService.incorrectEmployment(meq(nino), meq(id), meq(employment))(any(), any()))
          .thenReturn(Future.successful(envelopeId))

        val result = sut.incorrectEmployment(nino, id)(
          FakeRequest("POST", "/", FakeHeaders(), Json.toJson(employment))
            .withHeaders(("content-type", "application/json"))
        )

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
            .updatePreviousYearIncome(meq(nino), meq(taxYear), meq(employment))(any())
        )
          .thenReturn(Future.successful(envelopeId))

        val result = sut.updatePreviousYearIncome(nino, taxYear)(
          FakeRequest("POST", "/", FakeHeaders(), Json.toJson(employment))
            .withHeaders(("content-type", "application/json"))
        )

        status(result) mustBe OK
        contentAsJson(result).as[ApiResponse[String]] mustBe ApiResponse(envelopeId, Nil)
      }
    }
  }

  "employmentOnly" must {
    "return Ok when employment is found for the given nino, id, and tax year" in {
      when(mockEmploymentService.employmentWithoutRTIAsEitherT(any(), any(), any())(any(), any()))
        .thenReturn(EitherT.rightT(emp))

      val result = sut.employmentOnly(nino, 2, TaxYear("2017"))(FakeRequest())

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

      verify(mockEmploymentService, times(1))
        .employmentWithoutRTIAsEitherT(any(), meq(2), meq(TaxYear("2017")))(any(), any())
    }

    "return Not Found when employment does not exist" in {
      when(mockEmploymentService.employmentWithoutRTIAsEitherT(any(), any(), any())(any(), any()))
        .thenReturn(EitherT.leftT(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.employmentOnly(nino, 3, TaxYear("2017"))(FakeRequest())

      status(result) mustBe NOT_FOUND
      verify(mockEmploymentService, times(1))
        .employmentWithoutRTIAsEitherT(any(), meq(3), meq(TaxYear("2017")))(any(), any())
    }
  }

  "employmentsOnly" must {
    "return Ok when employments exist for the given nino and tax year" in {
      when(mockEmploymentService.employmentsWithoutRtiAsEitherT(any(), any())(any()))
        .thenReturn(EitherT.rightT(Employments(Seq(emp), None)))

      val result = sut.employmentsOnly(nino, TaxYear("2017"))(FakeRequest())

      val jsonResult = Json.obj(
        "data" -> Json.obj(
          "employments" -> Json.arr(
            Json.obj(
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
            )
          )
        ),
        "links" -> Json.arr()
      )

      status(result) mustBe OK
      contentAsJson(result) mustBe jsonResult

      verify(mockEmploymentService, times(1)).employmentsWithoutRtiAsEitherT(any(), meq(TaxYear("2017")))(any())
    }

    "return Not Found when employments do not exist" in {
      when(mockEmploymentService.employmentsWithoutRtiAsEitherT(any(), any())(any()))
        .thenReturn(EitherT.leftT(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.employmentsOnly(nino, TaxYear("2017"))(FakeRequest())

      status(result) mustBe NOT_FOUND
      verify(mockEmploymentService, times(1)).employmentsWithoutRtiAsEitherT(any(), meq(TaxYear("2017")))(any())
    }
  }
}

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

package uk.gov.hmrc.tai.controllers.employments

import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.mvc.Results.ServiceUnavailable
import play.api.test.Helpers._
import play.api.test.FakeRequest
import uk.gov.hmrc.http.{BadRequestException, InternalServerException, NotFoundException}
import uk.gov.hmrc.tai.config.FeatureTogglesConfig
import uk.gov.hmrc.tai.controllers.isolators.RtiIsolatorImpl
import uk.gov.hmrc.tai.controllers.predicates.AuthenticatedRequest
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model.domain.{Employment, OldEmployment}
import uk.gov.hmrc.tai.model.error.{EmploymentAccountStubbed, EmploymentNotFound}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.{EmploymentService, OldEmploymentService}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class OldEmploymentsControllerSpec extends PlaySpec with MockitoSugar with MockAuthenticationPredicate {

  def getRtiIsolator(isolate: Boolean = false) = {
    val mockConfig = mock[FeatureTogglesConfig]
    when(mockConfig.useRti).thenReturn(!isolate)
    new RtiIsolatorImpl(mockConfig)
  }

  "employments" must {
    "return Ok" when {
      "called with a valid nino and year" in {
        val mockEmploymentService = mock[OldEmploymentService]
        when(mockEmploymentService.employments(any(), any())(any()))
          .thenReturn(Future.successful(Nil))

        val sut = new OldEmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate, getRtiIsolator())

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe OK
      }
    }
    "return a valid API json response" when {
      "called with a valid nino and year" in {
        val mockEmploymentService = mock[OldEmploymentService]
        when(mockEmploymentService.employments(any(), any())(any()))
          .thenReturn(Future.successful(List(emp)))

        val sut = new OldEmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate, getRtiIsolator())

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
        val mockEmploymentService = mock[OldEmploymentService]
        when(mockEmploymentService.employments(any(), any())(any()))
          .thenReturn(Future.failed(new NotFoundException("employment not found")))

        val sut = new OldEmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate, getRtiIsolator())

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe NOT_FOUND
        contentAsString(result) mustBe "employment not found"
      }
      "the employments service returns a bad request exception" in {
        val mockEmploymentService = mock[OldEmploymentService]
        when(mockEmploymentService.employments(any(), any())(any()))
          .thenReturn(Future.failed(new BadRequestException("no employments recorded for this individual")))

        val sut = new OldEmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate, getRtiIsolator())

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe BAD_REQUEST
        contentAsString(result) mustBe "no employments recorded for this individual"

      }
      "the employments service returns an error" in {
        val mockEmploymentService = mock[OldEmploymentService]
        when(mockEmploymentService.employments(any(), any())(any()))
          .thenReturn(Future.failed(new InternalServerException("employment service failed")))

        val sut = new OldEmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate, getRtiIsolator())

        val result = sut.employments(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }
  }

  "employment" must {
    "return ok" when {
      "called with valid nino, year and id" in {
        val mockEmploymentService = mock[OldEmploymentService]
        when(mockEmploymentService.employment(any(), any())(any()))
          .thenReturn(Future.successful(Right(emp)))

        val sut = new OldEmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate, getRtiIsolator())
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
        val mockEmploymentService = mock[OldEmploymentService]
        when(mockEmploymentService.employment(any(), any())(any()))
          .thenReturn(Future.successful(Left("Not Found")))

        val sut = new OldEmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate, getRtiIsolator())
        val result = sut.employment(nino, 3)(FakeRequest())

        status(result) mustBe NOT_FOUND
        verify(mockEmploymentService, times(1)).employment(any(), Matchers.eq(3))(any())
      }

      "throw not found exception" in {
        val mockEmploymentService = mock[OldEmploymentService]
        when(mockEmploymentService.employment(any(), any())(any()))
          .thenReturn(Future.failed(new NotFoundException("")))

        val sut = new OldEmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate, getRtiIsolator())
        val result = sut.employment(nino, 3)(FakeRequest())

        status(result) mustBe NOT_FOUND
        verify(mockEmploymentService, times(1)).employment(any(), Matchers.eq(3))(any())
      }
    }

    "return internal server" when {
      "employment service throws an error" in {
        val mockEmploymentService = mock[OldEmploymentService]
        when(mockEmploymentService.employment(any(), any())(any()))
          .thenReturn(Future.failed(new InternalServerException("")))

        val sut = new OldEmploymentsController(mockEmploymentService, loggedInAuthenticationPredicate, getRtiIsolator())
        val result = sut.employment(nino, 3)(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        verify(mockEmploymentService, times(1)).employment(any(), Matchers.eq(3))(any())
      }
    }
  }

  private val emp =
    OldEmployment("company name", Some("888"), new LocalDate(2017, 5, 26), None, Nil, "", "", 2, Some(100), false, true)

}

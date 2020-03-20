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
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.FakeRequest
import play.api.test.Helpers.status
import uk.gov.hmrc.tai.config.FeatureTogglesConfig
import uk.gov.hmrc.tai.controllers.isolators.RtiIsolatorImpl
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.{AnnualAccountService, EmploymentService}
import play.api.test.Helpers._

import scala.language.postfixOps
import org.scalatest.Matchers._
import uk.gov.hmrc.tai.model.domain.{AnnualAccount, Available, Employment}
import uk.gov.hmrc.tai.model.error.EmploymentNotFound

import scala.concurrent.Future

class AnnualAccountControllerSpec extends PlaySpec with MockitoSugar with MockAuthenticationPredicate {

  def getRtiIsolator(isolate: Boolean = false) = {
    val mockConfig = mock[FeatureTogglesConfig]
    when(mockConfig.useRti).thenReturn(!isolate)
    new RtiIsolatorImpl(mockConfig)
  }

  def buildAnnualAccount = AnnualAccount("k-e-y", TaxYear(2017), Available, Nil, Nil)
  def buildEmployment = Employment("name", Some("y"), LocalDate.now(), None, "k", "e", 1, None, false, false)

  "getAccountsForEmployment" must {
    "return OK" when {
      "called with a valid nino, employmentId and taxyear" in {
        val mockAnnualAccountService = mock[AnnualAccountService]
        when(mockAnnualAccountService.annualAccounts(Matchers.any(), Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(Seq(buildAnnualAccount)))
        val mockEmploymentService = mock[EmploymentService]
        when(mockEmploymentService.employment(Matchers.any(), Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(Right(buildEmployment)))
        val sut = new AnnualAccountController(
          mockAnnualAccountService,
          mockEmploymentService,
          loggedInAuthenticationPredicate,
          getRtiIsolator())
        val result = sut.getAccountsForEmployment(nino, TaxYear(2017), 1)(FakeRequest())

        status(result) mustBe OK
      }
    }

    "return Not Found" when {
      "no employment exists with the employmentId provided" in {
        val mockEmploymentService = mock[EmploymentService]
        when(mockEmploymentService.employment(Matchers.any(), Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(Left(EmploymentNotFound)))
        val mockAnnualAccountService = mock[AnnualAccountService]
        when(mockAnnualAccountService.annualAccounts(Matchers.any(), Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(Seq(buildAnnualAccount)))
        val sut = new AnnualAccountController(
          mockAnnualAccountService,
          mockEmploymentService,
          loggedInAuthenticationPredicate,
          getRtiIsolator())
        val result = sut.getAccountsForEmployment(nino, TaxYear(2017), 1)(FakeRequest())

        status(result) mustBe NOT_FOUND
      }
    }

    "return ServiceUnavailiable" when {
      "Rti is disabled" in {
        val sut = new AnnualAccountController(
          mock[AnnualAccountService],
          mock[EmploymentService],
          loggedInAuthenticationPredicate,
          getRtiIsolator(true))
        val result = sut.getAccountsForEmployment(nino, TaxYear("2017"), 1)(FakeRequest())
        status(result) mustBe SERVICE_UNAVAILABLE
      }
    }
  }

  "getAccounts" must {
    "return OK" when {
      "called with a valid nino, and taxYear" in {
        val mockAnnualAccountService = mock[AnnualAccountService]
        when(mockAnnualAccountService.annualAccounts(Matchers.any(), Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(Seq(buildAnnualAccount)))
        val sut = new AnnualAccountController(
          mockAnnualAccountService,
          mock[EmploymentService],
          loggedInAuthenticationPredicate,
          getRtiIsolator())
        val result = sut.getAccounts(nino, TaxYear(2017))(FakeRequest())

        status(result) mustBe OK
      }
    }

    "return ServiceUnavailiable" when {
      "Rti is disabled" in {
        val sut = new AnnualAccountController(
          mock[AnnualAccountService],
          mock[EmploymentService],
          loggedInAuthenticationPredicate,
          getRtiIsolator(true))
        val result = sut.getAccounts(nino, TaxYear("2017"))(FakeRequest())
        status(result) mustBe SERVICE_UNAVAILABLE
      }
    }
  }
}

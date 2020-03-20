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

import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.model.domain.{AnnualAccount, Employment, OldEmployment, Unavailable}
import uk.gov.hmrc.tai.model.error.EmploymentNotFound
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.{AnnualAccountRepository, EmploymentRepository, PersonRepository}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

class OldEmploymentServiceSpec extends PlaySpec with MockitoSugar {

  "OldEmploymentService" should {
    "return employments for passed nino and year" in {
      val employmentsForYear = List(employment)

      val mockEmploymentRepository = mock[EmploymentRepository]
      when(mockEmploymentRepository.employmentsForYear(any(), any())(any()))
        .thenReturn(Future.successful(employmentsForYear))

      val mockAnnualAccountRepository = mock[AnnualAccountRepository]
      when(mockAnnualAccountRepository.annualAccounts(any(), any())(any()))
        .thenReturn(Future.successful(Seq(annualAccount)))

      val sut = createSut(mockEmploymentRepository, mockAnnualAccountRepository)

      val employments = Await.result(sut.employments(nino, TaxYear())(HeaderCarrier()), 5 seconds)

      employments mustBe List(oldEmployment)
    }

    "return employment for passed nino, year and id" in {
      val mockEmploymentRepository = mock[EmploymentRepository]
      when(mockEmploymentRepository.employmentsForYear(any(), any())(any()))
        .thenReturn(Future.successful(List(employment)))

      val mockAnnualAccountRepository = mock[AnnualAccountRepository]
      when(mockAnnualAccountRepository.annualAccounts(any(), any())(any()))
        .thenReturn(Future.successful(Seq(annualAccount)))

      val sut = createSut(mockEmploymentRepository, mockAnnualAccountRepository)
      val employments = Await.result(sut.employment(nino, 2)(HeaderCarrier()), 5.seconds)

      employments mustBe Right(oldEmployment)
    }

    "return the correct Error Type when the employment doesn't exist" in {
      val mockEmploymentRepository = mock[EmploymentRepository]
      when(mockEmploymentRepository.employmentsForYear(any(), any())(any()))
        .thenReturn(Future.successful(Nil))

      val mockAnnualAccountRepository = mock[AnnualAccountRepository]
      when(mockAnnualAccountRepository.annualAccounts(any(), any())(any()))
        .thenReturn(Future.successful(Seq(annualAccount)))

      val sut = createSut(mockEmploymentRepository, mockAnnualAccountRepository)
      val employments = Await.result(sut.employment(nino, 5)(HeaderCarrier()), 5.seconds)

      employments mustBe Left("EmploymentNotFound")
    }
  }

  private val nino = new Generator(new Random).nextNino

  private val annualAccount = AnnualAccount("--12345", TaxYear(2019), Unavailable, Nil, Nil)

  private val employment = Employment("TEST", Some("12345"), LocalDate.now(), None, "", "", 2, Some(100), false, false)

  private val oldEmployment = OldEmployment(annualAccount, employment)

  private def createSut(employmentRepository: EmploymentRepository, accountRepository: AnnualAccountRepository) =
    new OldEmploymentService(employmentRepository, accountRepository)
}

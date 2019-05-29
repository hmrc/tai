/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.tai.controllers.benefits

import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.auth.core.MissingBearerToken
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model.domain.benefits.{CompanyCar, CompanyCarBenefit, WithdrawCarAndFuel}
import uk.gov.hmrc.tai.service.benefits.BenefitsService

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Random

class CompanyCarBenefitControllerSpec
  extends PlaySpec
    with MockitoSugar
    with MockAuthenticationPredicate {

  "companyCarBenefits" must {

    "return NOT AUTHORISED" when {
      "the user is not logged in" in {
        val nino = randomNino
        val sut = new CompanyCarBenefitController(mock[BenefitsService], notLoggedInAuthenticationPredicate, cc)
        val result = sut.companyCarBenefits(nino)(FakeRequest())

        ScalaFutures.whenReady(result.failed) { e =>
          e mustBe a[MissingBearerToken]
        }
      }
    }
    "return NotFound" when {
      "company car benefit service returns Nil" in {
        val mockCompanyCarService = mock[BenefitsService]
        when(mockCompanyCarService.companyCarBenefits(any())(any()))
          .thenReturn(Future.successful(Nil))

        val sut = new CompanyCarBenefitController(mockCompanyCarService, loggedInAuthenticationPredicate, cc)
        val result = sut.companyCarBenefits(randomNino)(FakeRequest())
        status(result) mustBe NOT_FOUND
      }
    }

    "return sequence of company car benefit" when {
      "company car benefit service returns a sequence of company car benefit with no fuel benefits" in {
        val companyCarSeq = Seq(
          CompanyCarBenefit(
            10,
            1000,
            Seq(CompanyCar(10, "Company car", hasActiveFuelBenefit = false, Some(LocalDate.parse("2014-06-10")), None, None)),
            sampleVersion))

        val mockCompanyCarService = mock[BenefitsService]
        when(mockCompanyCarService.companyCarBenefits(any())(any()))
          .thenReturn(Future.successful(companyCarSeq))

        val sut = new CompanyCarBenefitController(mockCompanyCarService, loggedInAuthenticationPredicate, cc)
        val result = sut.companyCarBenefits(randomNino)(FakeRequest())

        status(result) mustBe OK
        val expectedJson =
          Json.obj(
            "data" -> Json.obj(
              "companyCarBenefits" -> Json.arr(
                Json.obj(
                  "employmentSeqNo" -> 10,
                  "grossAmount" -> 1000,
                  "companyCars" -> Json.arr(
                    Json.obj(
                      "carSeqNo" -> 10,
                      "makeModel" -> "Company car",
                      "hasActiveFuelBenefit" -> false,
                      "dateMadeAvailable" -> "2014-06-10")),
                  "version" -> 1))),
            "links" -> Json.arr())

        contentAsJson(result) mustBe expectedJson
      }

      "company car benefit service returns a sequence of company car benefit with a fuel benefit" in {
        val companyCarSeq = Seq(
          CompanyCarBenefit(
            10,
            1000,
            Seq(CompanyCar(10, "Company car", hasActiveFuelBenefit = true, Some(LocalDate.parse("2014-06-10")),Some(LocalDate.parse("2014-06-10")), None)),
            sampleVersion))

        val mockCompanyCarService = mock[BenefitsService]
        when(mockCompanyCarService.companyCarBenefits(any())(any()))
          .thenReturn(Future.successful(companyCarSeq))

        val sut = new CompanyCarBenefitController(mockCompanyCarService, loggedInAuthenticationPredicate, cc)
        val result = sut.companyCarBenefits(randomNino)(FakeRequest())

        status(result) mustBe OK

        val expectedJson = Json.obj(
          "data"-> Json.obj(
            "companyCarBenefits"-> Json.arr(
              Json.obj(
                "employmentSeqNo" -> 10,
                "grossAmount"->1000,
                "companyCars"-> Json.arr(
                  Json.obj(
                    "carSeqNo"->10,
                    "makeModel"->"Company car",
                    "hasActiveFuelBenefit"-> true,
                    "dateMadeAvailable"->"2014-06-10",
                    "dateActiveFuelBenefitMadeAvailable"->"2014-06-10")),
                "version" -> 1))),
          "links" -> Json.arr())

        contentAsJson(result) mustBe expectedJson

      }
    }
  }
  "companyCarBenefitForEmployment" must {
    "return NOT AUTHORISED" when {
      "the user is not logged in" in {
        val nino = randomNino
        val sut = new CompanyCarBenefitController(mock[BenefitsService], notLoggedInAuthenticationPredicate, cc)
        val result = sut.companyCarBenefitForEmployment(nino, employmentSeqNum)(FakeRequest())

        ScalaFutures.whenReady(result.failed) { e =>
          e mustBe a[MissingBearerToken]
        }
      }
    }
    "return NotFound" when {
      "company car benefit service returns Nil" in {
        val mockCompanyCarService = mock[BenefitsService]
        when(mockCompanyCarService.companyCarBenefitForEmployment(any(), any())(any()))
          .thenReturn(Future.successful(None))

        val sut = new CompanyCarBenefitController(mockCompanyCarService, loggedInAuthenticationPredicate, cc)
        val result = sut.companyCarBenefitForEmployment(randomNino, employmentSeqNum)(FakeRequest())

        status(result) mustBe NOT_FOUND
      }
    }

    "filter company car benefits and return one with a matching employment sequence number" when {
      "company car benefit service returns a sequence of company car benefit with a matching employment sequence number" in {
        val companyCarBenefit = CompanyCarBenefit(
          10,
          1000,
          Seq(CompanyCar(10, "Company car", hasActiveFuelBenefit = false, Some(LocalDate.parse("2014-06-10")),None, None)),
          sampleVersion)

        val mockCompanyCarService = mock[BenefitsService]
        when(mockCompanyCarService.companyCarBenefitForEmployment(any(), any())(any()))
          .thenReturn(Future.successful(Some(companyCarBenefit)))

        val sut = new CompanyCarBenefitController(mockCompanyCarService, loggedInAuthenticationPredicate, cc)
        val result = sut.companyCarBenefitForEmployment(randomNino, employmentSeqNum)(FakeRequest())

        status(result) mustBe OK
        val expectedJson =
          Json.obj(
            "data" -> Json.obj(
              "employmentSeqNo" -> 10,
              "grossAmount" -> 1000,
              "companyCars" -> Json.arr(
                Json.obj(
                  "carSeqNo" -> 10,
                  "makeModel" -> "Company car",
                  "hasActiveFuelBenefit" -> false,
                  "dateMadeAvailable" -> "2014-06-10")),
              "version" -> 1),
            "links" -> Json.arr())

        contentAsJson(result) mustBe expectedJson
      }
    }
  }

  "removeCompanyCarAndFuel" must {
    "return NOT AUTHORISED" when {
      "the user is not logged in" in {
        val nino = randomNino
        val sut = new CompanyCarBenefitController(mock[BenefitsService], notLoggedInAuthenticationPredicate, cc)
        val result = sut.withdrawCompanyCarAndFuel(nino, employmentSeqNum, 1)(FakeRequest("POST", "/", FakeHeaders(), Json.toJson("")).
          withHeaders(("content-type", "application/json")))

        ScalaFutures.whenReady(result.failed) { e =>
          e mustBe a[MissingBearerToken]
        }
      }
    }

    "return OK when called with correct parameters" in{
      val carWithdrawDate = new LocalDate(2017, 4, 24)
      val fuelWithdrawDate = Some(new LocalDate(2017, 4, 24))
      val nino = randomNino
      val carSeqNum = 10
      val employmentSeqNum = 11
      val removeCarAndFuel = WithdrawCarAndFuel(10, carWithdrawDate, fuelWithdrawDate)
      val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(removeCarAndFuel))
        .withHeaders(("content-type", "application/json"))

      val mockCompanyCarService = mock[BenefitsService]
      when(mockCompanyCarService.withdrawCompanyCarAndFuel(Matchers.eq(nino),Matchers.eq(employmentSeqNum),
        Matchers.eq(carSeqNum), Matchers.eq(removeCarAndFuel))(any())).thenReturn(Future.successful("123456"))

      val sut = new CompanyCarBenefitController(mockCompanyCarService, loggedInAuthenticationPredicate, cc)
      val result = sut.withdrawCompanyCarAndFuel(nino,employmentSeqNum, carSeqNum)(fakeRequest)

      status(result) mustBe OK
    }
  }

  def randomNino = new Generator(new Random).nextNino

  def employmentSeqNum = 10
  val sampleVersion = Some(1)

  private implicit val hc = HeaderCarrier(sessionId = Some(SessionId("TEST")))

}

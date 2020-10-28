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

package uk.gov.hmrc.tai.controllers.benefits

import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import play.api.libs.json.Json
import play.api.test.Helpers.{status, _}
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.tai.model.api.ApiResponse
import uk.gov.hmrc.tai.model.domain.benefits._
import uk.gov.hmrc.tai.model.domain.{Accommodation, Assets}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.benefits.BenefitsService
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future
import scala.util.Random

class BenefitsControllerSpec extends BaseSpec {

  "benefits" must {
    "return Benefits case class with empty lists" when {
      "benefit service returns benefits case class with empty lists" in {
        val nino = randomNino
        val mockBenefitService = mock[BenefitsService]
        val emptyBenefits = Benefits(Seq(), Seq())
        when(mockBenefitService.benefits(any(), any())(any()))
          .thenReturn(Future.successful(emptyBenefits))

        val sut = new BenefitsController(mockBenefitService, loggedInAuthenticationPredicate, cc)
        val result = sut.benefits(randomNino, TaxYear())(FakeRequest())
        status(result) mustBe OK
        val expectedJson =
          Json.obj(
            "data" -> Json.obj(
              "companyCarBenefits" -> Json.arr(),
              "otherBenefits"      -> Json.arr()
            ),
            "links" -> Json.arr())

        contentAsJson(result) mustBe expectedJson
      }
    }

    "return Benefits case class with carBenefits and other benefits" when {
      "benefit service returns car benefits and other benefits" in {
        val mockBenefitService = mock[BenefitsService]
        val carBenefits = Seq(
          CompanyCarBenefit(
            12,
            200,
            Seq(
              CompanyCar(
                10,
                "Company car",
                hasActiveFuelBenefit = false,
                Some(LocalDate.parse("2014-06-10")),
                None,
                None)),
            Some(123)),
          CompanyCarBenefit(0, 800, Seq(), None)
        )
        val otherBenefits = Seq(
          GenericBenefit(Accommodation, Some(126), 111),
          GenericBenefit(Assets, None, 222)
        )
        val benefits = Benefits(carBenefits, otherBenefits)

        when(mockBenefitService.benefits(any(), any())(any()))
          .thenReturn(Future.successful(benefits))

        val sut = new BenefitsController(mockBenefitService, loggedInAuthenticationPredicate, cc)
        val result = sut.benefits(randomNino, TaxYear())(FakeRequest())
        status(result) mustBe OK
        val expectedJson =
          Json.obj(
            "data" -> Json.obj(
              "companyCarBenefits" -> Json.arr(
                Json.obj(
                  "employmentSeqNo" -> 12,
                  "grossAmount"     -> 200,
                  "companyCars" -> Json.arr(
                    Json.obj(
                      "carSeqNo"             -> 10,
                      "makeModel"            -> "Company car",
                      "hasActiveFuelBenefit" -> false,
                      "dateMadeAvailable"    -> "2014-06-10")),
                  "version" -> 123
                ),
                Json.obj(
                  "employmentSeqNo" -> 0,
                  "grossAmount"     -> 800,
                  "companyCars"     -> Json.arr()
                )
              ),
              "otherBenefits" -> Json.arr(
                Json.obj(
                  "benefitType"  -> "Accommodation",
                  "employmentId" -> 126,
                  "amount"       -> 111
                ),
                Json.obj(
                  "benefitType" -> "Assets",
                  "amount"      -> 222
                )
              )
            ),
            "links" -> Json.arr()
          )

        contentAsJson(result) mustBe expectedJson
      }
    }
  }

  "removeCompanyBenefits" must {
    "return an envelope Id" when {
      "called with valid remove company benefit request" in {
        val envelopeId = "envelopeId"
        val removeCompanyBenefit =
          RemoveCompanyBenefit("Mileage", "On Or After 6 April 2017", Some("1200"), "Yes", Some("123456789"))
        val nino = randomNino
        val employmentId = 1

        val mockBenefitService = mock[BenefitsService]
        when(
          mockBenefitService.removeCompanyBenefits(
            Matchers.eq(nino),
            Matchers.eq(employmentId),
            Matchers.eq(removeCompanyBenefit))(any())).thenReturn(Future.successful(envelopeId))

        val sut = new BenefitsController(mockBenefitService, loggedInAuthenticationPredicate, cc)
        val result = sut.removeCompanyBenefits(nino, employmentId)(
          FakeRequest("POST", "/", FakeHeaders(), Json.toJson(removeCompanyBenefit))
            .withHeaders(("content-type", "application/json")))

        status(result) mustBe OK
        contentAsJson(result).as[ApiResponse[String]] mustBe ApiResponse(envelopeId, Nil)
      }
    }
  }

  def randomNino = new Generator(new Random).nextNino
}

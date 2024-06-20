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

package uk.gov.hmrc.tai.controllers.benefits

import java.time.LocalDate
import org.mockito.ArgumentMatchers.any
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.FakeRequest
import uk.gov.hmrc.tai.model.domain.benefits.{CompanyCar, CompanyCarBenefit}
import uk.gov.hmrc.tai.service.benefits.BenefitsService
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class CompanyCarBenefitControllerSpec extends BaseSpec {

  "companyCarBenefits" must {
    "return NotFound" when {
      "company car benefit service returns Nil" in {
        val mockCompanyCarService = mock[BenefitsService]
        when(mockCompanyCarService.companyCarBenefits(any())(any()))
          .thenReturn(Future.successful(Nil))

        val sut = new CompanyCarBenefitController(mockCompanyCarService, loggedInAuthenticationPredicate, cc)
        val result = sut.companyCarBenefits(nino)(FakeRequest())
        status(result) mustBe NOT_FOUND
      }
    }

    "return sequence of company car benefit" when {
      "company car benefit service returns a sequence of company car benefit with no fuel benefits" in {
        val companyCarSeq = Seq(
          CompanyCarBenefit(
            10,
            1000,
            Seq(
              CompanyCar(
                10,
                "Company car",
                hasActiveFuelBenefit = false,
                Some(LocalDate.parse("2014-06-10")),
                None,
                None
              )
            ),
            sampleVersion
          )
        )

        val mockCompanyCarService = mock[BenefitsService]
        when(mockCompanyCarService.companyCarBenefits(any())(any()))
          .thenReturn(Future.successful(companyCarSeq))

        val sut = new CompanyCarBenefitController(mockCompanyCarService, loggedInAuthenticationPredicate, cc)
        val result = sut.companyCarBenefits(nino)(FakeRequest())

        status(result) mustBe OK
        val expectedJson =
          Json.obj(
            "data" -> Json.obj(
              "companyCarBenefits" -> Json.arr(
                Json.obj(
                  "employmentSeqNo" -> 10,
                  "grossAmount"     -> 1000,
                  "companyCars" -> Json.arr(
                    Json.obj(
                      "carSeqNo"             -> 10,
                      "makeModel"            -> "Company car",
                      "hasActiveFuelBenefit" -> false,
                      "dateMadeAvailable"    -> "2014-06-10"
                    )
                  ),
                  "version" -> 1
                )
              )
            ),
            "links" -> Json.arr()
          )

        contentAsJson(result) mustBe expectedJson
      }

      "company car benefit service returns a sequence of company car benefit with a fuel benefit" in {
        val companyCarSeq = Seq(
          CompanyCarBenefit(
            10,
            1000,
            Seq(
              CompanyCar(
                10,
                "Company car",
                hasActiveFuelBenefit = true,
                Some(LocalDate.parse("2014-06-10")),
                Some(LocalDate.parse("2014-06-10")),
                None
              )
            ),
            sampleVersion
          )
        )

        val mockCompanyCarService = mock[BenefitsService]
        when(mockCompanyCarService.companyCarBenefits(any())(any()))
          .thenReturn(Future.successful(companyCarSeq))

        val sut = new CompanyCarBenefitController(mockCompanyCarService, loggedInAuthenticationPredicate, cc)
        val result = sut.companyCarBenefits(nino)(FakeRequest())

        status(result) mustBe OK

        val expectedJson = Json.obj(
          "data" -> Json.obj(
            "companyCarBenefits" -> Json.arr(
              Json.obj(
                "employmentSeqNo" -> 10,
                "grossAmount"     -> 1000,
                "companyCars" -> Json.arr(
                  Json.obj(
                    "carSeqNo"                           -> 10,
                    "makeModel"                          -> "Company car",
                    "hasActiveFuelBenefit"               -> true,
                    "dateMadeAvailable"                  -> "2014-06-10",
                    "dateActiveFuelBenefitMadeAvailable" -> "2014-06-10"
                  )
                ),
                "version" -> 1
              )
            )
          ),
          "links" -> Json.arr()
        )

        contentAsJson(result) mustBe expectedJson

      }
    }
  }

  def employmentSeqNum = 10
  val sampleVersion = Some(1)
}

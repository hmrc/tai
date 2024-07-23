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

package uk.gov.hmrc.tai.model.domain.formatters

import java.time.LocalDate
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsResultException, Json}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.model.domain.benefits.CompanyCar.companyCarReadsFromHod
import uk.gov.hmrc.tai.model.domain.benefits.CompanyCarBenefit.companyCarBenefitReadsFromHod
import uk.gov.hmrc.tai.model.domain.benefits.{CompanyCar, CompanyCarBenefit}

import scala.util.Random

class CompanyCarBenefitFormattersSpec extends PlaySpec {

  "companyCarBenefitReads" must {
    "be able to read correct companyCarBenefit json" when {
      "there is a list of company cars but no fuel benefit" in {
        val json =
          Json.obj(
            "employmentSequenceNumber" -> 1,
            "grossAmount"              -> 3333,
            "carDetails" -> Json.arr(
              Json.obj("carSequenceNumber" -> 24, "makeModel" -> "Company car", "dateMadeAvailable" -> "2014-06-10")
            )
          )

        val companyCarSeq = Seq(CompanyCar(24, "Company car", false, Some(LocalDate.parse("2014-06-10")), None, None))
        json.as[CompanyCarBenefit](companyCarBenefitReadsFromHod) mustBe CompanyCarBenefit(1, 3333, companyCarSeq)
      }

      "there is a list of company cars and an inactive fuel benefit" in {
        val json =
          Json.obj(
            "employmentSequenceNumber" -> 1,
            "grossAmount"              -> 3333,
            "carDetails" -> Json.arr(
              Json.obj(
                "carSequenceNumber" -> 24,
                "makeModel"         -> "Company car",
                "dateMadeAvailable" -> "2014-06-10",
                "fuelBenefit" -> Json.obj(
                  "benefitAmount"     -> 500,
                  "dateMadeAvailable" -> "2014-06-10",
                  "dateWithdrawn"     -> "2017-06-02",
                  "actions"           -> Json.obj("foo" -> "bar")
                )
              )
            )
          )

        val companyCarSeq = Seq(CompanyCar(24, "Company car", false, Some(LocalDate.parse("2014-06-10")), None, None))
        json.as[CompanyCarBenefit](companyCarBenefitReadsFromHod) mustBe
          CompanyCarBenefit(1, 3333, companyCarSeq)
      }

      "there is a list of company cars and an active fuel benefit" in {
        val json =
          Json.obj(
            "employmentSequenceNumber" -> 1,
            "grossAmount"              -> 3333,
            "carDetails" -> Json.arr(
              Json.obj(
                "carSequenceNumber" -> 24,
                "makeModel"         -> "Company car",
                "dateMadeAvailable" -> "2014-06-10",
                "fuelBenefit" -> Json.obj(
                  "dateMadeAvailable" -> "2017-05-02"
                )
              )
            )
          )

        val companyCarSeq = Seq(
          CompanyCar(
            24,
            "Company car",
            true,
            Some(LocalDate.parse("2014-06-10")),
            Some(LocalDate.parse("2017-05-02")),
            None
          )
        )
        json.as[CompanyCarBenefit](companyCarBenefitReadsFromHod) mustBe
          CompanyCarBenefit(1, 3333, companyCarSeq)
      }

      "be able to read correct companyCarBenefit json when company cars is an empty list" in {
        val json =
          Json.obj("employmentSequenceNumber" -> 1, "grossAmount" -> 3333, "carDetails" -> Json.arr())

        json.as[CompanyCarBenefit](companyCarBenefitReadsFromHod) mustBe CompanyCarBenefit(1, 3333, Nil)
      }
    }

    "throw an error" when {
      "employmentSequenceNumber is missing" in {
        val json = Json.obj("grossAmount" -> 3333)

        val result = the[JsResultException] thrownBy json.as[CompanyCarBenefit](companyCarBenefitReadsFromHod)
        result.getMessage must include("'employmentSequenceNumber' is undefined")
      }
      "grossAmount is missing" in {
        val json = Json.obj("employmentSequenceNumber" -> 1)

        val result = the[JsResultException] thrownBy json.as[CompanyCarBenefit](companyCarBenefitReadsFromHod)
        result.getMessage must include("'grossAmount' is undefined")
      }
      "car details is missing" in {
        val json = Json.obj("employmentSequenceNumber" -> 1, "grossAmount" -> 3333)

        val result = the[JsResultException] thrownBy json.as[CompanyCarBenefit](companyCarBenefitReadsFromHod)
        result.getMessage must include("'carDetails' is undefined")
      }
    }
  }

  "companyCarReads" must {
    "read correct Company Car json" when {
      "there is a valid make model with only mandatory fields complete" in {
        val json = Json.obj("carSequenceNumber" -> 24, "makeModel" -> "Company car")

        json.as[CompanyCar](companyCarReadsFromHod) mustBe CompanyCar(24, "Company car", false, None, None, None)
      }
      "there is a valid response with active fuel benefit" in {
        val json =
          Json.obj(
            "carSequenceNumber" -> 24,
            "makeModel"         -> "Company car",
            "dateMadeAvailable" -> "2013-06-10",
            "fuelBenefit" -> Json.obj(
              "dateMadeAvailable" -> "2014-06-10",
              "benefitAmount"     -> 2222,
              "actions"           -> Json.obj("remove" -> s"/paye/${nino.nino}/benefits/2017/2/car/1/fuel/remove")
            )
          )

        json.as[CompanyCar](companyCarReadsFromHod) mustBe CompanyCar(
          24,
          "Company car",
          true,
          Some(LocalDate.parse("2013-06-10")),
          Some(LocalDate.parse("2014-06-10")),
          None
        )
      }
      "there is a valid response with car benefit withdrawn date" in {
        val json =
          Json.obj(
            "carSequenceNumber" -> 24,
            "makeModel"         -> "Company car",
            "dateMadeAvailable" -> "2013-06-10",
            "dateWithdrawn"     -> "2014-06-16",
            "fuelBenefit" -> Json.obj(
              "dateMadeAvailable" -> "2014-06-10",
              "dateWithdrawn"     -> "2014-06-14",
              "benefitAmount"     -> 2222,
              "actions"           -> Json.obj("remove" -> s"/paye/${nino.nino}/benefits/2017/2/car/1/fuel/remove")
            )
          )

        json.as[CompanyCar](companyCarReadsFromHod) mustBe CompanyCar(
          24,
          "Company car",
          false,
          Some(LocalDate.parse("2013-06-10")),
          None,
          Some(LocalDate.parse("2014-06-16"))
        )
      }
    }

    "throw an error" when {
      "makeModel is missing" in {
        val json = Json.obj("carSequenceNumber" -> 24)

        val result = the[JsResultException] thrownBy json.as[CompanyCar](companyCarReadsFromHod)
        result.getMessage must include("'makeModel' is undefined")
      }
      "carSequenceNumber is missing" in {
        val json = Json.obj("makeModel" -> "Company car")
        val result = the[JsResultException] thrownBy json.as[CompanyCar](companyCarReadsFromHod)
        result.getMessage must include("'carSequenceNumber' is undefined")
      }
    }
  }

  private val nino: Nino = new Generator(new Random).nextNino
}

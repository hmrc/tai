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

package uk.gov.hmrc.tai.model.domain.income

import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import uk.gov.hmrc.tai.model.domain._

import scala.io.Source

class TaxCodeIncomeHipReadsSpec extends PlaySpec {
  private val basePath = "test/resources/data/TaxAccount/TaxCodeIncome/hip/"
  private def readFile(fileName: String): JsObject = {
    val jsonFilePath = basePath + fileName
    val bufferedSource = Source.fromFile(jsonFilePath)
    val source = bufferedSource.mkString("")
    bufferedSource.close()
    Json.parse(source).as[JsObject]
  }

  "taxCodeIncomeSourcesRead should use totalTaxableIncome to read amount" must {
    "return a value for the total taxable income from NPS tax response" when {

      "estimated pay is available in iabds" in {
        val payload = readFile("TC01.json")
        val result =
          payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads)
        result.head.amount mustBe 111

      }

      "estimated pay is available in iabds for the correct employment id" in {
        val payload = readFile("TC02.json")
        val result = payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads)
        result.head.amount mustBe 1111
      }
    }

    "return 0 as amount" when {

      "pay and tax is not available" in {

        val payload = readFile("TC03.json")
        val result = payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads)
        result.head.amount mustBe 0
      }

      "employmentId does not match" in {

        val payload = readFile("TC04.json")
        val result = payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads)
        result.head.amount mustBe 0
      }
    }

  }

  "taxCodeIncomeSourceReads" must {
    "read taxCodeIncome" when {
      "all income source indicators are false" in {
        val payload = readFile("TC05.json")
        val result = payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads)
        result mustBe Seq(
          TaxCodeIncome(
            EmploymentIncome,
            Some(1),
            BigDecimal(1111),
            "EmploymentIncome",
            "1150L",
            "Employer1",
            Week1Month1BasisOperation,
            Live,
            BigDecimal(0),
            BigDecimal(0),
            BigDecimal(0)
          )
        )
      }

      "pension indicator is true" in {
        val payload = readFile("TC06.json")
        val result = payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads)

        result mustBe Seq(
          TaxCodeIncome(
            PensionIncome,
            Some(1),
            BigDecimal(1111),
            "PensionIncome",
            "1150L",
            "PensionProvider1",
            OtherBasisOperation,
            Live,
            BigDecimal(0),
            BigDecimal(0),
            BigDecimal(0)
          )
        )
      }

      "jobSeekers indicator is true" in {
        val payload = readFile("TC07.json")
        val result = payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads)

        result mustBe Seq(
          TaxCodeIncome(
            JobSeekerAllowanceIncome,
            Some(1),
            BigDecimal(1111),
            "JobSeekerAllowanceIncome",
            "1150L",
            "",
            OtherBasisOperation,
            Live,
            BigDecimal(0),
            BigDecimal(0),
            BigDecimal(0)
          )
        )
      }

      "other income source indicator is true" in {
        val payload = readFile("TC08.json")
        val result = payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads)
        result mustBe Seq(
          TaxCodeIncome(
            OtherIncome,
            Some(1),
            BigDecimal(1111),
            "OtherIncome",
            "1150L",
            "",
            OtherBasisOperation,
            Live,
            BigDecimal(0),
            BigDecimal(0),
            BigDecimal(0)
          )
        )
      }

      "employment id is not available" in {
        val payload = readFile("TC09.json")
        val result = payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads)
        result mustBe Seq(
          TaxCodeIncome(
            OtherIncome,
            None,
            BigDecimal(0),
            "OtherIncome",
            "1150L",
            "",
            OtherBasisOperation,
            Live,
            BigDecimal(0),
            BigDecimal(0),
            BigDecimal(0)
          )
        )
      }

      "taxCode is not available" in {
        val payload = readFile("TC10.json")
        val result = payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads)
        result mustBe Seq(
          TaxCodeIncome(
            OtherIncome,
            Some(1),
            BigDecimal(1111),
            "OtherIncome",
            "",
            "",
            OtherBasisOperation,
            Live,
            BigDecimal(0),
            BigDecimal(0),
            BigDecimal(0)
          )
        )
      }
    }

    "utilize totalTaxableIncomeReads to read amount" when {
      "provided with whole json" in {
        val payload = readFile("TC11.json")
        val result = payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads)
        result mustBe Seq(
          TaxCodeIncome(
            OtherIncome,
            Some(1),
            BigDecimal(1111),
            "OtherIncome",
            "1150L",
            "",
            OtherBasisOperation,
            Live,
            BigDecimal(0),
            BigDecimal(0),
            BigDecimal(0)
          )
        )
      }

      "provided with missing payAndTax element" in {
        val payload = readFile("TC12.json")
        val result = payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads)
        result mustBe Seq(
          TaxCodeIncome(
            OtherIncome,
            Some(1),
            BigDecimal(0),
            "OtherIncome",
            "1150L",
            "",
            OtherBasisOperation,
            Live,
            BigDecimal(0),
            BigDecimal(0),
            BigDecimal(0)
          )
        )
      }
    }
  }

  "taxAccountReads" must {
    "deserialize the tax account json into the case class with none values" when {
      "none of the fields are provided" in {
        val payload = Json.obj(
          "taxYear" -> JsNumber(2017)
        )
        payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads) mustBe Nil
      }

      "fields provided with null values" in {
        val payload = readFile("TC14.json")
        payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads) mustBe Nil
      }
    }

    "deserialize the tax account json into the case class with all values" when {
      "all of the fields are provided" in {
        val payload = readFile("TC15.json")
        payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads) mustBe
          Seq(
            TaxCodeIncome(
              EmploymentIncome,
              Some(1),
              BigDecimal(0),
              "EmploymentIncome",
              "1150L",
              "Employer1",
              Week1Month1BasisOperation,
              Live,
              BigDecimal(0),
              BigDecimal(0),
              BigDecimal(0)
            ),
            TaxCodeIncome(
              EmploymentIncome,
              Some(2),
              BigDecimal(0),
              "EmploymentIncome",
              "1100L",
              "Employer2",
              OtherBasisOperation,
              Live,
              BigDecimal(0),
              BigDecimal(0),
              BigDecimal(0)
            )
          )
      }
    }
  }

  "taxCodeIncomeSourceReads using employmentStatus" must {
    "read status as live" when {
      "provided with employmentStatus as 1" in {
        val payload = readFile("TC16.json")
        val result = payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads)
        result mustBe Seq(
          TaxCodeIncome(
            OtherIncome,
            Some(1),
            BigDecimal(0),
            "OtherIncome",
            "1150L",
            "",
            OtherBasisOperation,
            Live,
            BigDecimal(0),
            BigDecimal(0),
            BigDecimal(0)
          )
        )
      }
    }

    "read status as potentially ceased" when {
      "provided with employmentStatus as 2" in {
        val payload = readFile("TC17.json")
        val result = payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads)
        result mustBe Seq(
          TaxCodeIncome(
            OtherIncome,
            Some(1),
            BigDecimal(0),
            "OtherIncome",
            "1150L",
            "",
            OtherBasisOperation,
            PotentiallyCeased,
            BigDecimal(0),
            BigDecimal(0),
            BigDecimal(0)
          )
        )
      }
    }

    "read status as ceased" when {
      "provided with employmentStatus as 3" in {
        val payload = readFile("TC18.json")
        val result = payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads)
        result mustBe Seq(
          TaxCodeIncome(
            OtherIncome,
            Some(1),
            BigDecimal(0),
            "OtherIncome",
            "1150L",
            "",
            OtherBasisOperation,
            Ceased,
            BigDecimal(0),
            BigDecimal(0),
            BigDecimal(0)
          )
        )
      }
    }

    "read in year adjustment amounts" when {
      "values are present within tax account JSON" in {
        val payload = readFile("TC19.json")
        val result = payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads)

        result mustBe Seq(
          TaxCodeIncome(
            componentType = OtherIncome,
            employmentId = Some(1),
            amount = BigDecimal(0),
            description = "OtherIncome",
            taxCode = "1150L",
            name = "",
            basisOperation = OtherBasisOperation,
            status = Ceased,
            inYearAdjustmentIntoCY = BigDecimal(10.20),
            totalInYearAdjustment = BigDecimal(30.40),
            inYearAdjustmentIntoCYPlusOne = BigDecimal(10.60)
          )
        )
      }
    }

    "report zero value in year adjustment amounts" when {
      "values are absent from tax account JSON" in {

        val payload = readFile("TC20.json")
        val result = payload.as[Seq[TaxCodeIncome]](TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads)
        result mustBe Seq(
          TaxCodeIncome(
            OtherIncome,
            Some(1),
            BigDecimal(0),
            "OtherIncome",
            "1150L",
            "",
            OtherBasisOperation,
            Ceased,
            BigDecimal(0),
            BigDecimal(0),
            BigDecimal(0)
          )
        )
      }
    }

    "error out" when {
      "provided with invalid employmentStatus" in {
        val payload = readFile("TC21.json")
        val ex =
          the[RuntimeException] thrownBy payload.as[Seq[TaxCodeIncome]](
            TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads
          )
        ex.getMessage mustBe "Invalid employment status"
      }

      "employmentStatus is not available" in {

        val payload = readFile("TC22.json")
        val ex =
          the[RuntimeException] thrownBy payload.as[Seq[TaxCodeIncome]](
            TaxCodeIncomeHipReads.taxCodeIncomeSourcesReads
          )
        ex.getMessage mustBe "Invalid employment status"
      }
    }
  }

  "TaxCodeIncomeHipReads.newEstimatedPayTypeFilter" must {
    "return true" when {
      "new estimated pay is available" in {
        val iabd = IabdSummary(27, Some(1), 11111)
        TaxCodeIncomeHipReads.newEstimatedPayTypeFilter(iabd) mustBe true
      }
    }

    "return false" when {
      "new estimated pay is not available" in {
        val iabd = IabdSummary(28, Some(1), 11111)
        TaxCodeIncomeHipReads.newEstimatedPayTypeFilter(iabd) mustBe false
      }
    }
  }

  "TaxCodeIncomeHipReads.employmentFilter" must {
    "return true" when {
      "iabd employment match with nps employment" in {
        val iabd = IabdSummary(27, Some(1), 11111)
        TaxCodeIncomeHipReads.employmentFilter(iabd, Some(1)) mustBe true
      }
    }

    "return false" when {
      "iabd employment does not match nps employment" in {
        val iabd = IabdSummary(27, Some(1), 11111)
        TaxCodeIncomeHipReads.employmentFilter(iabd, Some(2)) mustBe false
      }

      "iabd employment is not available" in {
        val iabd = IabdSummary(27, None, 11111)
        TaxCodeIncomeHipReads.employmentFilter(iabd, Some(2)) mustBe false
      }

      "nps employment is not available" in {
        val iabd = IabdSummary(27, Some(1), 11111)
        TaxCodeIncomeHipReads.employmentFilter(iabd, None) mustBe false
      }

      "iabd employment and nps employment are not available" in {
        val iabd = IabdSummary(27, None, 11111)
        TaxCodeIncomeHipReads.employmentFilter(iabd, None) mustBe false
      }
    }
  }
}

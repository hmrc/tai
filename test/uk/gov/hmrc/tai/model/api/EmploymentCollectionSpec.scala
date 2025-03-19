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

package uk.gov.hmrc.tai.model.api

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.*
import uk.gov.hmrc.tai.model.api.EmploymentCollection.employmentCollectionHodReadsHIP
import uk.gov.hmrc.tai.model.domain.income.Live
import uk.gov.hmrc.tai.model.domain.{Employment, EmploymentIncome}
import uk.gov.hmrc.tai.util.TaxCodeHistoryConstants

import java.io.File
import java.time.LocalDate
import scala.io.BufferedSource
import scala.util.{Failure, Try}

class EmploymentCollectionSpec extends PlaySpec with TaxCodeHistoryConstants {

  private def getJson(fileName: String): JsValue = {
    val jsonFilePath = "test/resources/data/EmploymentHodFormattersTesting/" + fileName + ".json"
    val file: File = new File(jsonFilePath)
    val source: BufferedSource = scala.io.Source.fromFile(file)
    val jsVal = Json.parse(source.mkString(""))
    jsVal
  }

  private val sampleSingleEmployment = List(
    Employment(
      "EMPLOYER1",
      Live,
      Some("0000"),
      LocalDate.of(2016, 4, 6),
      None,
      Nil,
      "000",
      "00000",
      2,
      Some(100),
      false,
      false,
      Some(EmploymentIncome),
      Some("1250L")
    )
  )

  private val sampleDualEmployment = List(
    Employment(
      "EMPLOYER1",
      Live,
      Some("0000"),
      LocalDate.of(2016, 4, 6),
      None,
      Nil,
      "000",
      "00000",
      2,
      None,
      true,
      false,
      Some(EmploymentIncome)
    ),
    Employment(
      "EMPLOYER2",
      Live,
      Some("0000"),
      LocalDate.of(2016, 4, 6),
      None,
      Nil,
      "000",
      "00000",
      2,
      Some(100),
      false,
      false,
      Some(EmploymentIncome)
    )
  )

  "employmentCollectionHodReads (for HIP payloads)" must {
    "un-marshall employment json" when {

      "reading empty response from Hod" in {
        val employment =
          Json.obj().as[EmploymentCollection](employmentCollectionHodReadsHIP)

        employment.employments mustBe List()
      }

      "reading single employment from Hod" in {
        val employment =
          getJson("hipSingleEmployment").as[EmploymentCollection](employmentCollectionHodReadsHIP)

        employment.employments mustBe sampleSingleEmployment

      }

      "reading single employment from Hod where format is NPS format instead of HIP format" in {
        val employment =
          getJson("npsSingleEmployment").as[EmploymentCollection](employmentCollectionHodReadsHIP)

        employment.employments mustBe sampleSingleEmployment
      }

      "reading single employment from Hod where invalid payload (array - Squid) returns the HIP errors and not NPS errors" in {
        val actual = Try(
          Json.arr(Json.obj("a" -> "b")).as[EmploymentCollection](employmentCollectionHodReadsHIP)
        )

        actual mustBe Failure(
          JsResultException(
            Seq((__, List(JsonValidationError(List("Unexpected array - Squid payload?")))))
          )
        )
      }

      "reading multiple employments from Hod" in {
        val employment =
          getJson("hipDualEmployment").as[EmploymentCollection](employmentCollectionHodReadsHIP)

        employment.employments mustBe sampleDualEmployment
      }
    }

    "Remove any leading zeroes from a numeric 'taxDistrictNumber' field" in {
      val employment = getJson("hipLeadingZeroTaxDistrictNumber").as[EmploymentCollection](
        employmentCollectionHodReadsHIP
      )
      employment.employments.head.taxDistrictNumber mustBe "000"
      employment.employments.head.sequenceNumber mustBe 2
    }

    "Correctly handle a non numeric 'taxDistrictNumber' field" in {
      val employment = getJson("hipNonNumericTaxDistrictNumber").as[EmploymentCollection](
        employmentCollectionHodReadsHIP
      )
      employment.employments.head.taxDistrictNumber mustBe "000"
      employment.employments.head.sequenceNumber mustBe 2
    }
  }
}

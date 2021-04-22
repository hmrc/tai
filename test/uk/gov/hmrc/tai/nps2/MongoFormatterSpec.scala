/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.tai.nps2

import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNull, Json}
import uk.gov.hmrc.tai.model.enums.BasisOperation
import uk.gov.hmrc.tai.model.nps2.IabdType.GiftAidPayments
import uk.gov.hmrc.tai.model.nps2.IabdUpdateSource.Letter
import uk.gov.hmrc.tai.model.nps2.Income.{Ceased, IncomeType, Status}
import uk.gov.hmrc.tai.model.nps2.{Component, Iabd, Income, MongoFormatter, NpsEmployment, TaxBand, TaxDetail, TaxObject}

class MongoFormatterSpec extends PlaySpec with MongoFormatter {

  "Mongo Formatter" should {

    "provide Json formatting of Liability Map" when {
      "reading the liability map JsValue" in {

        val json = Json.obj(
          "nonSavings" -> Json.obj(
            "taxBands" -> Json.arr(
              Json.obj(
                "bandType"  -> "dummyType",
                "code"      -> "ABCD123",
                "income"    -> 33000,
                "tax"       -> 8000,
                "lowerBand" -> 5000,
                "upperBand" -> 20000,
                "rate"      -> 33
              ),
              Json.obj(
                "bandType"  -> "dummyType",
                "code"      -> "ABCD123",
                "income"    -> 33000,
                "tax"       -> 8000,
                "lowerBand" -> 5000,
                "upperBand" -> 20000,
                "rate"      -> 33
              )
            ),
            "totalTax"           -> 123.1,
            "totalTaxableIncome" -> 999.1,
            "totalIncome"        -> 333.1
          )
        )

        val totalLiabilityMap = json.as[Map[TaxObject.Type.Value, TaxDetail]]

        totalLiabilityMap must not be empty
        val taxDetail = totalLiabilityMap.get(TaxObject.Type.NonSavings)

        taxDetail mustBe Some(
          TaxDetail(
            totalTax = Some(123.1),
            totalTaxableIncome = Some(999.1),
            totalIncome = Some(333.1),
            taxBands = List(testTaxBand, testTaxBand)))

        Json.toJson(totalLiabilityMap) mustBe json
      }
    }

    "Income Writes" when {

      "converts Income to Json where where many fields are not present" in {

        Json.toJson(defaultIncome) mustEqual defaultIncomeJson

      }

      "converts Income to json where incomeType is pension" in {

        val json = defaultIncomeJson ++ Json.obj("pensionIndicator" -> true)
        Json.toJson(defaultIncome.copy(incomeType = IncomeType.Pension)) mustEqual json

      }

      "converts Income to json where incomeType is JSA" in {

        val json = defaultIncomeJson ++ Json.obj("jsaIndicator" -> true)
        Json.toJson(defaultIncome.copy(incomeType = IncomeType.JobSeekersAllowance)) mustEqual json

      }

      "converts Income to json where incomeType is OtherIncome" in {

        val json = defaultIncomeJson ++ Json.obj("otherIncomeSourceIndicator" -> true)
        Json.toJson(defaultIncome.copy(incomeType = IncomeType.OtherIncome)) mustEqual json

      }

      "converts Income to Json where all the fields are present" in {
        val json = Json.obj(
          "employmentId"                -> 1,
          "employmentType"              -> 1,
          "employmentStatus"            -> 1,
          "employmentTaxDistrictNumber" -> 111,
          "employmentPayeRef"           -> "some paye ref",
          "pensionIndicator"            -> false,
          "jsaIndicator"                -> false,
          "otherIncomeSourceIndicator"  -> false,
          "name"                        -> "some name",
          "endDate"                     -> JsNull,
          "worksNumber"                 -> "1234",
          "taxCode"                     -> "AB1234",
          "potentialUnderpayment"       -> 20.2,
          "employmentRecord" -> Json.obj(
            "employerName"      -> "Company Plc",
            "employmentType"    -> 1,
            "sequenceNumber"    -> 1,
            "worksNumber"       -> "1234",
            "taxDistrictNumber" -> "1",
            "iabds" -> Json.arr(
              Json.obj(
                "grossAmount" -> 10,
                "type"        -> 1,
                "source"      -> 16,
                "typeDescription" ->
                  "dummyDescription",
                "employmentSequenceNumber" -> 32
              )
            ),
            "cessationPayThisEmployment" -> 2200.22,
            "startDate"                  -> "12/12/2017"
          ),
          "basisOperation" -> "Week1Month1"
        )

        val income = Income(
          employmentId = Some(1),
          isPrimary = true,
          incomeType = IncomeType.Employment,
          status = Status(Some(1), None),
          taxDistrict = Some(111),
          payeRef = "some paye ref",
          name = "some name",
          worksNumber = Some("1234"),
          taxCode = "AB1234",
          potentialUnderpayment = 20.2,
          employmentRecord = Some(
            NpsEmployment(
              Some("Company Plc"),
              isPrimary = true,
              sequenceNumber = 1,
              worksNumber = Some("1234"),
              districtNumber = 1,
              iabds = List(testIabd),
              cessationPay = Some(2200.22),
              start = new LocalDate(2017, 12, 12)
            )),
          basisOperation = Some(BasisOperation.Week1Month1)
        )
        Json.toJson(income) mustEqual json
      }

    }

    "provide Json formatting of Income" when {
      "reading the Income json with Ceased Status" in {

        val json = Json.obj(
          "employmentId"                -> JsNull,
          "employmentType"              -> 2,
          "employmentStatus"            -> 3,
          "employmentTaxDistrictNumber" -> JsNull,
          "employmentPayeRef"           -> "payRef",
          "pensionIndicator"            -> false,
          "jsaIndicator"                -> false,
          "otherIncomeSourceIndicator"  -> false,
          "name"                        -> "name",
          "endDate"                     -> "12/12/2017",
          "worksNumber"                 -> "1234",
          "taxCode"                     -> "AB1234",
          "potentialUnderpayment"       -> 20.2,
          "employmentRecord" -> Json.obj(
            "employerName"      -> "Company Plc",
            "employmentType"    -> 1,
            "sequenceNumber"    -> 1,
            "worksNumber"       -> "1234",
            "taxDistrictNumber" -> "1",
            "iabds" -> Json.arr(
              Json.obj(
                "grossAmount"              -> 10,
                "type"                     -> 1,
                "source"                   -> 16,
                "typeDescription"          -> "dummyDescription",
                "employmentSequenceNumber" -> 32
              )),
            "cessationPayThisEmployment" -> 2200.22,
            "startDate"                  -> "12/12/2017"
          ),
          "basisOperation" -> "Week1Month1"
        )

        val income = json.as[Income]

        income.employmentRecord mustBe Some(testNpsEmployment)
        income.basisOperation mustBe Some(BasisOperation.Week1Month1)
        income.incomeType mustBe IncomeType.Employment
        income.status mustBe Income.Ceased
      }

      "reading the Income json with Live Status" in {

        val json = Json.obj(
          "employmentId"                -> JsNull,
          "employmentType"              -> 2,
          "employmentStatus"            -> 1,
          "employmentTaxDistrictNumber" -> JsNull,
          "employmentPayeRef"           -> "payRef",
          "pensionIndicator"            -> false,
          "jsaIndicator"                -> true,
          "otherIncomeSourceIndicator"  -> false,
          "name"                        -> "name",
          "endDate"                     -> "12/12/2017",
          "worksNumber"                 -> "1234",
          "taxCode"                     -> "AB1234",
          "potentialUnderpayment"       -> 20.2,
          "employmentRecord" -> Json.obj(
            "employerName"      -> "Company Plc",
            "employmentType"    -> 1,
            "sequenceNumber"    -> 1,
            "worksNumber"       -> "1234",
            "taxDistrictNumber" -> "1",
            "iabds" -> Json.arr(
              Json.obj(
                "grossAmount"              -> 10,
                "type"                     -> 1,
                "source"                   -> 16,
                "typeDescription"          -> "dummyDescription",
                "employmentSequenceNumber" -> 32
              )),
            "cessationPayThisEmployment" -> 2200.22,
            "startDate"                  -> "12/12/2017"
          ),
          "basisOperation" -> "Week1Month1"
        )

        val income = json.as[Income]

        income.employmentRecord mustBe Some(testNpsEmployment)
        income.basisOperation mustBe Some(BasisOperation.Week1Month1)
        income.incomeType mustBe IncomeType.JobSeekersAllowance
        income.status mustBe Income.Live
      }

      "reading the Income json with Potentially Ceased Status" in {

        val json = Json.obj(
          "employmentId"                -> JsNull,
          "employmentType"              -> 2,
          "employmentStatus"            -> 2,
          "employmentTaxDistrictNumber" -> JsNull,
          "employmentPayeRef"           -> "payRef",
          "pensionIndicator"            -> false,
          "jsaIndicator"                -> false,
          "otherIncomeSourceIndicator"  -> true,
          "name"                        -> "name",
          "endDate"                     -> "12/12/2017",
          "worksNumber"                 -> "1234",
          "taxCode"                     -> "AB1234",
          "potentialUnderpayment"       -> 20.2,
          "employmentRecord" -> Json.obj(
            "employerName"      -> "Company Plc",
            "employmentType"    -> 1,
            "sequenceNumber"    -> 1,
            "worksNumber"       -> "1234",
            "taxDistrictNumber" -> "1",
            "iabds" -> Json.arr(
              Json.obj(
                "grossAmount"              -> 10,
                "type"                     -> 1,
                "source"                   -> 16,
                "typeDescription"          -> "dummyDescription",
                "employmentSequenceNumber" -> 32
              )),
            "cessationPayThisEmployment" -> 2200.22,
            "startDate"                  -> "12/12/2017"
          ),
          "basisOperation" -> "Week1Month1"
        )

        val income = json.as[Income]

        income.employmentRecord mustBe Some(testNpsEmployment)
        income.basisOperation mustBe Some(BasisOperation.Week1Month1)
        income.incomeType mustBe IncomeType.OtherIncome
        income.status mustBe Income.PotentiallyCeased
      }
    }

    "throw Runtime Exception" when {
      "reading the Income json with incorrect Status" in {

        val json = Json.obj(
          "employmentId"                -> JsNull,
          "employmentType"              -> 2,
          "employmentStatus"            -> 4,
          "employmentTaxDistrictNumber" -> JsNull,
          "employmentPayeRef"           -> "payRef",
          "pensionIndicator"            -> false,
          "jsaIndicator"                -> false,
          "otherIncomeSourceIndicator"  -> true,
          "name"                        -> "name",
          "endDate"                     -> "12/12/2017",
          "worksNumber"                 -> "1234",
          "taxCode"                     -> "AB1234",
          "potentialUnderpayment"       -> 20.2,
          "employmentRecord" -> Json.obj(
            "employerName"      -> "Company Plc",
            "employmentType"    -> 1,
            "sequenceNumber"    -> 1,
            "worksNumber"       -> "1234",
            "taxDistrictNumber" -> "1",
            "iabds" -> Json.arr(
              Json.obj(
                "grossAmount"              -> 10,
                "type"                     -> 1,
                "source"                   -> 16,
                "typeDescription"          -> "dummyDescription",
                "employmentSequenceNumber" -> 32
              )),
            "cessationPayThisEmployment" -> 2200.22,
            "startDate"                  -> "12/12/2017"
          ),
          "basisOperation" -> "Week1Month1"
        )

        the[RuntimeException] thrownBy json.as[Income]
      }
    }

    "provide Json formatting of Component" when {
      "reading the Component json" in {

        val json = Json.obj(
          "amount"       -> 34000.3,
          "sourceAmount" -> 32000.7,
          "iabdSummaries" -> Json.arr(
            Json.obj(
              "grossAmount"              -> 10,
              "type"                     -> 1,
              "source"                   -> 16,
              "typeDescription"          -> "dummyDescription",
              "employmentSequenceNumber" -> 32
            ))
        )

        val component = json.as[Component]

        component.amount mustBe 34000.30
        component.sourceAmount mustBe Some(32000.70)
        component.iabds mustBe List(testIabd)
      }
    }

    "provide Json formatting of NpsEmployment" when {
      "reading the NpsEmployment json" in {

        val json = Json.obj(
          "employerName"      -> "Company Plc",
          "employmentType"    -> 1,
          "sequenceNumber"    -> 1,
          "worksNumber"       -> "1234",
          "taxDistrictNumber" -> "1",
          "iabds" -> Json.arr(
            Json.obj(
              "grossAmount"              -> 10,
              "type"                     -> 1,
              "source"                   -> 16,
              "typeDescription"          -> "dummyDescription",
              "employmentSequenceNumber" -> 32
            )),
          "cessationPayThisEmployment" -> 2200.22,
          "startDate"                  -> "12/12/2017"
        )

        val npsEmployment = json.as[NpsEmployment]

        npsEmployment.employerName mustBe Some("Company Plc")
        npsEmployment.isPrimary mustBe true
        npsEmployment.sequenceNumber mustBe 1
        npsEmployment.worksNumber mustBe Some("1234")
        npsEmployment.districtNumber mustBe 1
        npsEmployment.iabds mustBe List(testIabd)
        npsEmployment.cessationPay mustBe Some(2200.22)
        npsEmployment.start mustBe fixedDate
      }
    }
  }

  private val testTaxBand = TaxBand(
    bandType = Some("dummyType"),
    code = Some("ABCD123"),
    income = 33000,
    tax = 8000,
    lowerBand = Some(5000),
    upperBand = Some(20000),
    rate = 33)

  private val fixedDate = LocalDate.parse("2017-12-12")

  private val testIabd = Iabd(
    amount = 10,
    iabdType = GiftAidPayments,
    source = Letter,
    description = "dummyDescription",
    employmentSequence = Some(32))

  private val testNpsEmployment = NpsEmployment(
    employerName = Some("Company Plc"),
    isPrimary = true,
    sequenceNumber = 1,
    worksNumber = Some("1234"),
    districtNumber = 1,
    iabds = List(testIabd),
    cessationPay = Some(2200.22),
    start = fixedDate
  )

  private val defaultIncome = Income(
    employmentId = None,
    isPrimary = false,
    incomeType = IncomeType.Employment,
    status = Status(Some(1), None),
    taxDistrict = None,
    payeRef = "some paye ref",
    name = "some name",
    worksNumber = None,
    taxCode = "AB1234",
    potentialUnderpayment = 20.2,
    employmentRecord = None,
    basisOperation = None
  )

  private val defaultIncomeJson = Json.obj(
    "employmentId"                -> JsNull,
    "employmentType"              -> 2,
    "employmentStatus"            -> 1,
    "employmentTaxDistrictNumber" -> JsNull,
    "employmentPayeRef"           -> "some paye ref",
    "pensionIndicator"            -> false,
    "jsaIndicator"                -> false,
    "otherIncomeSourceIndicator"  -> false,
    "name"                        -> "some name",
    "endDate"                     -> JsNull,
    "worksNumber"                 -> JsNull,
    "taxCode"                     -> "AB1234",
    "potentialUnderpayment"       -> 20.2,
    "employmentRecord"            -> JsNull
  )

  private def stripFormatting(string: String): String =
    string.stripMargin.replaceAll("\\n+", "")
}

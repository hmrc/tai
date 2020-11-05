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

package uk.gov.hmrc.tai.nps2

import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import uk.gov.hmrc.tai.model.enums.PayFreq
import uk.gov.hmrc.tai.model.nps2
import uk.gov.hmrc.tai.model.tai.NoOfMonths
import nps2._
import uk.gov.hmrc.tai.model.nps2.IabdType.GiftAidPayments
import uk.gov.hmrc.tai.model.nps2.IabdUpdateSource.Letter
import uk.gov.hmrc.tai.model.nps2.Income.{IncomeType, Status}
import uk.gov.hmrc.tai.model.enums.BasisOperation

class Nps2PackageSpec extends PlaySpec with NpsFormatter {

  "package" should {
    "provide Json formatting of enumeration types" when {
      "correctly marshall string enumeration types to and from Json" in {
        val format = nps2.enumerationFormat(PayFreq)
        val jsValue = format.writes(PayFreq.weekly)
        jsValue mustBe JsString("weekly")

        val resultEnum = format.reads(jsValue).get
        resultEnum mustBe PayFreq.weekly
      }

      "correctly marshall numeric enumeration types to and from Json" in {
        val noMonthsEnum = NoOfMonths.Annually
        val format = nps2.enumerationNumFormat(NoOfMonths)
        val jsValue = format.writes(noMonthsEnum)
        jsValue mustBe JsNumber(12)

        val resultEnum = format.reads(jsValue).get
        resultEnum mustBe noMonthsEnum
      }
    }

    "provide Json formatting of LocalDate" when {
      "marshall a LocalDate into JsValue" in {
        val jsonObj = Json.toJson(new LocalDate("2017-05-03"))
        jsonObj.toString() mustBe """"03/05/2017""""
      }

      "unmarshall a valid Json date string into a LocalDate" in {
        val unmarshalledTestClass = Json.parse(""""03/05/2017"""").as[LocalDate]
        unmarshalledTestClass mustBe new LocalDate("2017-05-03")
      }

      "produce a JsError when attempting to unmarshall an invalid date string" in {
        val exception = intercept[JsResultException] {
          Json.parse(""""033/05/2017"""").as[LocalDate]
        }

        val errorStrings = extractErrorsPerPath(exception)
        errorStrings.size mustBe 1
        errorStrings must contain(" -> Invalid date format [dd/MM/yyyy]: \"033/05/2017\"")
      }
    }

    "provide Json formatting of TaxBand" when {
      "marshall a TaxBand into a JSValue" in {
        val jsonObj = Json.toJson(testTaxBand)
        jsonObj.toString() mustBe
          """{"bandType":"dummyType","code":"ABCD123","income":33000,"tax":8000,"lowerBand":5000,"upperBand":20000,"rate":33}"""
      }

      "unmarshall a Json taxBand string into a TaxBand model" in {
        val unmarshalledTestClass = Json
          .parse(
            """{"bandType":"dummyType","code":"ABCD123","income":33000,"tax":8000,"lowerBand":5000,"upperBand":20000,"rate":33}""")
          .as[TaxBand]
        unmarshalledTestClass mustBe testTaxBand
      }

      "unmarshall a taxBand provided by nps2 in Json format into a TaxBand model" in {
        val unmarshalledTestClass = Json
          .parse(
            """{"bandType": "B","taxCode": "BR","isBasicRate": true,"income": 14000,"tax": 2800,"lowerBand": 0,"upperBand": 32000,"rate": 20}""")
          .as[TaxBand]

        unmarshalledTestClass mustBe
          TaxBand(
            bandType = Some("B"),
            code = None,
            income = 14000,
            tax = 2800,
            lowerBand = Some(0),
            upperBand = Some(32000),
            rate = 20
          )
      }
    }

    "provide Json formatting of Iabd" when {
      "marshall an Iabd into a JSValue" in {
        val jsonObj = Json.toJson(testIabd)
        jsonObj.toString() mustBe """{"grossAmount":10,"type":1,"source":16,"typeDescription":"dummyDescription","employmentSequenceNumber":32}"""
      }

      "unmarshall a Json Iabd string into an Iabd model" in {
        val unmarshalledTestClass = Json
          .parse(
            """{"grossAmount":10,"type":1,"source":16,"typeDescription":"dummyDescription","employmentSequenceNumber":32}""")
          .as[Iabd]
        unmarshalledTestClass mustBe testIabd
      }

      "marshall a list of Iabd into a JsValue" in {
        val jsonObj = Json.toJson(iabdList)
        jsonObj.toString() mustBe stripFormatting(
          """
            |[
            |{"grossAmount":10,"type":1,"source":16,"typeDescription":"dummyDescription","employmentSequenceNumber":32},
            |{"grossAmount":10,"type":1,"source":16,"typeDescription":"dummyDescription","employmentSequenceNumber":32},
            |{"grossAmount":10,"type":1,"source":16,"typeDescription":"dummyDescription","employmentSequenceNumber":32}]"""
        )
      }

      "unmarshall a Json Iabd list into a List of Iabd" in {
        val unmarshalledTestClass = Json
          .parse(
            """
            [
            {"grossAmount":10,"type":1,"source":16,"typeDescription":"dummyDescription","employmentSequenceNumber":32},
            {"grossAmount":10,"type":1,"source":16,"typeDescription":"dummyDescription","employmentSequenceNumber":32},
            {"grossAmount":10,"type":1,"source":16,"typeDescription":"dummyDescription","employmentSequenceNumber":32}]"""
          )
          .as[List[Iabd]]

        unmarshalledTestClass mustBe iabdList
      }
    }

    "provide Json formatting of Component" when {
      "marshall a Component into a JSValue" in {
        val jsonObj = Json.toJson(testComponent)
        jsonObj.toString() mustBe stripFormatting(
          """
            |{
            |"amount":34000.3,
            |"sourceAmount":32000.7,
            |"iabdSummaries":[{"grossAmount":10,"type":1,"source":16,"typeDescription":"dummyDescription","employmentSequenceNumber":32}]}"""
        )
      }

      "unmarshall a Json Component string into a Component model" in {
        val unmarshalledTestClass = Json
          .parse(
            """
          {
          "amount":34000.3,
          "sourceAmount":32000.7,
          "iabdSummaries":[{"grossAmount":10,"type":1,"source":16,"typeDescription":"dummyDescription","employmentSequenceNumber":32}]}"""
          )
          .as[Component]

        unmarshalledTestClass mustBe testComponent
      }
    }

    "provide Json formatting of liabilityMap" when {
      "marshall a liabilityMap scala structure into JsValue" in {
        val jsonObj = Json.toJson(Map(testLiabilityMapKey -> testLiabilityMapValue))
        jsonObj.toString() mustBe stripFormatting(
          """{
            |"nonSavings":{
            |"taxBands":[{"bandType":"dummyType","code":"ABCD123","income":33000,"tax":8000,"lowerBand":5000,"upperBand":20000,"rate":33},
            |{"bandType":"dummyType","code":"ABCD123","income":33000,"tax":8000,"lowerBand":5000,"upperBand":20000,"rate":33}],
            |"totalTax":123.1,"totalTaxableIncome":999.1,"totalIncome":333.1}}""".stripMargin
        )
      }

      "marshall a liabilityMap scala structure into JsValue for null totalTax and totalTaxableIncome" in {
        val jsonObj = Json.toJson(Map(testLiabilityMapKey -> testLiabilityMapValueEmpty))
        jsonObj.toString() mustBe stripFormatting(
          """{
            |"nonSavings":{
            |"taxBands":[{"bandType":"dummyType","code":"ABCD123","income":33000,"tax":8000,"lowerBand":5000,"upperBand":20000,"rate":33},
            |{"bandType":"dummyType","code":"ABCD123","income":33000,"tax":8000,"lowerBand":5000,"upperBand":20000,"rate":33}],
            |"totalTax":null,"totalTaxableIncome":null,"totalIncome":333.1}}""".stripMargin
        )
      }

      "marshall a liabilityMap scala structure into JsValue for empty taxBands" in {
        val jsonObj = Json.toJson(Map(testLiabilityMapKey -> testLiabilityMapValueEmptyTaxBands))
        jsonObj.toString() mustBe stripFormatting(
          """{
            |"nonSavings":{}}""".stripMargin
        )
      }

      "unmarshalling an nps2 Json liability structure" should {
        "ignore any tax bands that have income value of zero" when {
          "and create a corresponding Liability Map structure from the filtered result" in {
            val npsJson =
              """
            {
               "nonSavings": {
                 "taxBands": [
                   {
                     "bandType": "B",
                     "income": 19595,
                     "isBasicRate": true,
                     "lowerBand": 0,
                     "rate": 20,
                     "tax": 3919,
                     "taxCode": "BR",
                     "upperBand": 32000
                   },
                   {
                     "bandType": "D0",
                     "income": null,
                     "isBasicRate": false,
                     "lowerBand": 32000,
                     "rate": 40,
                     "tax": null,
                     "taxCode": "D0",
                     "upperBand": 150000
                   },
                   {
                     "bandType": "D1",
                     "income": null,
                     "isBasicRate": false,
                     "lowerBand": 150000,
                     "rate": 45,
                     "tax": null,
                     "taxCode": "D1",
                     "upperBand": 0
                   }
                 ],
                 "totalIncome": {
                   "amount": 30595,
                   "iabdSummaries": [
                     {
                       "amount": 30595,
                       "defaultEstimatedPay": null,
                       "employmentId": 1,
                       "npsDescription": "New Estimated Pay",
                       "type": 27
                     }
                   ],
                   "npsDescription": null,
                   "sourceAmount": null,
                   "type": null
                 },
                 "totalTax": 3919,
                 "totalTaxableIncome": 19595
               }
             }"""

            val unmarshalledTestClass = Json.parse(npsJson).as[Map[TaxObject.Type.Value, TaxDetail]]

            unmarshalledTestClass must not be empty
            val taxDetail = unmarshalledTestClass.get(TaxObject.Type.NonSavings)

            taxDetail mustEqual Some(TaxDetail(
              totalTax = Some(3919),
              totalTaxableIncome = Some(19595),
              totalIncome = Some(30595),
              taxBands = List(
                TaxBand(
                  bandType = Some("B"),
                  code = None,
                  income = 19595,
                  tax = 3919,
                  lowerBand = Some(0),
                  upperBand = Some(32000),
                  rate = 20)
              )
            ))
          }
        }

        "detect any 'allowReliefDeducts' structure" when {
          "and create an additional tax band of type='pa' and amount equal to the allowReliefDeducts.amount field" in {
            val npsJson =
              """
              {
                 "nonSavings": {
                   "allowReliefDeducts": {
                     "amount": 11000,
                     "iabdSummaries": [
                       {
                         "amount": 11000,
                         "employmentId": null,
                         "npsDescription": "Personal Allowance Aged (PAA)",
                         "type": 119
                       }
                     ],
                     "npsDescription": null,
                     "sourceAmount": null,
                     "type": null
                   },
                   "totalIncome": {
                     "amount": 30595,
                     "iabdSummaries": [
                       {
                         "amount": 30595,
                         "defaultEstimatedPay": null,
                         "employmentId": 1,
                         "npsDescription": "New Estimated Pay",
                         "type": 27
                       }
                     ],
                     "npsDescription": null,
                     "sourceAmount": null,
                     "type": null
                   },
                   "totalTax": 3919,
                   "totalTaxableIncome": 19595
                 }
               }"""

            val unmarshalledTestClass = Json.parse(npsJson).as[Map[TaxObject.Type.Value, TaxDetail]]

            unmarshalledTestClass must not be empty
            val taxDetail = unmarshalledTestClass.get(TaxObject.Type.NonSavings)

            taxDetail mustEqual Some(
              TaxDetail(
                totalTax = Some(3919),
                totalTaxableIncome = Some(19595),
                totalIncome = Some(30595),
                taxBands = List(
                  TaxBand(bandType = Some("pa"), income = 11000, tax = 0, rate = 0)
                )
              ))
          }
        }

        "cater for presence of tax bands and 'allowReliefDeducts' structure together" when {
          "and create appropriate liability map" in {

            val npsJson =
              """
              {
                 "nonSavings": {
                   "allowReliefDeducts": {
                     "amount": 11000,
                     "iabdSummaries": [
                       {
                         "amount": 11000,
                         "employmentId": null,
                         "npsDescription": "Personal Allowance Aged (PAA)",
                         "type": 119
                       }
                     ],
                     "npsDescription": null,
                     "sourceAmount": null,
                     "type": null
                   },
                   "taxBands": [
                     {
                       "bandType": "B",
                       "income": 19595,
                       "isBasicRate": true,
                       "lowerBand": 0,
                       "rate": 20,
                       "tax": 3919,
                       "taxCode": "BR",
                       "upperBand": 32000
                     }
                   ],
                   "totalIncome": {
                     "amount": 30595,
                     "iabdSummaries": [
                       {
                         "amount": 30595,
                         "defaultEstimatedPay": null,
                         "employmentId": 1,
                         "npsDescription": "New Estimated Pay",
                         "type": 27
                       }
                     ],
                     "npsDescription": null,
                     "sourceAmount": null,
                     "type": null
                   },
                   "totalTax": 3919,
                   "totalTaxableIncome": 19595
                 }
               }"""

            val unmarshalledTestClass = Json.parse(npsJson).as[Map[TaxObject.Type.Value, TaxDetail]]

            unmarshalledTestClass must not be empty
            val taxDetail = unmarshalledTestClass.get(TaxObject.Type.NonSavings)

            taxDetail mustEqual Some(TaxDetail(
              totalTax = Some(3919),
              totalTaxableIncome = Some(19595),
              totalIncome = Some(30595),
              taxBands = List(
                TaxBand(bandType = Some("pa"), income = 11000, tax = 0, rate = 0),
                TaxBand(
                  bandType = Some("B"),
                  code = None,
                  income = 19595,
                  tax = 3919,
                  lowerBand = Some(0),
                  upperBand = Some(32000),
                  rate = 20)
              )
            ))
          }
        }
      }
    }

    "provide Json formatting of Income" when {

      "marshall an Income into a JSValue" in {

        val json = Json
          .parse(
            """{
              |"employmentId":1,
              |"employmentType":1,
              |"employmentStatus":3,
              |"employmentTaxDistrictNumber":1,
              |"employmentPayeRef":"000",
              |"pensionIndicator":false,
              |"jsaIndicator":false,
              |"otherIncomeSourceIndicator":false,
              |"name":"name",
              |"endDate":"12/12/2017",
              |"worksNumber":"1234",
              |"taxCode":"AB1234",
              |"potentialUnderpayment":20.2,
              |"employmentRecord":{
              |"employerName":"EMPLOYER1",
              |"employmentType":1,
              |"sequenceNumber":1,
              |"worksNumber":"1234",
              |"taxDistrictNumber":"1",
              |"iabds":[{"grossAmount":10,"type":1,"source":16,"typeDescription":"dummyDescription","employmentSequenceNumber":32}],
              |"cessationPayThisEmployment":2200.22,
              |"startDate":"12/12/2017"},
              |"basisOperation":"Week1Month1"}""".stripMargin
          )

        json.as[Income] mustBe testIncome
        Json.toJson(testIncome) mustBe json
      }

      "marshall an Income into a JSValue for null empId and tax district number" in {

        val json = Json
          .parse(
            """{
              |"employmentId":null,
              |"employmentType":2,
              |"employmentStatus":3,
              |"employmentTaxDistrictNumber":null,
              |"employmentPayeRef":"000",
              |"pensionIndicator":false,
              |"jsaIndicator":false,
              |"otherIncomeSourceIndicator":false,
              |"name":"name",
              |"endDate":"12/12/2017",
              |"worksNumber":"1234",
              |"taxCode":"AB1234",
              |"potentialUnderpayment":20.2,
              |"employmentRecord":{
              |"employerName":"EMPLOYER1",
              |"employmentType":1,
              |"sequenceNumber":1,
              |"worksNumber":"1234",
              |"taxDistrictNumber":"1",
              |"iabds":[{"grossAmount":10,"type":1,"source":16,"typeDescription":"dummyDescription","employmentSequenceNumber":32}],
              |"cessationPayThisEmployment":2200.22,
              |"startDate":"12/12/2017"},
              |"basisOperation":"Week1Month1"}""".stripMargin
          )

        json.as[Income] mustBe testIncomeNoEmpIdTaxDistrict
        Json.toJson(testIncomeNoEmpIdTaxDistrict) mustBe json
      }

      "unmarshall a Json Income string into an Income model" in {

        val incomeJson =
          """{
                              "employmentId":1,
                              "employmentType":1,
                              "employmentStatus":2,
                              "employmentTaxDistrictNumber":1,
                              "employmentPayeRef":"000",
                              "pensionIndicator":false,
                              "jsaIndicator":false,
                              "otherIncomeSourceIndicator":false,
                              "name":"name",
                              "endDate":"12/12/2017",
                              "worksNumber":"1234",
                              "taxCode":"AB1234",
                              "potentialUnderpayment":20.2,
                              "employmentRecord":{
                              "employerName":"EMPLOYER1",
                              "employmentType":1,
                              "sequenceNumber":1,
                              "worksNumber":"1234",
                              "taxDistrictNumber":"1",
                              "iabds":[{"grossAmount":10,"type":1,"source":16,"typeDescription":"dummyDescription","employmentSequenceNumber":32}],
                              "cessationPayThisEmployment":2200.22,
                              "startDate":"12/12/2017"}
                              }"""

        val unmarshalledTestClass = Json.parse(incomeJson).as[Income]
        unmarshalledTestClass mustEqual testIncome.copy(basisOperation = None)
      }

      "fail to unmarshall a Json Income string for which the Income Type cannot be derived" in {
        val incomeJson =
          """{
                              "employmentId":1,
                              "employmentType":1,
                              "employmentStatus":2,
                              "employmentTaxDistrictNumber":1,
                              "employmentPayeRef":"000",
                              "pensionIndicator":true,
                              "jsaIndicator":true,
                              "otherIncomeSourceIndicator":true,
                              "name":"name",
                              "endDate":"12/12/2017",
                              "worksNumber":"1234",
                              "taxCode":"AB1234",
                              "potentialUnderpayment":20.2,
                              "employmentRecord":{
                              "employerName":"EMPLOYER1",
                              "employmentType":1,
                              "sequenceNumber":1,
                              "worksNumber":"1234",
                              "taxDistrictNumber":"1",
                              "iabds":[{"grossAmount":10,"type":1,"source":16,"typeDescription":"dummyDescription","employmentSequenceNumber":32}],
                              "cessationPayThisEmployment":2200.22,
                              "startDate":"12/12/2017"}
                              }"""

        val exception = intercept[IllegalArgumentException] {
          Json.parse(incomeJson).as[Income]
        }
        exception.getMessage mustEqual """Unknown Income Type (jsa:true, pension:true, other:true)"""
      }
    }

    "provide Json formatting of NpsEmployment" when {
      "marshall an NpsEmployment into a JSValue" in {
        val jsonObj = Json.toJson(testNpsEmployment)
        jsonObj.toString() mustBe stripFormatting(
          """{
            |"employerName":"EMPLOYER1",
            |"employmentType":1,
            |"sequenceNumber":1,
            |"worksNumber":"1234",
            |"taxDistrictNumber":"1",
            |"iabds":[{"grossAmount":10,"type":1,"source":16,"typeDescription":"dummyDescription","employmentSequenceNumber":32}],
            |"cessationPayThisEmployment":2200.22,
            |"startDate":"12/12/2017"}""".stripMargin)
      }

      "unmarshall a Json NpsEmployment string into an NpsEmployment model" in {
        val npsEmploymentJson =
          """{
              "employerName":"EMPLOYER1",
              "employmentType":1,
              "sequenceNumber":1,
              "worksNumber":"1234",
              "taxDistrictNumber":"1",
              "iabds":[{"grossAmount":10,"type":1,"source":16,"typeDescription":"dummyDescription","employmentSequenceNumber":32}],
              "cessationPayThisEmployment":2200.22,
              "startDate":"12/12/2017"}"""

        val unmarshalledTestClass = Json.parse(npsEmploymentJson).as[NpsEmployment]
        unmarshalledTestClass mustEqual testNpsEmployment
      }
    }

    "provide Json formatting of TaxAccount" when {
      "correctly format a TaxAccount" in {
        val taxAccount = TaxAccount(None, None, BigDecimal(1))
        Json.toJson(taxAccount).as[TaxAccount] mustBe taxAccount
      }

      "unmarshall a Json TaxAccount string into a TaxAccount model" in {
        val taxAccountJson =
          """{
                "taxAcccountId":12345,
                "date":"12/12/2017",
                "totalEstTax":12000.32,
                "totalLiability":{
                "nonSavings":
                {"taxBands":[
                {"bandType":"dummyType","code":"ABCD123","income":33000,"tax":8000,"lowerBand":5000,"upperBand":20000,"rate":33},
                {"bandType":"dummyType","code":"ABCD123","income":33000,"tax":8000,"lowerBand":5000,"upperBand":20000,"rate":33}],
                "totalTax":123.1,
                "totalTaxableIncome":999.1,
                "totalIncome":333.1}},
                "incomeSources":[{
                "employmentId":1,
                "employmentType":1,
                "employmentStatus":2,
                "employmentTaxDistrictNumber":1,
                "employmentPayeRef":"000",
                "pensionIndicator":false,
                "jsaIndicator":false,
                "otherIncomeSourceIndicator":false,
                "name":"name",
                "endDate":"12/12/2017",
                "worksNumber":"1234",
                "taxCode":"AB1234",
                "potentialUnderpayment":20.2,
                "employmentRecord":{
                "employerName":"EMPLOYER1",
                "employmentType":1,
                "sequenceNumber":1,
                "worksNumber":"1234",
                "taxDistrictNumber":"1",
                "iabds":[{"grossAmount":10,"type":1,"source":16,"typeDescription":"dummyDescription","employmentSequenceNumber":32}],
                "cessationPayThisEmployment":2200.22,
                "startDate":"12/12/2017"} }],
                "iabds":[{"grossAmount":10,"type":1,"source":16,"typeDescription":"dummyDescription","employmentSequenceNumber":32}]}"""

        val unmarshalledTestClass = Json.parse(taxAccountJson).as[TaxAccount]

        unmarshalledTestClass mustEqual testTaxAccount.copy(
          incomes = List(testIncome.copy(basisOperation = None)),
          taxObjects = Map(testLiabilityMapKey -> testLiabilityMapValue.copy(totalIncome = None)))
      }
    }
  }

  private def extractErrorsPerPath(exception: JsResultException): Seq[String] =
    for {
      (path: JsPath, errors: Seq[JsonValidationError]) <- exception.errors
      error: JsonValidationError                       <- errors
      message: String                                  <- error.messages
    } yield {
      path.toString() + " -> " + message
    }

  private def stripFormatting(string: String): String =
    string.stripMargin.replaceAll("\\n+", "")

  private val fixedDate = LocalDate.parse("2017-12-12")

  private val testTaxBand = TaxBand(
    bandType = Some("dummyType"),
    code = Some("ABCD123"),
    income = 33000,
    tax = 8000,
    lowerBand = Some(5000),
    upperBand = Some(20000),
    rate = 33)

  private val testIabd = Iabd(
    amount = 10,
    iabdType = GiftAidPayments,
    source = Letter,
    description = "dummyDescription",
    employmentSequence = Some(32))

  private val iabdList = List(testIabd, testIabd, testIabd)

  private val testComponent = Component(
    amount = 34000.30,
    sourceAmount = Some(32000.70),
    iabds = List(testIabd)
  )

  private val testLiabilityMapKey = TaxObject.Type.NonSavings

  private val testLiabilityMapValue = TaxDetail(
    totalTax = Some(123.1),
    totalTaxableIncome = Some(999.1),
    totalIncome = Some(333.1),
    taxBands = List(testTaxBand, testTaxBand))

  private val testLiabilityMapValueEmpty = TaxDetail(
    totalTax = None,
    totalTaxableIncome = None,
    totalIncome = Some(333.1),
    taxBands = List(testTaxBand, testTaxBand))

  private val testLiabilityMapValueEmptyTaxBands =
    TaxDetail(totalTax = Some(123.1), totalTaxableIncome = Some(999.1), totalIncome = Some(333.1), taxBands = Nil)

  private val testNpsEmployment = NpsEmployment(
    employerName = Some("EMPLOYER1"),
    isPrimary = true,
    sequenceNumber = 1,
    worksNumber = Some("1234"),
    districtNumber = 1,
    iabds = List(testIabd),
    cessationPay = Some(2200.22),
    start = fixedDate
  )

  private val testIncome = Income(
    employmentId = Some(1),
    isPrimary = true,
    incomeType = IncomeType.Employment,
    status = Status(Some(1), ceased = Some(fixedDate)),
    taxDistrict = Some(1),
    payeRef = "000",
    name = "name",
    worksNumber = Some("1234"),
    taxCode = "AB1234",
    potentialUnderpayment = 20.20,
    employmentRecord = Some(testNpsEmployment),
    basisOperation = Some(BasisOperation.Week1Month1)
  )

  private val testIncomeNoEmpIdTaxDistrict = Income(
    employmentId = None,
    isPrimary = false,
    incomeType = IncomeType.Employment,
    status = Status(Some(1), ceased = Some(fixedDate)),
    taxDistrict = None,
    payeRef = "000",
    name = "name",
    worksNumber = Some("1234"),
    taxCode = "AB1234",
    potentialUnderpayment = 20.20,
    employmentRecord = Some(testNpsEmployment),
    basisOperation = Some(BasisOperation.Week1Month1)
  )

  private val testTaxAccount = TaxAccount(
    id = Some(12345),
    date = Some(fixedDate),
    tax = 12000.32,
    taxObjects = Map(testLiabilityMapKey -> testLiabilityMapValue),
    incomes = List(testIncome),
    freeIabds = List(testIabd)
  )

}

/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.rti

import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsResultException, Json, JsonValidationError}
import uk.gov.hmrc.tai.model.rti.QaData._

import scala.util.{Success, Try}

class RtiPackageSpec extends PlaySpec {

  "package" must {
    "successfully use implicit Local date formats" when {

      "Parse received date" in {
        val jsonContent = """{"rcvdDate":"2016-07-29"}""".stripMargin
        val parsedJson: RtiEyu = Json.parse(jsonContent).as[RtiEyu]
        parsedJson.rcvdDate must be(new LocalDate(2016, 7, 29))
      }

      "throw error when trying to parse invalid received date" in {
        val jsonContent = """{"rcvdDate":"201-2013-0000"}""".stripMargin
        val ex = intercept[JsResultException] {
          Json.parse(jsonContent).as[RtiEyu]
        }

        val msg: Seq[String] = for {
          (_, validations)                <- ex.errors
          validation: JsonValidationError <- validations
          messages: String                <- validation.messages
        } yield {
          messages
        }

        msg mustNot be(Nil)
        msg.map { validationError =>
          validationError must be("""Invalid date format [yyyy-MM-dd]: "201-2013-0000"""")
        }
      }

      "convert received date object to json" in {
        val rti = RtiEyu(None, None, None, (new LocalDate(2016, 7, 29)))
        Json.toJson(rti).toString must be(
          """{"optionalAdjustmentAmount":[],"niLettersAndValues":[{"niFigure":[]}],"rcvdDate":"2016-07-29"}""")
      }

    }

    "successfully use implicit RTI Eyu formats" when {

      "EYU object need to be converted from json" in {
        val jsonContent =
          """{
          "optionalAdjustmentAmount": [
            {
              "type": "TaxablePayDelta",
              "amount": 10.99
            },
            {
              "type": "TotalTaxDelta",
              "amount": 15.99
            }
          ],
          "niLettersAndValues": [
            {
              "niFigure": [
                {
                  "type": "EmpeeContribnsDelta",
                  "amount": 600.99
                }
              ]
            }
          ],
          "rcvdDate":"2016-07-29"
        }"""

        val parsedJson: RtiEyu = Json.parse(jsonContent).as[RtiEyu]
        parsedJson.totalTaxDelta must be(Some(BigDecimal("15.99")))
        parsedJson.taxablePayDelta must be(Some(BigDecimal("10.99")))
        parsedJson.empeeContribnsDelta must be(Some(BigDecimal("600.99")))
        parsedJson.rcvdDate must be(new LocalDate(2016, 7, 29))
      }

      "Parse EYU Data and get one field" in {
        val jsonContent =
          """{
          "optionalAdjustmentAmount": [ {
              "type": "TaxablePayDelta",
              "amount": 10.99
            }
          ],
          "rcvdDate":"2016-07-29"
        }"""

        val parsedJson: RtiEyu = Json.parse(jsonContent).as[RtiEyu]
        parsedJson.taxablePayDelta must be(Some(BigDecimal("10.99")))
        parsedJson.totalTaxDelta must be(None)
        parsedJson.empeeContribnsDelta must be(None)
      }

      "Parse EYU Data and get two fields" in {

        val jsonContent =
          """{
          "optionalAdjustmentAmount": [
            {
              "type": "TaxablePayDelta",
              "amount": 10.99
            },
            {
              "type": "TotalTaxDelta",
              "amount": 15.99
            }
          ],
          "rcvdDate":"2016-07-29"
        }"""

        val parsedJson: RtiEyu = Json.parse(jsonContent).as[RtiEyu]
        parsedJson.taxablePayDelta must be(Some(BigDecimal("10.99")))
        parsedJson.totalTaxDelta must be(Some(BigDecimal("15.99")))
        parsedJson.empeeContribnsDelta must be(None)
      }

      "Get all EYU Fields from main Json" in {
        val rtiData = data.RTIData.getRtiData
        rtiData mustNot be(null)

        val employments = rtiData.employments
        employments mustNot be(Nil)

        val rtiEyu = employments.map { employment =>
          employment.eyu
        }

        rtiEyu mustNot be(Nil)
        val eyuList = rtiEyu(0)

        eyuList mustNot be(Nil)
        val eyu = eyuList(0)

        eyu.taxablePayDelta must be(Some(-600.99))
        eyu.totalTaxDelta must be(Some(-10.99))
        eyu.empeeContribnsDelta must be(Some(-5.99))
      }

      "convert EYU object having taxablePayDelta only to json " in {
        val rti = RtiEyu(Some(10), None, None, (new LocalDate(2016, 7, 29)))
        Json.toJson(rti).toString() must be(
          """{"optionalAdjustmentAmount":[{"type":"TaxablePayDelta","amount":10}],"niLettersAndValues":[{"niFigure":[]}],"rcvdDate":"2016-07-29"}""")
      }

      "convert EYU object having taxablePayDelta and totalTaxDelta to json " in {
        val rti = RtiEyu(Some(10), Some(10), None, (new LocalDate(2016, 7, 29)))
        Json.toJson(rti).toString() must be(
          """{"optionalAdjustmentAmount":[{"type":"TaxablePayDelta","amount":10},{"type":"TotalTaxDelta","amount":10}],"niLettersAndValues":[{"niFigure":[]}],"rcvdDate":"2016-07-29"}""")
      }

      "convert EYU object to json " in {
        val rti = RtiEyu(Some(10), Some(10), Some(10), (new LocalDate(2016, 7, 29)))
        Json.toJson(rti).toString() must be(
          """{"optionalAdjustmentAmount":[{"type":"TaxablePayDelta","amount":10},{"type":"TotalTaxDelta","amount":10}],"niLettersAndValues":[{"niFigure":[{"type":"EmpeeContribnsDelta","amount":10}]}],"rcvdDate":"2016-07-29"}""")
      }

    }

    "successfully use implicit employments formats" when {

      "employment Json is parsed with empty payment list" in {
        val json =
          """
              {
                  "empRefs":{
                      "officeNo": "002",
                      "payeRef": "AA00000",
                      "aoRef": "00000"
                  },
                  "payments":{},
                  "sequenceNumber": 3
              }"""

        val parsedJson: RtiEmployment = Json.parse(json).as[RtiEmployment]
        parsedJson.officeRefNo must be("002")
        parsedJson.payeRef must be("AA00000")
        parsedJson.sequenceNumber must be(3)
      }

      "employment object is converted from json" in {
        val json: String =
          """{"empRefs":{"officeNo":"HMRC","payeRef":"12345","aoRef":"00000"},"payments":{"inYear":[],"eyu":[]},"sequenceNumber":1}"""
        val employmentObject = RtiEmployment("HMRC", "12345", "00000", Nil, Nil, None, 1)
        Json.parse(json).as[RtiEmployment] mustBe employmentObject
        Json.toJson(employmentObject) mustBe Json.parse(json)
      }
    }

    "successfully use implicit payments formats" when {

      val records = Seq("15-16", "16-17").flatMap(
        year =>
          json(year).map {
            case (nino, json) => (year, nino, Try(json.as[RtiData]))
        }
      )

      "monetary amount map is parsed from the JSON" in {
        val parsedJson = Json
          .parse(
            """[{"type": "TaxablePayYTD","amount": 2135.41}]"""
          )
          .as[Map[String, BigDecimal]]
        parsedJson("TaxablePayYTD") must be(BigDecimal("2135.41"))
      }

      "payment Json is parsed" in {
        val jsonContent =
          """
            {
              "econ" : "E3534540R",
              "payFreq" : "M1",
              "periodsCovered" : 1,
              "aggregatedEarnings" : true,
              "hoursWorked" : "Up to 15.99",
              "mandatoryMonetaryAmount" : [ {
                "type" : "TaxablePayYTD",
                "amount" : 2135.41
              }, {
                "type" : "TotalTaxYTD",
                "amount" : 427.8
              }, {
                "type" : "TaxDeductedOrRefunded",
                "amount" : 427.8
              }, {
                "type" : "TaxablePay",
                "amount" : 2135.41
              } ],
              "optionalMonetaryAmount" : [ {
                "type" : "PayAfterStatDedns",
                "amount" : 666.66
              } ],
              "starter" : {
                "startDate" : "2011-04-12",
                "startDec" : "C"
              },
              "niLettersAndValues" : [ {
                "niLetter" : "X",
                "niFigure" : [ {
                  "type" : "AtLELYTD",
                  "amount" : 0
                }, {
                  "type" : "EmpeeContribnsInPd",
                  "amount" : 150
                }, {
                  "type" : "EmpeeContribnsYTD",
                  "amount" : 300
                }, {
                  "type" : "GrossEarningsForNICsInPd",
                  "amount" : 833.33
                }, {
                  "type" : "GrossEarningsForNICsYTD",
                  "amount" : 0
                }, {
                  "type" : "LELtoPTYTD",
                  "amount" : 0
                }, {
                  "type" : "PTtoUAPYTD",
                  "amount" : 0
                }, {
                  "type" : "TotalEmpNICInPd",
                  "amount" : 0
                }, {
                  "type" : "TotalEmpNICYTD",
                  "amount" : 0
                }, {
                  "type" : "UAPtoUELYTD",
                  "amount" : 0
                } ]
              } ],
              "pmtDate" : "2013-10-25",
              "rcvdDate" : "2013-10-24",
              "pmtConfidence" : 4,
              "taxYear" : "13-14"
            }"""

        val parsedJson: RtiPayment = Json.parse(jsonContent).as[RtiPayment]

        parsedJson.payFrequency must be(PayFrequency.Monthly)
        parsedJson.occupationalPensionAmount.isDefined must be(false)
        parsedJson.paidOn must be(new LocalDate(2013, 10, 25))
        parsedJson.submittedOn must be(new LocalDate(2013, 10, 24))
        parsedJson.nicPaid must be(Some(150))
        parsedJson.nicPaidYTD must be(Some(300))
      }

      "payment json with no optionalMonetaryAmount is parsed" in {
        val jsonContent =
          """{
              "econ": "E3534540R",
              "payFreq": "M1",
              "periodsCovered": 1,
              "aggregatedEarnings": true,
              "hoursWorked": "Up to 15.99",
              "mandatoryMonetaryAmount": [
                  {"type": "TaxablePayYTD","amount": 2135.41},
                  { "type": "TotalTaxYTD","amount": 427.8 },
                  {"type": "TaxDeductedOrRefunded","amount": 427.8},
                  {"type": "TaxablePay","amount": 2135.41}],
              "taxCode": {"value": "BR"},
              "starter": {"startDate": "2011-04-12","startDec": ""},
              "niLettersAndValues": [
                  {"niLetter": "X","niFigure": [
                      {"type": "AtLELYTD", "amount": 0 },
                      {"type": "EmpeeContribnsInPd","amount": 0},
                      {"type": "EmpeeContribnsYTD","amount": 0},
                      {"type": "GrossEarningsForNICsInPd","amount": 833.33},
                      {"type": "GrossEarningsForNICsYTD","amount": 0},
                      {"type": "LELtoPTYTD","amount": 0},
                      {"type": "PTtoUAPYTD", "amount": 0 },
                      {"type": "TotalEmpNICInPd","amount": 0},
                      {"type": "TotalEmpNICYTD","amount": 0 },
                      {"type": "UAPtoUELYTD","amount": 0}
                  ]}
              ],
              "pmtDate": "2013-10-25",
              "rcvdDate": "2013-10-24",
              "pmtConfidence": 4,
              "taxYear": "13-14"
            }"""

        val parsedJson: RtiPayment = Json.parse(jsonContent).as[RtiPayment]
        parsedJson.taxed must be(BigDecimal("427.8"))
        parsedJson.taxedYTD must be(BigDecimal("427.8"))
        parsedJson.taxablePay must be(BigDecimal("2135.41"))
        parsedJson.taxablePayYTD must be(BigDecimal("2135.41"))

        parsedJson.payFrequency must be(PayFrequency.Monthly)
        parsedJson.paidOn must be(new LocalDate(2013, 10, 25))
        parsedJson.submittedOn must be(new LocalDate(2013, 10, 24))
      }

      "payment json with no EmpeeContribnsInPd and EmpeeContribnsYTD is parsed" in {
        val jsonContent =
          """{
              "econ": "E3534540R",
              "payFreq": "M1",
              "periodsCovered": 1,
              "aggregatedEarnings": true,
              "hoursWorked": "Up to 15.99",
              "mandatoryMonetaryAmount": [
                  {"type": "TaxablePayYTD","amount": 2135.41},
                  { "type": "TotalTaxYTD","amount": 427.8 },
                  {"type": "TaxDeductedOrRefunded","amount": 427.8},
                  {"type": "TaxablePay","amount": 2135.41}],
              "taxCode": {"value": "BR"},
              "starter": {"startDate": "2011-04-12","startDec": ""},
              "niLettersAndValues": [
                  {"niLetter": "X","niFigure": [
                      {"type": "AtLELYTD", "amount": 0 },
                      {"type": "GrossEarningsForNICsInPd","amount": 833.33},
                      {"type": "GrossEarningsForNICsYTD","amount": 0},
                      {"type": "LELtoPTYTD","amount": 0},
                      {"type": "PTtoUAPYTD", "amount": 0 },
                      {"type": "TotalEmpNICInPd","amount": 0},
                      {"type": "TotalEmpNICYTD","amount": 0 },
                      {"type": "UAPtoUELYTD","amount": 0}
                  ]}
              ],
              "pmtDate": "2013-10-25",
              "rcvdDate": "2013-10-24",
              "pmtConfidence": 4,
              "taxYear": "13-14"
            }"""

        val parsedJson: RtiPayment = Json.parse(jsonContent).as[RtiPayment]
        parsedJson.taxed must be(BigDecimal("427.8"))
        parsedJson.taxedYTD must be(BigDecimal("427.8"))
        parsedJson.taxablePay must be(BigDecimal("2135.41"))
        parsedJson.taxablePayYTD must be(BigDecimal("2135.41"))

        parsedJson.payFrequency must be(PayFrequency.Monthly)
        parsedJson.paidOn must be(new LocalDate(2013, 10, 25))
        parsedJson.submittedOn must be(new LocalDate(2013, 10, 24))
        parsedJson.nicPaid must be(None)
        parsedJson.nicPaidYTD must be(None)
      }

      "RtiPayment object is converted to json" in {
        val rtiPayment =
          RtiPayment(PayFrequency.Monthly, new LocalDate(2016, 11, 23), new LocalDate(2016, 12, 20), 200, 300, 100, 200)
        Json.toJson(rtiPayment).toString must be(
          """{"payFreq":"M1","pmtDate":"2016-11-23","rcvdDate":"2016-12-20","mandatoryMonetaryAmount":[{"type":
              "TaxablePayYTD","amount":300},{"type":"TotalTaxYTD","amount":200},{"type":"TaxablePay","amount":200},{"type":"TaxDeductedOrRefunded","amount":100}],
              "optionalMonetaryAmount":[],"payId":null,"occPenInd":false,"irrEmp":false,"weekNo":null,"monthNo":null,"niLettersAndValues":[{"niFigure":[]}]}
              """.replaceAll("\\n+", "").replaceAll("\\s+", ""))
      }

      "valid json are parsed" in {
        records.collect {
          case (year, nino, exception) if exception.isFailure => (year, nino, exception)
        } must be(Nil)
      }

      "successful future json response is parsed" in {
        records.foreach {
          case (_, _, Success(json)) => Json.toJson(json).as[RtiData] must be(json)
          case record =>
            val (year, nino, json) = record
            fail("Not able to parse json " + (year, nino).toString)
        }
      }
    }
  }
}

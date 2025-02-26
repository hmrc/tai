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

package uk.gov.hmrc.tai.model.rti

import play.api.libs.json.*

import java.io.*
import scala.io.Source.*
import scala.util.*

object QaData {

  def jsonFile[T](
    f: String
  )(implicit m: Format[T]): Option[T] =
    Option(getClass.getResourceAsStream(f))
      .map { x =>
        Json.parse(
          fromInputStream(x).getLines().mkString
        )
      }
      .map { x =>
        Json.fromJson[T](x) match {
          case JsSuccess(r, _) => r
          case JsError(e) =>
            throw new IllegalArgumentException(
              e.map(_.toString).mkString("\n")
            )
        }
      }

  def obj(year: String)(nino: String): RtiData = json(year)(nino).as[RtiData]

  def paymentDetailsForYear(year: String)(fileName: String): JsValue = json(year)(fileName)

  def json(year: String): Map[String, JsValue] = {
    val dir = new File(s"test/data/rti/$year")
    val jsonFiles = dir.listFiles.filter(_.getName.endsWith(".json"))
    jsonFiles.map { file =>
      (
        file.getName.takeWhile(_ != '.'),
        Json.parse(
          fromFile(file).getLines().mkString
        )
      )
    }
  }.toMap

  def prettyPrint(year: String)(nino: String): String = {

    def printEmployment(emp: RtiEmployment): String = {
      val table = emp.payments.sorted
        .map { x =>
          Seq("", x.paidOn, x.taxablePay, x.taxablePayYTD, x.taxed, x.taxedYTD).mkString("|")
        }
        .mkString("\n")
      s"""
** Employment (${emp.sequenceNumber})
- Office Reference Number :: ${emp.officeRefNo}
- PAYE :: ${emp.payeRef}
- Account Office :: ${emp.accountOfficeReference}

      | Paid On | Taxable Pay | Pay YTD | Taxed | Taxed YTD |
      |-
$table
"""
    }

    s"* $nino ($year)" +
      Try(obj(year)(nino))
        .map {
          _.employments.map(printEmployment).mkString
        }
        .recover { case e => e.toString }
        .toOption
        .get
  }

  val prettyPrintAll: String =
    Seq("15-16", "16-17")
      .map { y =>
        json(y).keys
          .map { n =>
            prettyPrint(y)(n)
          }
          .mkString("\n")
      }
      .mkString("\n\n")
}

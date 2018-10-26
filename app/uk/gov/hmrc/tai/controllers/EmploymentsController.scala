/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.tai.controllers

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{BadRequestException, NotFoundException}
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.model.api.{ApiFormats, ApiResponse, EmploymentCollection}
import uk.gov.hmrc.tai.model.domain.{AddEmployment, Employment, EndEmployment, IncorrectEmployment}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.EmploymentService
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate

@Singleton
class EmploymentsController @Inject()(employmentService: EmploymentService,
                                      authentication: AuthenticationPredicate)
  extends BaseController
  with ApiFormats {

  def employments(nino: Nino, year: TaxYear): Action[AnyContent] = authentication.async { implicit request =>

    employmentService.employments(nino, year).map { employments: Seq[Employment] =>

      print(Console.YELLOW + "employments: \n" + prettyPrint(employments,depth = 2) + Console.WHITE)
      Ok(Json.toJson(ApiResponse(EmploymentCollection(employments), Nil)))

    }.recover {
      case _: NotFoundException => NotFound
      case ex:BadRequestException => BadRequest(ex.getMessage)
      case _ => InternalServerError
    }
  }

  def employment(nino: Nino, id: Int): Action[AnyContent] = authentication.async { implicit request =>
    employmentService.employment(nino, id).map {
      case Some(employment) => Ok(Json.toJson(ApiResponse(employment, Nil)))
      case None => NotFound
    }.recover {
      case _: NotFoundException => NotFound
      case _ => InternalServerError
    }
  }

  def endEmployment(nino: Nino, id: Int): Action[JsValue] = authentication.async(parse.json) {
    implicit request =>
      withJsonBody[EndEmployment] {
        endEmployment =>
          employmentService.endEmployment(nino, id, endEmployment) map (envelopeId => {
            Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
          })
      }
  }

  def addEmployment(nino: Nino): Action[JsValue] =  authentication.async(parse.json) {
    implicit request =>
      withJsonBody[AddEmployment] {
        employment =>
          employmentService.addEmployment(nino, employment) map (envelopeId => {
            Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
          })
      }
  }

  def incorrectEmployment(nino: Nino, id: Int): Action[JsValue] =  authentication.async(parse.json) {
    implicit request =>
      withJsonBody[IncorrectEmployment] {
        employment =>
          employmentService.incorrectEmployment(nino, id, employment) map (envelopeId => {
            Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
          })
      }
  }

  def updatePreviousYearIncome(nino: Nino, taxYear: TaxYear): Action[JsValue] =  authentication.async(parse.json) {
    implicit request =>
      withJsonBody[IncorrectEmployment] {
        employment =>
          employmentService.updatePreviousYearIncome(nino, taxYear, employment) map (envelopeId => {
            Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
          })
      }
  }


  private def prettyPrint(a: Any, indentSize: Int = 2, maxElementWidth: Int = 30, depth: Int = 0): String = {
    val indent = " " * depth * indentSize
    val fieldIndent = indent + (" " * indentSize)
    val thisDepth = prettyPrint(_: Any, indentSize, maxElementWidth, depth)
    val nextDepth = prettyPrint(_: Any, indentSize, maxElementWidth, depth + 1)
    a match {
      // Make Strings look similar to their literal form.
      case s: String =>
        val replaceMap = Seq(
          "\n" -> "\\n",
          "\r" -> "\\r",
          "\t" -> "\\t",
          "\"" -> "\\\""
        )
        '"' + replaceMap.foldLeft(s) { case (acc, (c, r)) => acc.replace(c, r) } + '"'
      // For an empty Seq just use its normal String representation.
      case xs: Seq[_] if xs.isEmpty => xs.toString()
      case xs: Seq[_] =>
        // If the Seq is not too long, pretty print on one line.
        val resultOneLine = xs.map(nextDepth).toString()
        if (resultOneLine.length <= maxElementWidth) return resultOneLine
        // Otherwise, build it with newlines and proper field indents.
        val result = xs.map(x => s"\n$fieldIndent${nextDepth(x)}").toString()
        result.substring(0, result.length - 1) + "\n" + indent + ")"
      // Product should cover case classes.
      case p: Product =>
        val prefix = p.productPrefix
        // We'll use reflection to get the constructor arg names and values.
        val cls = p.getClass
        val fields = cls.getDeclaredFields.filterNot(_.isSynthetic).map(_.getName)
        val values = p.productIterator.toSeq
        // If we weren't able to match up fields/values, fall back to toString.
        if (fields.length != values.length) return p.toString
        fields.zip(values).toList match {
          // If there are no fields, just use the normal String representation.
          case Nil => p.toString
          // If there is just one field, let's just print it as a wrapper.
          case (_, value) :: Nil => s"$prefix(${thisDepth(value)})"
          // If there is more than one field, build up the field names and values.
          case kvps =>
            val prettyFields = kvps.map { case (k, v) => s"$fieldIndent$k = ${nextDepth(v)}" }
            // If the result is not too long, pretty print on one line.
            val resultOneLine = s"$prefix(${prettyFields.mkString(", ")})"
            if (resultOneLine.length <= maxElementWidth) return resultOneLine
            // Otherwise, build it with newlines and proper field indents.
            s"$prefix(\n${prettyFields.mkString(",\n")}\n$indent)"
        }
      // If we haven't specialized this type, just use its toString.
      case _ => a.toString
    }
  }
}


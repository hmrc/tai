/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.nps2

import org.joda.time.LocalDate
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.functional.syntax.{unlift, _}
import play.api.libs.json._
import uk.gov.hmrc.tai.model.nps2.Income.IncomeType
import uk.gov.hmrc.tai.model.tai.{AnnualAccount, JsonExtra}
import uk.gov.hmrc.tai.model.{TaxSummaryDetails, nps2}
import uk.gov.hmrc.tai.model.enums.BasisOperation.BasisOperation

trait NpsFormatter {

  implicit val log: Logger = LoggerFactory.getLogger(this.getClass)

  import uk.gov.hmrc.tai.model.nps2.TaxObject.Type.{Value => TaxObjectType}

  implicit val formatTaxBand: Format[nps2.TaxBand] = (
    (__ \ "bandType").formatNullable[String] and
      (__ \ "code").formatNullable[String] and
      (__ \ "income").formatNullable[BigDecimal].inmap[BigDecimal](_.getOrElse(0), Some(_)) and
      (__ \ "tax").formatNullable[BigDecimal].inmap[BigDecimal](_.getOrElse(0), Some(_)) and
      (__ \ "lowerBand").formatNullable[BigDecimal] and
      (__ \ "upperBand").formatNullable[BigDecimal] and
      (__ \ "rate").format[BigDecimal]
  )(nps2.TaxBand.apply, unlift(nps2.TaxBand.unapply))

  implicit val formatIabd: Format[Iabd] = (
    (__ \ "grossAmount").format[BigDecimal] and
      (__ \ "type").format[Int].inmap[IabdType](IabdType(_), _.code) and
      (__ \ "source").format[Int].inmap[IabdUpdateSource](IabdUpdateSource(_), _.code) and
      (__ \ "typeDescription")
        .formatNullable[String]
        .inmap[String](
          _.getOrElse(""),
          Some(_)
        ) and
      (__ \ "employmentSequenceNumber").formatNullable[Int]
  )(Iabd.apply, unlift(Iabd.unapply))

  implicit val formatIabdList: Format[List[Iabd]] =
    JsonExtra.bodgeList[Iabd]

  implicit val formatComponent: Format[Component] = (
    (__ \ "amount").format[BigDecimal] and
      (__ \ "sourceAmount").formatNullable[BigDecimal] and
      (__ \ "iabdSummaries").format[Seq[Iabd]]
  )(Component.apply, unlift(Component.unapply))

  val taxBandsWithPaBand
    : PartialFunction[(String, Option[Seq[nps2.TaxBand]], Option[BigDecimal]), Seq[nps2.TaxBand]] = {
    case (totalLiabilitySection, Some(taxBands), Some(paAmount)) if totalLiabilitySection != "untaxedInterest" =>
      Seq(nps2.TaxBand(bandType = Some("pa"), income = paAmount, tax = 0, rate = 0)) ++ taxBands
  }

  val paBandOnly: PartialFunction[(String, Option[Seq[nps2.TaxBand]], Option[BigDecimal]), Seq[nps2.TaxBand]] = {
    case (totalLiabilitySection, None, Some(paAmount)) if totalLiabilitySection != "untaxedInterest" =>
      Seq(nps2.TaxBand(bandType = Some("pa"), income = paAmount, tax = 0, rate = 0))
  }

  val taxBandsOnly: PartialFunction[(String, Option[Seq[nps2.TaxBand]], Option[BigDecimal]), Seq[nps2.TaxBand]] = {
    case (_, Some(taxBands), _) =>
      taxBands
  }

  val emptyTaxBand: PartialFunction[(String, Option[Seq[nps2.TaxBand]], Option[BigDecimal]), Seq[nps2.TaxBand]] = {
    case _ => Nil
  }

  val processLiabilities =
    taxBandsWithPaBand orElse
      paBandOnly orElse
      taxBandsOnly orElse
      emptyTaxBand

  implicit val formatliabilityMap: Format[Map[TaxObjectType, TaxDetail]] = {

    val fieldNames: Map[TaxObject.Type.Value, String] =
      TaxObject.Type.values.toSeq.map { x =>
        (x, x.toString.head.toLower + x.toString.tail)
      }.toMap

    new Format[Map[TaxObjectType, TaxDetail]] {
      def reads(json: JsValue): JsResult[Map[TaxObjectType, TaxDetail]] =
        JsSuccess {

          val x = fieldNames.mapValues { liabilityType =>
            val npsTaxBands = (json \ liabilityType \ "taxBands").asOpt[Seq[nps2.TaxBand]]
            val npsPAAmount = (json \ liabilityType \ "allowReliefDeducts" \ "amount").asOpt[BigDecimal]

            val taxBandsList = processLiabilities((liabilityType, npsTaxBands, npsPAAmount))

            TaxDetail(
              taxBands = taxBandsList.filter(_.income > 0),
              totalTax = (json \ liabilityType \ "totalTax").asOpt[BigDecimal],
              totalTaxableIncome = (json \ liabilityType \ "totalTaxableIncome").asOpt[BigDecimal],
              totalIncome = (json \ liabilityType \ "totalIncome" \ "amount").asOpt[BigDecimal]
            )
          }

          x.filter(_._2.taxBands.nonEmpty)
        }

      def writes(data: Map[TaxObjectType, TaxDetail]): JsValue =
        JsObject(data.toSeq.map {
          case (f, v) if v.taxBands.nonEmpty =>
            val x = (
              fieldNames(f),
              JsObject(
                Seq(
                  ("taxBands", Json.toJson(v.taxBands)),
                  ("totalTax", v.totalTax.map(x => JsNumber(x)).getOrElse(JsNull)),
                  ("totalTaxableIncome", v.totalTaxableIncome.map(x => JsNumber(x)).getOrElse(JsNull)),
                  ("totalIncome", v.totalIncome.map(x => JsNumber(x)).getOrElse(JsNull))
                )))
            x
          case (f, _) => (fieldNames(f), JsObject(Nil))
        })
    }

  }

  val processJSAAllowanceIncomeType: PartialFunction[(Boolean, Boolean, Boolean), IncomeType.Value] = {
    case (true, false, _) => IncomeType.JobSeekersAllowance
  }
  val processPensionIncomeType: PartialFunction[(Boolean, Boolean, Boolean), IncomeType.Value] = {
    case (false, true, _) => IncomeType.Pension
  }
  val processOtherIncomeType: PartialFunction[(Boolean, Boolean, Boolean), IncomeType.Value] = {
    case (false, false, true) => IncomeType.OtherIncome
  }
  val processEmploymetIncomeType: PartialFunction[(Boolean, Boolean, Boolean), IncomeType.Value] = {
    case (false, false, false) => IncomeType.Employment
  }
  val throwExceptionForUnknownIncomeType: PartialFunction[(Boolean, Boolean, Boolean), Nothing] = {
    case (jsa, pen, oth) =>
      throw new IllegalArgumentException(s"Unknown Income Type (jsa:$jsa, pension:$pen, other:$oth)")
  }

  val processIncomeTypes =
    processJSAAllowanceIncomeType orElse
      processPensionIncomeType orElse
      processOtherIncomeType orElse
      processEmploymetIncomeType orElse
      throwExceptionForUnknownIncomeType

  implicit val formatIncome: Format[Income] = Format(
    new Reads[Income] {
      def reads(json: JsValue) = {
        def getVal(name: String): Boolean = (json \ name).asOpt[Boolean].getOrElse(false)

        val iType =
          processIncomeTypes((getVal("jsaIndicator"), getVal("pensionIndicator"), getVal("otherIncomeSourceIndicator")))

        JsSuccess {
          Income(
            (json \ "employmentId").asOpt[Int],
            (json \ "employmentType").as[Int] == 1,
            iType,
            Income.Status(
              (json \ "employmentStatus").asOpt[Int],
              (json \ "endDate").asOpt[LocalDate]
            ),
            (json \ "employmentTaxDistrictNumber").asOpt[Int],
            (json \ "employmentPayeRef").asOpt[String].getOrElse(""),
            (json \ "name").asOpt[String].getOrElse(""),
            (json \ "worksNumber").asOpt[String],
            (json \ "taxCode").asOpt[String].getOrElse(""),
            (json \ "potentialUnderpayment").asOpt[BigDecimal].getOrElse(0),
            (json \ "employmentRecord").asOpt[NpsEmployment],
            (json \ "basisOperation").asOpt[BasisOperation]
          )
        }
      }
    },
    new Writes[Income] {
      def writes(v: Income) =
        JsObject(
          Seq(
            "employmentId" -> v.employmentId
              .map { x =>
                JsNumber(x)
              }
              .getOrElse {
                JsNull
              },
            "employmentType"              -> JsNumber(if (v.isPrimary) 1 else 2),
            "employmentStatus"            -> JsNumber(v.status.code),
            "employmentTaxDistrictNumber" -> v.taxDistrict.map(x => JsNumber(x)).getOrElse(JsNull),
            "employmentPayeRef"           -> JsString(v.payeRef),
            "pensionIndicator"            -> JsBoolean(v.incomeType == IncomeType.Pension),
            "jsaIndicator"                -> JsBoolean(v.incomeType == IncomeType.JobSeekersAllowance),
            "otherIncomeSourceIndicator"  -> JsBoolean(v.incomeType == IncomeType.OtherIncome),
            "name"                        -> JsString(v.name),
            "endDate" -> (v.status match {
              case Income.Ceased(end) => Json.toJson(end)
              case _                  => JsNull
            }),
            "worksNumber" -> v.worksNumber
              .map {
                JsString
              }
              .getOrElse {
                JsNull
              },
            "taxCode"               -> JsString(v.taxCode),
            "potentialUnderpayment" -> JsNumber(v.potentialUnderpayment),
            "employmentRecord" -> v.employmentRecord
              .map { x =>
                Json.toJson(x)
              }
              .getOrElse {
                JsNull
              }
          )) ++ v.basisOperation.fold(Json.obj())(x => Json.obj("basisOperation" -> x.toString))
    }
  )

  implicit val formatNpsEmployment: Format[NpsEmployment] = (
    (__ \ "employerName").formatNullable[String] and
      (__ \ "employmentType")
        .format[Int]
        .inmap[Boolean](
          _ == 1,
          x => if (x) 1 else 2
        ) and
      (__ \ "sequenceNumber").format[Int] and
      (__ \ "worksNumber").formatNullable[String] and
      (__ \ "taxDistrictNumber")
        .format[String]
        .inmap[Int](
          a => a.toInt,
          x => x.toString
        ) and
      (__ \ "iabds").formatNullable[List[Iabd]].inmap[List[Iabd]](_.getOrElse(Nil), Some(_)) and
      (__ \ "cessationPayThisEmployment").formatNullable[BigDecimal] and
      (__ \ "startDate").format[LocalDate]
  )(NpsEmployment.apply, unlift(NpsEmployment.unapply))

  implicit val formatTaxAccount: Format[TaxAccount] = (
    (__ \ "taxAcccountId").formatNullable[Long] and
      (__ \ "date").formatNullable[LocalDate] and
      (__ \ "totalEstTax").formatNullable[BigDecimal].inmap[BigDecimal](_.getOrElse(0), Some(_)) and
      (__ \ "totalLiability").format[Map[TaxObjectType, TaxDetail]] and
      (__ \ "incomeSources").formatNullable[Seq[Income]].inmap[Seq[Income]](_.getOrElse(Nil), Some(_)) and
      (__ \ "iabds").formatNullable[List[Iabd]].inmap[List[Iabd]](_.getOrElse(Nil), Some(_))
  )(TaxAccount.apply, unlift(TaxAccount.unapply))

  implicit val annualAccountFormats: Format[AnnualAccount] = Json.format[AnnualAccount]

  implicit val taxSummaryDetailsFormat: Format[TaxSummaryDetails] = Json.format[TaxSummaryDetails]

}

/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.domain

import play.api.Logging
import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import uk.gov.hmrc.tai.util.JsonHelper
import uk.gov.hmrc.tai.util.JsonHelper.readsTypeTuple

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.util.Try

case class IabdDetails(
  nino: Option[String] = None,
  employmentSequenceNumber: Option[Int] = None,
  source: Option[Int] = None,
  `type`: Option[Int] = None,
  receiptDate: Option[LocalDate] = None,
  captureDate: Option[LocalDate] = None,
  grossAmount: Option[BigDecimal] = None,
  netAmount: Option[BigDecimal] = None
)

object IabdDetails extends Logging {

  private val isoFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  private val npsFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  private val lenientLocalDateReads: Reads[LocalDate] = Reads {
    case JsString(s) =>
      def parse(fmt: DateTimeFormatter): Option[LocalDate] =
        Try(LocalDate.parse(s, fmt)).toOption

      parse(isoFmt)
        .orElse(parse(npsFmt))
        .map(JsSuccess(_))
        .getOrElse(JsError(s"Invalid date format: $s (expected yyyy-MM-dd or dd/MM/yyyy)"))

    case other => JsError(s"Invalid JSON for LocalDate: $other")
  }

  private val isoDateWrites: Writes[LocalDate] = Writes(d => JsString(d.format(isoFmt)))
  private val dmyDateWrites: Writes[LocalDate] = Writes(d => JsString(d.format(npsFmt)))

  private def removeNulls(js: JsObject): JsObject =
    JsObject(
      js.fields.collect {
        case (k, v: JsObject)      => k -> removeNulls(v)
        case (k, v) if v != JsNull => k -> v
      }
    )

  def sourceReads: Reads[Option[Int]] = Reads {
    case JsString(n) =>
      mapIabdSource.get(n) match {
        case Some(code) => JsSuccess(Some(code))
        case None =>
          val msg = s"Unknown iabd source: $n"
          logger.warn(msg, new RuntimeException(msg))
          JsSuccess(None)
      }
    case JsNumber(n) if n.isValidInt => JsSuccess(Some(n.toInt))
    case JsNull                      => JsSuccess(None)
    case _                           => JsError("Invalid iabd source")
  }

  private lazy val mapIabdSource: Map[String, Int] = Map(
    "Cutover"               -> 0,
    "P161"                  -> 1,
    "P161W"                 -> 2,
    "P9D"                   -> 3,
    "P50 CESSATION"         -> 4,
    "P50 UNEMPLOYMENT"      -> 5,
    "P52"                   -> 6,
    "P53A"                  -> 7,
    "P53B"                  -> 8,
    "P810"                  -> 9,
    "P85"                   -> 10,
    "P87"                   -> 11,
    "R27"                   -> 12,
    "R40"                   -> 13,
    "575T"                  -> 14,
    "TELEPHONE CALL"        -> 15,
    "LETTER"                -> 16,
    "EMAIL"                 -> 17,
    "AGENT CONTACT"         -> 18,
    "P11D (ECS)"            -> 19,
    "P46(CAR) (ECS)"        -> 20,
    "P11D (MANUAL)"         -> 21,
    "P46(CAR) (MANUAL)"     -> 22,
    "SA"                    -> 23,
    "OTHER FORM"            -> 24,
    "CALCULATION ONLY"      -> 25,
    "Annual Coding"         -> 26,
    "DWP Uprate"            -> 27,
    "Assessed P11D"         -> 28,
    "P11D/P9D Amended"      -> 29,
    "BULK EXPENSES"         -> 30,
    "ESA"                   -> 31,
    "Budget Coding"         -> 32,
    "SA Autocoding"         -> 33,
    "SPA AUTOCODING"        -> 34,
    "P46(DWP)"              -> 35,
    "P46(DWP) Uprated"      -> 36,
    "P46(PEN)"              -> 37,
    "ChB Online Service"    -> 38,
    "Internet"              -> 39,
    "Information Letter"    -> 40,
    "DWP Estimated JSA"     -> 41,
    "Payrolling BIK"        -> 42,
    "P53 (IYC)"             -> 43,
    "R40 (IYC)"             -> 44,
    "Lump Sum (IYC)"        -> 45,
    "Internet Calculated"   -> 46,
    "FPS(RTI)"              -> 47,
    "BBSI DI"               -> 48,
    "CALCULATED"            -> 49,
    "FWKS"                  -> 50,
    "Home Working Expenses" -> 51,
    "T&TSP"                 -> 52,
    "HICBC PAYE"            -> 53
  )

  val publicWrites: OWrites[IabdDetails] = OWrites[IabdDetails] { i =>
    removeNulls(
      Json.obj(
        "nino"        -> i.nino,
        "type"        -> i.`type`,
        "grossAmount" -> i.grossAmount,
        "source"      -> i.source,
        "receiptDate" -> i.receiptDate.map(d => Json.toJson(d)(dmyDateWrites)),
        "captureDate" -> i.captureDate.map(d => Json.toJson(d)(dmyDateWrites))
      )
    )
  }

  val hipWrites: OWrites[IabdDetails] = OWrites[IabdDetails] { i =>
    removeNulls(
      Json.obj(
        "nationalInsuranceNumber"  -> i.nino,
        "employmentSequenceNumber" -> i.employmentSequenceNumber,
        "type"                     -> i.`type`,
        "receiptDate"              -> i.receiptDate.map(d => Json.toJson(d)(isoDateWrites)),
        "captureDate"              -> i.captureDate.map(d => Json.toJson(d)(isoDateWrites)),
        "grossAmount"              -> i.grossAmount,
        "netAmount"                -> i.netAmount
      )
    )
  }

  private val typeReadsFlexible: Reads[Option[Int]] = Reads { json =>
    (json \ "type") match {
      case JsDefined(JsString(s)) =>
        readsTypeTuple.reads(JsString(s)).map { case (_, code) => Some(code) }
      case JsDefined(JsNumber(n)) if n.isValidInt =>
        JsSuccess(Some(n.toInt))
      case JsDefined(JsNull) =>
        JsSuccess(None)
      case _: JsUndefined =>
        JsSuccess(None)
      case JsDefined(other) =>
        JsError(s"Invalid type format: $other")
    }
  }

  private val singleReads: Reads[IabdDetails] = {

    val ninoReads: Reads[Option[String]] =
      (
        (JsPath \ "nino").readNullable[String] and
          (JsPath \ "nationalInsuranceNumber").readNullable[String]
      )((nino, nin) => nino.orElse(nin).map(_.take(8)))

    (
      ninoReads and
        (JsPath \ "employmentSequenceNumber").readNullable[Int] and
        (JsPath \ "source").readNullable[Option[Int]](sourceReads).map(_.flatten) and
        typeReadsFlexible and
        (JsPath \ "receiptDate").readNullable[LocalDate](lenientLocalDateReads) and
        (JsPath \ "captureDate").readNullable[LocalDate](lenientLocalDateReads) and
        (JsPath \ "grossAmount").readNullable[BigDecimal] and
        (JsPath \ "netAmount").readNullable[BigDecimal]
    )(IabdDetails.apply _)
  }

  implicit val readsSingle: Reads[IabdDetails] = singleReads

  implicit val listReads: Reads[List[IabdDetails]] =
    Reads.list(readsSingle)

  implicit val reads: Reads[Seq[IabdDetails]] =
    (JsPath \ "iabdDetails").readNullable(Reads.seq(singleReads)).map(_.getOrElse(Seq.empty))

  val singleObjectReads: Reads[IabdDetails] = singleReads

  private lazy val iabdList: List[String] = List(
    "Gift Aid Payments (001)",
    "Gift Aid treated as paid in previous tax year (002)",
    "One off Gift Aid Payments (003)",
    "Gift Aid after end of tax year (004)",
    "Personal Pension Payments (005)",
    "Maintenance Payments (006)",
    "Total gift aid Payments (007)",
    "Employer Provided Services (008)",
    "Widows and Orphans (009)",
    "Balancing Charge (010)",
    "Loan Interest Amount (011)",
    "Death, Sickness or Funeral Benefits (012)",
    "Married Couples Allowance (MAA) (013)",
    "Blind Persons Allowance (014)",
    "BPA Received from Spouse/Civil Partner (015)",
    "Community Investment Tax Credit (016)",
    "Gifts of Shares to Charity (017)",
    "Retirement Annuity Payments (018)",
    "Non-Coded Income (019)",
    "Commission (020)",
    "Other Income (Earned) (021)",
    "Other Income (Not Earned) (022)",
    "Part Time Earnings (023)",
    "Tips (024)",
    "Other Earnings (025)",
    "Casual Earnings (026)",
    "New Estimated Pay (027)",
    "Benefit in Kind (028)",
    "Car Fuel Benefit (029)",
    "Medical Insurance (030)",
    "Car Benefit (031)",
    "Telephone (032)",
    "Service Benefit (033)",
    "Taxable Expenses Benefit (034)",
    "Van Benefit (035)",
    "Van Fuel Benefit (036)",
    "Beneficial Loan (037)",
    "Accommodation (038)",
    "Assets (039)",
    "Asset Transfer (040)",
    "Educational Services (041)",
    "Entertaining (042)",
    "Expenses (043)",
    "Mileage (044)",
    "Non-qualifying Relocation Expenses (045)",
    "Nursery Places (046)",
    "Other Items (047)",
    "Payments on Employee's Behalf (048)",
    "Personal Incidental Expenses (049)",
    "Qualifying Relocation Expenses (050)",
    "Employer Provided Professional Subscription (051)",
    "Income Tax Paid but not deducted from Director's Remuneration (052)",
    "Travel and Subsistence (053)",
    "Vouchers and Credit Cards (054)",
    "Job Expenses (055)",
    "Flat Rate Job Expenses (056)",
    "Professional Subscriptions (057)",
    "Hotel and Meal Expenses (058)",
    "Other Expenses (059)",
    "Vehicle Expenses (060)",
    "Mileage Allowance Relief (061)",
    "Foreign Dividend Income (062)",
    "Foreign Property Income (063)",
    "Foreign Interest & Other Savings (064)",
    "Foreign Pensions & Other Income (065)",
    "State Pension (066)",
    "Occupational Pension (067)",
    "Public Services Pension (068)",
    "Forces Pension (069)",
    "Personal Pension Annuity (070)",
    "Lump Sum Deferral (071)",
    "Profit (072)",
    "Loss (073)",
    "Loss Brought Forward from earlier tax year (074)",
    "Taxed Interest (075)",
    "UK Dividend (076)",
    "Unit Trust (077)",
    "Stock Dividend (078)",
    "National Savings (079)",
    "Savings Bond (080)",
    "Purchased Life Annuities (081)",
    "Untaxed Interest (082)",
    "Incapacity Benefit (083)",
    "Job Seekers Allowance (084)",
    "Other Benefit (085)",
    "Trusts, Settlements & Estates at Trust Rate (086)",
    "Trusts, Settlements & Estates at Basic Rate (087)",
    "Trusts, Settlements & Estates at Lower Rate (088)",
    "Trusts, Settlements & Estates at Non-payable Dividend Rate (089)",
    "Venture Capital Trust (090)",
    "BPA Transferred to Spouse/Civil Partner (091)",
    "Trade Union Subscriptions (093)",
    "Chargeable Event Gain (094)",
    "Gift Aid Adjustment (095)",
    "Widows and Orphans Adjustment (096)",
    "Married Couples Allowance to Wife (MAW) (097)",
    "Double Taxation Relief (098)",
    "Concession Relief (099)",
    "Enterprise Investment Scheme (100)",
    "Early Years Adjustment (101)",
    "Loss relief (102)",
    "Estimated Income (103)",
    "Foreign Pension Allowance (104)",
    "Allowances Allocated Elsewhere (105)",
    "Allowances Allocated Here (106)",
    "Estimated NIB (107)",
    "Estimated IB (108)",
    "Married Couples Allowance (MAE) (109)",
    "Married Couples Allowance (MCCP) (110)",
    "Surplus Married Couples Allowance (MAT) (111)",
    "Surplus Married Couples Allowance to Wife (WAA) (112)",
    "Surplus Married Couples Allowance to Wife (WAE) (113)",
    "Married Couples Allowance to Wife (WMA) (114)",
    "Friendly Society Subscriptions (115)",
    "Higher Rate Adjustment (116)",
    "Non-Cash Benefit (117)",
    "Personal Allowance (PA) (118)",
    "Personal Allowance Aged (PAA) (119)",
    "Personal Allowance Elderly (PAE) (120)",
    "Starting Rate Adjustment (LRA) (121)",
    "Starting Rate Band Adjustment (ELR) (122)",
    "Employment and Support Allowance (123)",
    "Child Benefit (124)",
    "Bereavement Allowance (125)",
    "PA transferred to spouse/civil partner (126)",
    "PA received from spouse/civil partner (127)",
    "Personal Savings Allowance (128)",
    "Dividend Tax (129)",
    "Relief At Source (RAS) (130)",
    "HICBC PAYE (131)",
    "Winter Payment Amount (132)"
  )

  private lazy val iabdCodeToLabel: Map[Int, String] =
    iabdList.flatMap { s =>
      JsonHelper.parseType(s) match {
        case Some((_, code)) => Some(code -> s)
        case None =>
          val ex = new RuntimeException(s"Failed to parse IABD type from string: $s")
          logger.error(ex.getMessage, ex)
          None
      }
    }.toMap

  def iabdTypeToString(sourceType: Int): Option[String] =
    iabdCodeToLabel.get(sourceType)
}

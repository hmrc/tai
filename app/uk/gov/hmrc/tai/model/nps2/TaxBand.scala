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

package uk.gov.hmrc.tai.model.nps2

case class TaxBand(
  /*
   * Part of the tax code. Rarely used by itself.
   */
  bandType: Option[String],
  code: Option[String] = None,
  income: BigDecimal,
  tax: BigDecimal,
  lowerBand: Option[BigDecimal] = None,
  upperBand: Option[BigDecimal] = None,
  rate: BigDecimal
)

object TaxBand {
  def unapply(t: TaxBand): Option[
    (Option[String], Option[String], BigDecimal, BigDecimal, Option[BigDecimal], Option[BigDecimal], BigDecimal)
  ] =
    Some((t.bandType, t.code, t.income, t.tax, t.lowerBand, t.upperBand, t.rate))
}

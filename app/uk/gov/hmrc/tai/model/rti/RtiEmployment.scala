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
import PayFrequency._

/*
  * A single employment, containing a list of payments for a given year
  *
  * @param officeRefNo office number which the scheme operates from
  * @param accountOfficeReference accounts office reference of the scheme making
  *   the payment to the employee
  * @param payeRef Pay As You Earn (PAYE) reference of the scheme making the
  *   payment to the employee
  * @param payments
  * @param eyu end year updates for reconcilliation inputs
  * @param currentPayId employer's current identification of the employee
  * @param sequenceNumber along with the associated [[RtiData.nino]], this
  *   uniquely identifies a specific employment in NPS
  */
case class RtiEmployment(
  officeRefNo: String,
  payeRef: String,
  accountOfficeReference: String,
  payments: List[RtiPayment] = Nil,
  eyu: List[RtiEyu] = Nil,
  currentPayId: Option[String] = None,
  sequenceNumber: Int
) {

  def payFrequency: PayFrequency.Value = payments.lastOption.map(_.payFrequency).getOrElse(Irregular)

  def taxablePayYTD: BigDecimal = payments.lastOption.map(_.taxablePayYTD).getOrElse(0)
}

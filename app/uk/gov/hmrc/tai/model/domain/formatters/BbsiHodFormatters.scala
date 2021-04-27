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

package uk.gov.hmrc.tai.model.domain.formatters

import play.api.libs.json._
import uk.gov.hmrc.tai.model.domain.BankAccount

trait BbsiHodFormatters {

  val bankAccountHodReads = new Reads[Seq[BankAccount]] {
    override def reads(json: JsValue): JsResult[Seq[BankAccount]] = {

      def createBankAccount(accounts: Seq[JsValue]): JsSuccess[Seq[BankAccount]] =
        JsSuccess(accounts.map { account =>
          val accountNumber = (account \ "accountNumber").asOpt[String]
          val sortCode = (account \ "sortCode").asOpt[String]
          val bankName = (account \ "bankName").asOpt[String]
          val source = (account \ "source").asOpt[String]
          val numberOfAccountHolders = (account \ "numberOfAccountHolders").asOpt[Int]
          val grossInterest = (account \ "grossInterest").as[BigDecimal]

          BankAccount(
            accountNumber = accountNumber,
            sortCode = sortCode,
            bankName = bankName,
            source = source,
            grossInterest = grossInterest,
            numberOfAccountHolders = numberOfAccountHolders
          )
        })

      (json \ "accounts").validate[JsArray] match {
        case JsSuccess(arr, _) => createBankAccount(arr.value)
        case e @ JsError(_)    => e
      }
    }
  }
}

object BbsiHodFormatters extends BbsiHodFormatters

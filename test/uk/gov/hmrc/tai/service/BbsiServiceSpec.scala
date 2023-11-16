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

package uk.gov.hmrc.tai.service

import org.mockito.ArgumentMatchers.any
import uk.gov.hmrc.tai.model.domain.BankAccount
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.deprecated.BbsiRepository
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class BbsiServiceSpec extends BaseSpec {

  "Bbsi Service" must {
    "return bank accounts" in {
      val mockBbsiRepository = mock[BbsiRepository]
      when(mockBbsiRepository.bbsiDetails(any(), any())(any()))
        .thenReturn(Future.successful(Seq(bankAccount)))

      val sut = new BbsiService(mockBbsiRepository)
      val result = sut.bbsiDetails(nino, TaxYear()).futureValue

      result mustBe Seq(bankAccount)
    }
  }

  private val bankAccount = BankAccount(1, Some("123"), Some("123456"), Some("TEST"), 10.80, Some("Customer"), Some(1))
}

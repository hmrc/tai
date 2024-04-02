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

package uk.gov.hmrc.tai.controllers.income

import org.mockito.ArgumentMatchers.any
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.FakeRequest
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.model.domain.BankAccount
import uk.gov.hmrc.tai.service.BbsiService
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class BbsiControllerSpec extends BaseSpec {

  "Bbsi details" must {
    "return OK" in {
      val expectedJson =
        Json.obj(
          "data" -> Json.arr(
            Json.obj(
              "id"                     -> 0,
              "accountNumber"          -> "*****5566",
              "sortCode"               -> "112233",
              "bankName"               -> "ACCOUNT ONE",
              "grossInterest"          -> 1500.5,
              "source"                 -> "Customer",
              "numberOfAccountHolders" -> 1
            ),
            Json.obj(
              "id"                     -> 0,
              "accountNumber"          -> "*****5566",
              "sortCode"               -> "112233",
              "bankName"               -> "ACCOUNT ONE",
              "grossInterest"          -> 1500.5,
              "source"                 -> "Customer",
              "numberOfAccountHolders" -> 1
            ),
            Json.obj(
              "id"                     -> 0,
              "accountNumber"          -> "*****5566",
              "sortCode"               -> "112233",
              "bankName"               -> "ACCOUNT ONE",
              "grossInterest"          -> 1500.5,
              "source"                 -> "Customer",
              "numberOfAccountHolders" -> 1
            )
          ),
          "links" -> Json.arr()
        )

      val bankAccount = BankAccount(
        accountNumber = Some("*****5566"),
        sortCode = Some("112233"),
        bankName = Some("ACCOUNT ONE"),
        grossInterest = 1500.5,
        source = Some("Customer"),
        numberOfAccountHolders = Some(1)
      )

      val mockBbsiService = mock[BbsiService]
      when(mockBbsiService.bbsiDetails(any(), any())(any()))
        .thenReturn(Future.successful(Seq(bankAccount, bankAccount, bankAccount)))

      val sut = createSUT(mockBbsiService)
      val result = sut.bbsiDetails(nino)(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe expectedJson
    }
  }

  private def createSUT(
    bbsiService: BbsiService,
    authentication: AuthenticationPredicate = loggedInAuthenticationPredicate
  ) =
    new BbsiController(bbsiService, authentication, cc)
}

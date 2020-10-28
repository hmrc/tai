/*
 * Copyright 2020 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import play.api.libs.json._
import play.api.test.Helpers.{contentAsJson, _}
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.service.{TaiService, TaxAccountService}
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class TaxSummaryControllerSpec extends BaseSpec {

  "updateEmployments " must {
    "update the estimated pay when user is doing edit employments successfully " in {
      val updateEmployment1 = EmploymentAmount("test1", "desc", 1, newAmount = 123, oldAmount = 222)
      val updateEmployment2 = EmploymentAmount("test2", "desc", 2, newAmount = 200, oldAmount = 333)
      val updateEmployment3 = EmploymentAmount("test3", "desc", 3, newAmount = 999, oldAmount = 123)
      val updateEmployment4 = EmploymentAmount("test4", "desc", 4, newAmount = 987, oldAmount = 123)
      val empAmount = List(updateEmployment1, updateEmployment2, updateEmployment3, updateEmployment4)
      val requestData = IabdUpdateEmploymentsRequest(version = 1, empAmount)
      val fakeRequest = FakeRequest(
        method = "POST",
        uri = "",
        headers = FakeHeaders(Seq("Content-type" -> "application/json")),
        body = Json.toJson(requestData))

      val mockTaiService = mock[TaiService]
      when(mockTaiService.updateEmployments(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(IabdUpdateEmploymentsResponse(TransactionId("txId"), 3, 27, empAmount)))

      val mockTaxAccountService = mock[TaxAccountService]
      doNothing()
        .when(mockTaxAccountService)
        .invalidateTaiCacheData(nino)

      val sut = createSUT(mockTaiService, mockTaxAccountService, mock[Metrics])
      val summaryDetails = sut.updateEmployments(new Nino(nino.nino), 2014)(fakeRequest)

      val json: JsValue = contentAsJson(summaryDetails)
      (json \ "version").get mustBe JsNumber(3)
      (json \ "iabdType").get mustBe JsNumber(27)
      (json \\ "employmentId") mustBe List(
        JsNumber(updateEmployment1.employmentId),
        JsNumber(updateEmployment2.employmentId),
        JsNumber(updateEmployment3.employmentId),
        JsNumber(updateEmployment4.employmentId)
      )
      (json \\ "newAmount") mustBe List(
        JsNumber(updateEmployment1.newAmount),
        JsNumber(updateEmployment2.newAmount),
        JsNumber(updateEmployment3.newAmount),
        JsNumber(updateEmployment4.newAmount)
      )

      verify(mockTaiService, times(1)).updateEmployments(any(), any(), any(), any())(any())
      verify(mockTaxAccountService, times(1)).invalidateTaiCacheData(meq(nino))(any())
    }

    "update the estimated pay must be failed when nps is throwing BadRequestException " in {
      val updateEmployment1 = EmploymentAmount("test1", "desc", 1, newAmount = 123, oldAmount = 222)
      val updateEmployment2 = EmploymentAmount("test2", "desc", 2, newAmount = 200, oldAmount = 333)
      val updateEmployment3 = EmploymentAmount("test3", "desc", 3, newAmount = 999, oldAmount = 123)
      val updateEmployment4 = EmploymentAmount("test4", "desc", 4, newAmount = 987, oldAmount = 123)
      val empAmount = List(updateEmployment1, updateEmployment2, updateEmployment3, updateEmployment4)
      val requestData = IabdUpdateEmploymentsRequest(version = 1, empAmount)
      val fakeRequest = FakeRequest(
        method = "POST",
        uri = "",
        headers = FakeHeaders(Seq("Content-type" -> "application/json")),
        body = Json.toJson(requestData))

      val mockTaiService = mock[TaiService]
      when(mockTaiService.updateEmployments(any(), any(), any(), any())(any()))
        .thenReturn(Future.failed(new HttpException("Incorrect Version Number", 400)))

      val sut = createSUT(mockTaiService, mock[TaxAccountService], mock[Metrics])
      val summaryDetails = sut.updateEmployments(new Nino(nino.nino), 2014)(fakeRequest)

      val ex = the[HttpException] thrownBy Await.result(summaryDetails, 5 seconds)
      ex.message mustBe "Incorrect Version Number"

      verify(mockTaiService, times(1)).updateEmployments(any(), any(), any(), any())(any())
    }

    "throw an exception when failed to remove the cache data" in {
      val updateEmployment1 = EmploymentAmount("test1", "desc", 1, newAmount = 123, oldAmount = 222)
      val updateEmployment2 = EmploymentAmount("test2", "desc", 2, newAmount = 200, oldAmount = 333)
      val updateEmployment3 = EmploymentAmount("test3", "desc", 3, newAmount = 999, oldAmount = 123)
      val updateEmployment4 = EmploymentAmount("test4", "desc", 4, newAmount = 987, oldAmount = 123)
      val empAmount = List(updateEmployment1, updateEmployment2, updateEmployment3, updateEmployment4)
      val requestData = IabdUpdateEmploymentsRequest(version = 1, empAmount)
      val fakeRequest = FakeRequest(
        method = "POST",
        uri = "",
        headers = FakeHeaders(Seq("Content-type" -> "application/json")),
        body = Json.toJson(requestData))

      val mockTaiService = mock[TaiService]
      when(mockTaiService.updateEmployments(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(IabdUpdateEmploymentsResponse(TransactionId("txId"), 3, 27, empAmount)))

      val mockTaxAccountService = mock[TaxAccountService]
      doThrow(new RuntimeException(""))
        .when(mockTaxAccountService)
        .invalidateTaiCacheData(meq(nino))(any())

      val sut = createSUT(mockTaiService, mockTaxAccountService, mock[Metrics])
      the[RuntimeException] thrownBy sut.updateEmployments(new Nino(nino.nino), 2014)(fakeRequest)

      verify(mockTaxAccountService, times(1)).invalidateTaiCacheData(meq(nino))(any())
    }
  }

  private def createSUT(
    taiService: TaiService,
    taxAccountService: TaxAccountService,
    metrics: Metrics,
    authentication: AuthenticationPredicate = loggedInAuthenticationPredicate) =
    new TaxSummaryController(taiService, taxAccountService, metrics, authentication, cc)
}

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

package uk.gov.hmrc.tai.controllers

import data.NpsData
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import play.api.test.Helpers.{contentAsJson, _}
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.auth.core.MissingBearerToken
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http._
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.service.{NpsError, TaiService, TaxAccountService}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class TaxSummaryControllerSpec
  extends PlaySpec
    with MockitoSugar
    with MockAuthenticationPredicate{

  private implicit val hc = HeaderCarrier()

  "getTaxSummary" must {
    "return NOT AUTHORISED" when {
      "the user is not logged in" in {
        val sut = createSUT(mock[TaiService], mock[TaxAccountService], mock[Metrics], notLoggedInAuthenticationPredicate)
        val result = sut.getTaxSummary(nino, 2014)(FakeRequest())
        ScalaFutures.whenReady(result.failed) { e =>
          e mustBe a[MissingBearerToken]
        }
      }
    }
    "return tax Summary details successfully from NPS for the supplied nino and year" in {
      val taxSummaryDetails = NpsData.getTaxSummary
      val nino = taxSummaryDetails.nino
      val sessionData = SessionData(nino = nino, taxSummaryDetailsCY = taxSummaryDetails)

      val mockTaxAccountService = mock[TaxAccountService]
      when(mockTaxAccountService.taxSummaryDetails(any(), any())(any()))
        .thenReturn(Future.successful(sessionData.taxSummaryDetailsCY))

      val sut = createSUT(mock[TaiService], mockTaxAccountService, mock[Metrics])
      val summaryDetails = sut.getTaxSummary(Nino(nino), 2014)(FakeRequest())

      status(summaryDetails) mustBe OK
      val json: JsValue = contentAsJson(summaryDetails)
      (json \ "nino").get mustBe JsString(nino)
    }

    "return Bad Request error from Hods for the supplied nino and year" in {
      val badRequestErrorResponse = Json.obj(
        "message" -> "Cannot complete a Coding Calculation without a Primary Employment",
        "statusCode" -> 400,
        "appStatusMessage" -> "Cannot complete a Coding Calculation without a Primary Employment",
        "requestUri" -> s"nps/person/${nino.nino}/tax-account/2014")

      val mockTaxAccountService = mock[TaxAccountService]
      when(mockTaxAccountService.taxSummaryDetails(any(), any())(any()))
        .thenReturn(Future.failed(NpsError(Json.prettyPrint(badRequestErrorResponse), BAD_REQUEST)))

      val sut = createSUT(mock[TaiService], mockTaxAccountService, mock[Metrics])
      val badRequest = sut.getTaxSummary(new Nino(nino.nino), 2014)(FakeRequest())

      status(badRequest) mustBe BAD_REQUEST
      val json: JsValue = contentAsJson(badRequest)
        (json \ "message").get mustBe JsString("Cannot complete a Coding Calculation without a Primary Employment")
    }

    "return Not Found error from Hods for the supplied nino and year" in {
      val notFoundErrorResponse = Json.obj(
        "message" -> "Not Found Exception",
        "statusCode" -> 404,
        "appStatusMessage" -> "Not Found Exception",
        "requestUri" -> s"nps/person/${nino.nino}/tax-account/2014")

      val mockTaxAccountService = mock[TaxAccountService]
      when(mockTaxAccountService.taxSummaryDetails(any(), any())(any()))
        .thenReturn(Future.failed(NpsError(Json.prettyPrint(notFoundErrorResponse), NOT_FOUND)))

      val sut = createSUT(mock[TaiService], mockTaxAccountService, mock[Metrics])
      val notFound = sut.getTaxSummary(new Nino(nino.nino), 2014)(FakeRequest())

      val thrown = the[NotFoundException] thrownBy await(notFound)
      thrown.getMessage mustBe Json.prettyPrint(notFoundErrorResponse)
    }

    "return Service Unavailable error from Hods for the supplied nino and year" in {
      val serviceUnavailableErrorResponse = Json.obj(
        "message" -> "Service Unavailable",
        "statusCode" -> 503,
        "appStatusMessage" -> "Service Unavailable",
        "requestUri" -> s"nps/person/${nino.nino}/tax-account/2014")

      val mockTaxAccountService = mock[TaxAccountService]
      when(mockTaxAccountService.taxSummaryDetails(any(), any())(any()))
        .thenReturn(Future.failed(NpsError(Json.prettyPrint(serviceUnavailableErrorResponse), SERVICE_UNAVAILABLE)))

      val sut = createSUT(mock[TaiService], mockTaxAccountService, mock[Metrics])
      val serviceUnavailable = sut.getTaxSummary(new Nino(nino.nino), 2014)(FakeRequest())

      val thrown = the[HttpException] thrownBy await(serviceUnavailable)
      thrown.getMessage mustBe Json.prettyPrint(serviceUnavailableErrorResponse)
    }

    "return Internal Server error from Hods for the supplied nino and year" in {
      val internalServerErrorResponse = Json.obj(
        "message" -> "Internal Server error",
        "statusCode" -> 500,
        "appStatusMessage" -> "Internal Server error",
        "requestUri" -> s"nps/person/${nino.nino}/tax-account/2014")

      val mockTaxAccountService = mock[TaxAccountService]
      when(mockTaxAccountService.taxSummaryDetails(any(), any())(any()))
        .thenReturn(Future.failed(NpsError(Json.prettyPrint(internalServerErrorResponse), INTERNAL_SERVER_ERROR)))

      val sut = createSUT(mock[TaiService], mockTaxAccountService, mock[Metrics])
      val internalServerError = sut.getTaxSummary(new Nino(nino.nino), 2014)(FakeRequest())

      val thrown = the[InternalServerException] thrownBy await(internalServerError)
      thrown.getMessage mustBe Json.prettyPrint(internalServerErrorResponse)
    }
  }

  "getTaxSummaryPartial" must {
    "return NOT AUTHORISED" when {
      "the user is not logged in" in {
        val sut = createSUT(mock[TaiService], mock[TaxAccountService], mock[Metrics], notLoggedInAuthenticationPredicate)
        val result = sut.getTaxSummaryPartial(nino, 2014)(FakeRequest())
        ScalaFutures.whenReady(result.failed) { e =>
          e mustBe a[MissingBearerToken]
        }
      }
    }

    "return tax Summary details successfully for the supplied nino and year" in {
      val taxSummaryDetails = NpsData.getTaxSummary
      val nino = taxSummaryDetails.nino

      val mockTaiService = mock[TaiService]
      when(mockTaiService.getCalculatedTaxAccountPartial(any(), any())(any()))
        .thenReturn(Future.successful(taxSummaryDetails))

      val sut = createSUT(mockTaiService, mock[TaxAccountService], mock[Metrics])
      val summaryDetails = sut.getTaxSummaryPartial(new Nino(nino), 2014)(FakeRequest())

      status(summaryDetails) mustBe OK
      val json: JsValue = contentAsJson(summaryDetails)
      (json \ "nino").get mustBe JsString(nino)
    }

    "return Bad Request error from Hods for the supplied nino and year" in {
      val mockTaiService = mock[TaiService]
      when(mockTaiService.getCalculatedTaxAccountPartial(any(), any())(any()))
        .thenReturn(Future.failed(new BadRequestException("Cannot complete a Coding Calculation without a Primary Employment")))

      val sut = createSUT(mockTaiService, mock[TaxAccountService], mock[Metrics])
      val badRequest = sut.getTaxSummaryPartial(new Nino(nino.nino), 2014)(FakeRequest())

      status(badRequest) mustBe BAD_REQUEST
    }

    "return Not Found error from Hods for the supplied nino and year" in {
      val mockTaiService = mock[TaiService]
      when(mockTaiService.getCalculatedTaxAccountPartial(any(),
        any())(any())).thenReturn(Future.failed(new NotFoundException("No Data Found")))

      val sut = createSUT(mockTaiService, mock[TaxAccountService], mock[Metrics])
      val notFound = sut.getTaxSummaryPartial(new Nino(nino.nino), 2014)(FakeRequest())

      val thrown = the[NotFoundException] thrownBy await(notFound)
      thrown.getMessage mustBe "No Data Found"
    }

    "return Service Unavailable error from Hods for the supplied nino and year" in {
      val mockTaiService = mock[TaiService]
      when(mockTaiService.getCalculatedTaxAccountPartial(any(), any())(any()))
        .thenReturn(Future.failed(new ServiceUnavailableException("Service Unavailable")))

      val sut = createSUT(mockTaiService, mock[TaxAccountService], mock[Metrics])
      val serviceUnavailable = sut.getTaxSummaryPartial(new Nino(nino.nino), 2014)(FakeRequest())

      val thrown = the[HttpException] thrownBy await(serviceUnavailable)
      thrown.getMessage mustBe "Service Unavailable"
    }

    "return Internal Server error from Hods for the supplied nino and year" in {
      val mockTaiService = mock[TaiService]
      when(mockTaiService.getCalculatedTaxAccountPartial(any(), any())(any()))
        .thenReturn(Future.failed(new InternalServerException("Internal Server Error")))

      val sut = createSUT(mockTaiService, mock[TaxAccountService], mock[Metrics])
      val internalServerError = sut.getTaxSummaryPartial(new Nino(nino.nino), 2014)(FakeRequest())

      val thrown = the[InternalServerException] thrownBy await(internalServerError)
      thrown.getMessage mustBe "Internal Server Error"
    }
  }


  "updateEmployments " must {
    "return NOT AUTHORISED" when {
      "the user is not logged in" in {
        val sut = createSUT(mock[TaiService], mock[TaxAccountService], mock[Metrics], notLoggedInAuthenticationPredicate)
        val result = sut.updateEmployments(new Nino(nino.nino), 2014)(FakeRequest("POST", "/",
          FakeHeaders(Seq("Content-type" -> "application/json")), JsNull))
        ScalaFutures.whenReady(result.failed) { e =>
          e mustBe a[MissingBearerToken]
        }
      }
    }
    "update the estimated pay when user is doing edit employments successfully " in {
      val updateEmployment1 = EmploymentAmount("test1", "desc", 1, newAmount = 123, oldAmount = 222)
      val updateEmployment2 = EmploymentAmount("test2", "desc", 2, newAmount = 200, oldAmount = 333)
      val updateEmployment3 = EmploymentAmount("test3", "desc", 3, newAmount = 999, oldAmount = 123)
      val updateEmployment4 = EmploymentAmount("test4", "desc", 4, newAmount = 987, oldAmount = 123)
      val empAmount = List(updateEmployment1, updateEmployment2, updateEmployment3, updateEmployment4)
      val requestData = IabdUpdateEmploymentsRequest(version = 1, empAmount)
      val fakeRequest = FakeRequest(method = "POST", uri = "",
        headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(requestData))

      val mockTaiService = mock[TaiService]
      when(mockTaiService.updateEmployments(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(IabdUpdateEmploymentsResponse(TransactionId("txId"), 3, 27, empAmount)))

      val mockTaxAccountService = mock[TaxAccountService]
      doNothing()
        .when(mockTaxAccountService).invalidateTaiCacheData()

      val sut = createSUT(mockTaiService, mockTaxAccountService, mock[Metrics])
      val summaryDetails = sut.updateEmployments(new Nino(nino.nino), 2014)(fakeRequest)

      val json: JsValue = contentAsJson(summaryDetails)
      (json \ "version").get mustBe JsNumber(3)
      (json \ "iabdType").get mustBe JsNumber(27)
      (json \\ "employmentId") mustBe List(JsNumber(updateEmployment1.employmentId),
        JsNumber(updateEmployment2.employmentId), JsNumber(updateEmployment3.employmentId),
        JsNumber(updateEmployment4.employmentId))
      (json \\ "newAmount") mustBe List(JsNumber(updateEmployment1.newAmount),
        JsNumber(updateEmployment2.newAmount), JsNumber(updateEmployment3.newAmount),
        JsNumber(updateEmployment4.newAmount))

      verify(mockTaiService, times(1)).updateEmployments(any(), any(), any(), any())(any())
      verify(mockTaxAccountService, times(1)).invalidateTaiCacheData()(any())
    }

    "update the estimated pay must be failed when nps is throwing BadRequestException " in {
      val updateEmployment1 = EmploymentAmount("test1", "desc", 1, newAmount = 123, oldAmount = 222)
      val updateEmployment2 = EmploymentAmount("test2", "desc", 2, newAmount = 200, oldAmount = 333)
      val updateEmployment3 = EmploymentAmount("test3", "desc", 3, newAmount = 999, oldAmount = 123)
      val updateEmployment4 = EmploymentAmount("test4", "desc", 4, newAmount = 987, oldAmount = 123)
      val empAmount = List(updateEmployment1, updateEmployment2, updateEmployment3, updateEmployment4)
      val requestData = IabdUpdateEmploymentsRequest(version = 1, empAmount)
      val fakeRequest = FakeRequest(method = "POST", uri = "",
        headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(requestData))

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
      val fakeRequest = FakeRequest(method = "POST", uri = "",
        headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(requestData))

      val mockTaiService = mock[TaiService]
      when(mockTaiService.updateEmployments(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(IabdUpdateEmploymentsResponse(TransactionId("txId"), 3, 27, empAmount)))

      val mockTaxAccountService = mock[TaxAccountService]
      doThrow(new RuntimeException(""))
        .when(mockTaxAccountService).invalidateTaiCacheData()(any())

      val sut = createSUT(mockTaiService, mockTaxAccountService, mock[Metrics])
      the[RuntimeException] thrownBy sut.updateEmployments(new Nino(nino.nino), 2014)(fakeRequest)

      verify(mockTaxAccountService, times(1)).invalidateTaiCacheData()(any())
    }
  }
  private val nino: Nino = new Generator(new Random).nextNino
  private def createSUT(taiService: TaiService,
                        taxAccountService: TaxAccountService,
                        metrics: Metrics, authentication: AuthenticationPredicate =
                        loggedInAuthenticationPredicate) =

    new TaxSummaryController(taiService, taxAccountService, metrics, authentication)
}

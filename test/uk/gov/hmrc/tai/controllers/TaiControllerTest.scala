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

import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import play.api.test.Helpers.{contentAsJson, _}
import play.api.test.{FakeHeaders, FakeRequest}
import play.mvc.Http.Status
import uk.gov.hmrc.auth.core.MissingBearerToken
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.{HttpException, InternalServerException, NotFoundException}
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model.nps2.MongoFormatter
import uk.gov.hmrc.tai.model.{SessionData, TaiRoot, TaxSummaryDetails}
import uk.gov.hmrc.tai.service.{NpsError, TaxAccountService}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class TaiControllerTest extends PlaySpec with MockitoSugar with MongoFormatter with MockAuthenticationPredicate {

  "getTaiRoot" should {

    "return NOT AUTHORISED" when {
      "the user is not logged in" in {
        val sut = createSUT(mock[TaxAccountService], mock[Metrics], notLoggedInAuthenticationPredicate)
        val result = sut.getTaiRoot(nino)(FakeRequest())
        ScalaFutures.whenReady(result.failed) { e =>
          e mustBe a[MissingBearerToken]
        }
      }
    }

    "return the TaiRoot for the supplied nino " in {
      val data = sessionData.copy(
        taiRoot = Some(
          TaiRoot(
            nino.nino,
            1,
            "Mr",
            "TestFName",
            Some("TestMName"),
            "TestLName",
            "TestFName TestLName",
            manualCorrespondenceInd = false,
            Some(false))))

      val mockTaxAccountService = mock[TaxAccountService]
      when(mockTaxAccountService.personDetails(any())(any()))
        .thenReturn(Future.successful(data.taiRoot.get))

      val sut = createSUT(mockTaxAccountService, mock[Metrics])
      val taiRoot = sut.getTaiRoot(nino)(FakeRequest())

      status(taiRoot) mustBe Status.OK
      val json: JsValue = contentAsJson(taiRoot)
      (json \ "nino").get mustBe JsString(nino.nino)
      (json \ "version").get mustBe JsNumber(1)
      (json \ "firstName").get mustBe JsString("TestFName")
      (json \ "surname").get mustBe JsString("TestLName")
      (json \ "name").get mustBe JsString("TestFName TestLName")
      (json \ "deceasedIndicator").as[Boolean] mustBe false
    }
  }

  "taiData" should {
    "return NOT AUTHORISED" when {
      "the user is not logged in" in {
        val sut = createSUT(mock[TaxAccountService], mock[Metrics], notLoggedInAuthenticationPredicate)
        val result = sut.taiData(nino)(FakeRequest())
        ScalaFutures.whenReady(result.failed) { e =>
          e mustBe a[MissingBearerToken]
        }
      }
    }
    "return cached data" in {
      val data = sessionData.copy(
        taiRoot = Some(
          TaiRoot(
            nino.nino,
            1,
            "Mr",
            "TestFName",
            Some("TestMName"),
            "TestLName",
            "TestFName TestLName",
            manualCorrespondenceInd = false,
            Some(false))))

      val mockTaxAccountService = mock[TaxAccountService]
      when(mockTaxAccountService.taiData(any(), any())(any()))
        .thenReturn(Future.successful(data))

      val sut = createSUT(mockTaxAccountService, mock[Metrics])
      val result = sut.taiData(nino)(FakeRequest())

      status(result) mustBe 200
      val taiData = contentAsJson(result).as[SessionData]
      taiData.taxSummaryDetailsCY mustBe taxSummaryDetails
      taiData.taiRoot mustBe data.taiRoot
    }

    "return Bad Request error from Hods for the supplied nino and year" in {
      val badRequestErrorResponse = Json.obj(
        "message"          -> "Cannot complete a Coding Calculation without a Primary Employment",
        "statusCode"       -> 400,
        "appStatusMessage" -> "Cannot complete a Coding Calculation without a Primary Employment",
        "requestUri"       -> s"nps/person/${nino.nino}/tax-account/2014/calculation"
      )

      val mockTaxAccountService = mock[TaxAccountService]
      when(mockTaxAccountService.taiData(any(), any())(any()))
        .thenReturn(Future.failed(NpsError(Json.prettyPrint(badRequestErrorResponse), BAD_REQUEST)))

      val sut = createSUT(mockTaxAccountService, mock[Metrics])
      val badRequest = sut.taiData(new Nino(nino.nino))(FakeRequest())

      status(badRequest) mustBe BAD_REQUEST
      val json: JsValue = contentAsJson(badRequest)
      (json \ "message").get mustBe JsString("Cannot complete a Coding Calculation without a Primary Employment")
    }

    "return Not Found error from Hods for the supplied nino and year" in {
      val notFoundErrorResponse = Json.obj(
        "message"          -> "Not Found Exception",
        "statusCode"       -> 404,
        "appStatusMessage" -> "Not Found Exception",
        "requestUri"       -> s"nps/person/${nino.nino}/tax-account/2014/calculation"
      )

      val mockTaxAccountService = mock[TaxAccountService]
      when(mockTaxAccountService.taiData(any(), any())(any()))
        .thenReturn(Future.failed(NpsError(Json.prettyPrint(notFoundErrorResponse), NOT_FOUND)))

      val sut = createSUT(mockTaxAccountService, mock[Metrics])
      val notFound = sut.taiData(new Nino(nino.nino))(FakeRequest())

      val thrown = the[NotFoundException] thrownBy await(notFound)
      thrown.getMessage mustBe Json.prettyPrint(notFoundErrorResponse)
    }

    "return Service Unavailable error from Hods for the supplied nino and year" in {
      val serviceUnavailableErrorResponse = Json.obj(
        "message"          -> "Service Unavailable",
        "statusCode"       -> 503,
        "appStatusMessage" -> "Service Unavailable",
        "requestUri"       -> s"nps/person/${nino.nino}/tax-account/2014/calculation"
      )

      val mockTaxAccountService = mock[TaxAccountService]
      when(mockTaxAccountService.taiData(any(), any())(any()))
        .thenReturn(Future.failed(NpsError(Json.prettyPrint(serviceUnavailableErrorResponse), SERVICE_UNAVAILABLE)))

      val sut = createSUT(mockTaxAccountService, mock[Metrics])
      val serviceUnavailable = sut.taiData(new Nino(nino.nino))(FakeRequest())

      val thrown = the[HttpException] thrownBy await(serviceUnavailable)
      thrown.getMessage mustBe Json.prettyPrint(serviceUnavailableErrorResponse)
    }

    "return Internal Server error from Hods for the supplied nino and year" in {
      val internalServerErrorResponse = Json.obj(
        "message"          -> "Internal Server error",
        "statusCode"       -> 500,
        "appStatusMessage" -> "Internal Server error",
        "requestUri"       -> s"nps/person/${nino.nino}/tax-account/2014/calculation"
      )

      val mockTaxAccountService = mock[TaxAccountService]
      when(mockTaxAccountService.taiData(any(), any())(any()))
        .thenReturn(Future.failed(NpsError(Json.prettyPrint(internalServerErrorResponse), INTERNAL_SERVER_ERROR)))

      val sut = createSUT(mockTaxAccountService, mock[Metrics])
      val internalServerError = sut.taiData(new Nino(nino.nino))(FakeRequest())

      val thrown = the[InternalServerException] thrownBy await(internalServerError)
      thrown.getMessage mustBe Json.prettyPrint(internalServerErrorResponse)
    }
  }

  "updateTaiData" should {
    "return NOT AUTHORISED" when {
      "the user is not logged in" in {
        val sut = createSUT(mock[TaxAccountService], mock[Metrics], notLoggedInAuthenticationPredicate)
        val result = sut.updateTaiData(nino)(
          FakeRequest("PUT", "/", FakeHeaders(Seq("Content-type" -> "application/json")), JsNull))
        ScalaFutures.whenReady(result.failed) { e =>
          e mustBe a[MissingBearerToken]
        }
      }
    }
    "return successful when data saved in cache" in {
      val fakeRequest = FakeRequest(
        method = "Put",
        uri = "",
        headers = FakeHeaders(Seq("Content-type" -> "application/json")),
        body = Json.toJson(sessionData))

      val mockTaxAccountService = mock[TaxAccountService]
      when(mockTaxAccountService.updateTaiData(any(), any())(any()))
        .thenReturn(Future.successful(sessionData))

      val sut = createSUT(mockTaxAccountService, mock[Metrics])
      val result = sut.updateTaiData(nino)(fakeRequest)

      status(result) mustBe 200
      verify(mockTaxAccountService, times(1))
        .updateTaiData(Matchers.eq(nino), any())(any())
    }

    "return failure when data couldn't be saved in cache" in {
      val fakeRequest = FakeRequest(
        method = "Put",
        uri = "",
        headers = FakeHeaders(Seq("Content-type" -> "application/json")),
        body = Json.toJson(sessionData))

      val mockTaxAccountService = mock[TaxAccountService]
      when(mockTaxAccountService.updateTaiData(any(), any())(any()))
        .thenReturn(Future.failed(new IllegalArgumentException("FAILED")))

      val sut = createSUT(mockTaxAccountService, mock[Metrics])
      val result = sut.updateTaiData(nino)(fakeRequest)

      val ex = the[InternalServerException] thrownBy Await.result(result, 5 seconds)

      ex.getMessage mustBe "FAILED"
      verify(mockTaxAccountService, times(1))
        .updateTaiData(Matchers.eq(nino), any())(any())
    }

  }

  val nino = new Generator(new Random).nextNino
  private val taxSummaryDetails = TaxSummaryDetails(nino = nino.nino, version = 0)
  private val sessionData = SessionData(nino = nino.nino, taxSummaryDetailsCY = taxSummaryDetails)

  private def createSUT(
    taxAccountService: TaxAccountService,
    metrics: Metrics,
    authentication: AuthenticationPredicate = loggedInAuthenticationPredicate) =
    new TaiController(taxAccountService, metrics, authentication)
}

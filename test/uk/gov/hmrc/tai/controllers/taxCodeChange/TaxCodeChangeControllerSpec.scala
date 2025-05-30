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

package uk.gov.hmrc.tai.controllers.taxCodeChange

import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.when
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{status, *}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.InternalServerException
import uk.gov.hmrc.tai.model.api.{TaxCodeChange, TaxCodeSummary}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.{TaxCodeMismatch, api}
import uk.gov.hmrc.tai.service.TaxCodeChangeServiceImpl
import uk.gov.hmrc.tai.util.{BaseSpec, TaxCodeHistoryConstants}

import java.time.LocalDate
import scala.concurrent.Future
import scala.util.Random

class TaxCodeChangeControllerSpec extends BaseSpec with TaxCodeHistoryConstants {

  val testNino: Nino = ninoGenerator

  val taxCodeService: TaxCodeChangeServiceImpl = mock[TaxCodeChangeServiceImpl]

  private def controller = new TaxCodeChangeController(loggedInAuthenticationAuthJourney, taxCodeService, cc)

  private def ninoGenerator = new Generator(new Random).nextNino

  "hasTaxCodeChanged" must {

    "return true" when {
      "there has been a tax code change" in {

        when(taxCodeService.hasTaxCodeChanged(any())(any(), any())).thenReturn(Future.successful(true))

        val response: Future[Result] = controller.hasTaxCodeChanged(testNino)(FakeRequest())

        status(response) mustBe OK

        contentAsJson(response) mustEqual Json.toJson(true)
      }
    }

    "return false" when {
      "there has not been a tax code change" in {

        when(taxCodeService.hasTaxCodeChanged(any())(any(), any())).thenReturn(Future.successful(false))

        val response: Future[Result] = controller.hasTaxCodeChanged(testNino)(FakeRequest())

        status(response) mustBe OK

        contentAsJson(response) mustEqual Json.toJson(false)
      }
    }
  }

  "taxCodeChange" must {
    "respond with OK and return given nino's tax code history" in {

      val date = LocalDate.now()
      val currentRecord = api.TaxCodeSummary(
        1,
        "b",
        Cumulative,
        date,
        date.minusDays(1),
        "Employer 1",
        Some("12345"),
        pensionIndicator = false,
        primary = true
      )
      val previousRecord = api.TaxCodeSummary(
        2,
        "a",
        Cumulative,
        date,
        date.minusDays(1),
        "Employer 2",
        Some("67890"),
        pensionIndicator = false,
        primary = true
      )
      when(taxCodeService.taxCodeChange(meq(testNino))(any()))
        .thenReturn(Future.successful(TaxCodeChange(Seq(currentRecord), Seq(previousRecord))))

      val expectedResponse = Json.obj(
        "data" -> Json.obj(
          "current" -> Json.arr(
            Json.obj(
              "taxCodeId"        -> 1,
              "taxCode"          -> "b",
              "basisOfOperation" -> Cumulative,
              "startDate"        -> date.toString,
              "endDate"          -> date.minusDays(1).toString,
              "employerName"     -> "Employer 1",
              "payrollNumber"    -> "12345",
              "pensionIndicator" -> false,
              "primary"          -> true
            )
          ),
          "previous" -> Json.arr(
            Json.obj(
              "taxCodeId"        -> 2,
              "taxCode"          -> "a",
              "basisOfOperation" -> Cumulative,
              "startDate"        -> date.toString,
              "endDate"          -> date.minusDays(1).toString,
              "employerName"     -> "Employer 2",
              "payrollNumber"    -> "67890",
              "pensionIndicator" -> false,
              "primary"          -> true
            )
          )
        ),
        "links" -> Json.arr()
      )

      val response = controller.taxCodeChange(testNino)(FakeRequest())

      contentAsJson(response) mustEqual expectedResponse
    }

    "respond with OK and give an empty sequence of taxCodeRecords when no tax code records are found" in {

      when(taxCodeService.taxCodeChange(meq(testNino))(any()))
        .thenReturn(Future.successful(TaxCodeChange(Seq.empty, Seq.empty)))

      val response = controller.taxCodeChange(testNino)(FakeRequest())

      val expectedResponse = Json.obj(
        "data" -> Json.obj(
          "current"  -> Json.arr(),
          "previous" -> Json.arr()
        ),
        "links" -> Json.arr()
      )

      status(response) mustBe OK
      contentAsJson(response) mustEqual expectedResponse
    }

    "respond with BAD_GATEWAY" when {
      "a bad gateway exception has occurred" in {
        when(taxCodeService.taxCodeChange(any())(any()))
          .thenReturn(Future.failed(badGatewayException))

        checkControllerResponse(
          badGatewayException,
          controller.taxCodeChange(testNino)(FakeRequest()),
          BAD_GATEWAY
        )
      }
    }

    "respond with NOT_FOUND" when {
      "a NotFoundException has occurred" in {
        when(taxCodeService.taxCodeChange(any())(any()))
          .thenReturn(Future.failed(notFoundException))

        checkControllerResponse(
          notFoundException,
          controller.taxCodeChange(testNino)(FakeRequest()),
          NOT_FOUND
        )
      }
    }

    "respond with BAD_GATEWAY" when {
      "a InternalServerException has occurred" in {
        when(taxCodeService.taxCodeChange(any())(any()))
          .thenReturn(Future.failed(internalServerException))

        checkControllerResponse(
          internalServerException,
          controller.taxCodeChange(testNino)(FakeRequest()),
          BAD_GATEWAY
        )
      }
    }
  }

  "taxCodeMismatch" must {

    val nino = ninoGenerator

    "return true and list of tax code changes" when {

      "there has been a tax code change but there is a mismatch between confirmed and unconfirmed codes" in {

        when(taxCodeService.taxCodeMismatch(any())(any(), any()))
          .thenReturn(Future.successful(TaxCodeMismatch(mismatch = true, Seq("1185L", "BR"), Seq("1185L"))))

        val expectedResponse = Json.obj(
          "data" -> Json.obj(
            "mismatch"            -> true,
            "unconfirmedTaxCodes" -> Json.arr("1185L", "BR"),
            "confirmedTaxCodes"   -> Json.arr("1185L")
          ),
          "links" -> Json.arr()
        )

        val result = controller.taxCodeMismatch(nino)(FakeRequest())

        contentAsJson(result) mustEqual expectedResponse
      }
    }

    "return false and list of tax code changes" when {
      "there has been a tax code change but there is a mismatch between confirmed and unconfirmed codes" in {

        when(taxCodeService.taxCodeMismatch(any())(any(), any()))
          .thenReturn(Future.successful(TaxCodeMismatch(mismatch = true, Seq("1185L", "BR"), Seq("1185L", "BR"))))

        val expectedResponse = Json.obj(
          "data" -> Json.obj(
            "mismatch"            -> true,
            "unconfirmedTaxCodes" -> Json.arr("1185L", "BR"),
            "confirmedTaxCodes"   -> Json.arr("1185L", "BR")
          ),
          "links" -> Json.arr()
        )

        val result = controller.taxCodeMismatch(nino)(FakeRequest())

        contentAsJson(result) mustEqual expectedResponse
      }
    }

    "return a BAD_GATEWAY" when {
      "a bad gateway exception has occurred" in {
        when(taxCodeService.taxCodeMismatch(any())(any(), any()))
          .thenReturn(Future.failed(badGatewayException))

        checkControllerResponse(
          badGatewayException,
          controller.taxCodeMismatch(testNino)(FakeRequest()),
          BAD_GATEWAY
        )
      }
    }

    "return a NotFound 404" when {
      "a not found exception has occurred" in {
        when(taxCodeService.taxCodeMismatch(any())(any(), any()))
          .thenReturn(Future.failed(notFoundException))

        checkControllerResponse(
          notFoundException,
          controller.taxCodeMismatch(testNino)(FakeRequest()),
          NOT_FOUND
        )
      }
    }

    "respond with BAD_GATEWAY" when {
      "a InternalServerException has occurred" in {
        when(taxCodeService.taxCodeMismatch(any())(any(), any()))
          .thenReturn(Future.failed(internalServerException))

        checkControllerResponse(
          internalServerException,
          controller.taxCodeMismatch(testNino)(FakeRequest()),
          BAD_GATEWAY
        )
      }
    }
  }

  "mostRecentTaxCodeRecords" must {

    val nino = ninoGenerator

    "respond with OK" when {
      "given valid nino and year" in {

        when(taxCodeService.latestTaxCodes(any(), any())(any()))
          .thenReturn(
            Future.successful(
              Seq(
                TaxCodeSummary(
                  1,
                  "code",
                  "Cumulative",
                  LocalDate.now(),
                  LocalDate.now().plusDays(1),
                  "Employer 1",
                  Some("1234"),
                  pensionIndicator = false,
                  primary = true
                )
              )
            )
          )

        val result = controller.mostRecentTaxCodeRecords(nino, TaxYear())(FakeRequest())

        val json = Json.obj(
          "data" -> Json.arr(
            Json.obj(
              "taxCodeId"        -> 1,
              "taxCode"          -> "code",
              "basisOfOperation" -> "Cumulative",
              "startDate"        -> LocalDate.now().toString,
              "endDate"          -> LocalDate.now().plusDays(1).toString,
              "employerName"     -> "Employer 1",
              "payrollNumber"    -> "1234",
              "pensionIndicator" -> false,
              "primary"          -> true
            )
          ),
          "links" -> Json.arr()
        )

        status(result) mustEqual OK
        contentAsJson(result) mustEqual json

      }
    }

    "respond with BAD_GATEWAY" when {
      "a bad gateway exception has occurred" in {
        when(taxCodeService.latestTaxCodes(any(), any())(any()))
          .thenReturn(Future.failed(badGatewayException))

        checkControllerResponse(
          badGatewayException,
          controller.mostRecentTaxCodeRecords(nino, TaxYear())(FakeRequest()),
          BAD_GATEWAY
        )
      }
    }

    "respond with NOT_FOUND" when {
      "a NotFoundException has occurred" in {
        when(taxCodeService.latestTaxCodes(any(), any())(any()))
          .thenReturn(Future.failed(notFoundException))

        checkControllerResponse(
          notFoundException,
          controller.mostRecentTaxCodeRecords(nino, TaxYear())(FakeRequest()),
          NOT_FOUND
        )
      }
    }

    "respond with BAD_GATEWAY" when {
      "a InternalServerException has occurred" in {
        val internalServerException = new InternalServerException("Bad gateway")
        when(taxCodeService.latestTaxCodes(any(), any())(any()))
          .thenReturn(Future.failed(internalServerException))

        checkControllerResponse(
          internalServerException,
          controller.mostRecentTaxCodeRecords(nino, TaxYear())(FakeRequest()),
          BAD_GATEWAY
        )

      }
    }
  }
}

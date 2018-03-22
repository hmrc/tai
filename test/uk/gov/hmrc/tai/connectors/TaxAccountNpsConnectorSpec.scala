/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.tai.connectors

import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.tai.config.NpsConfig
import uk.gov.hmrc.tai.model.domain.response.{HodUpdateFailure, HodUpdateSuccess}
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.nps.NpsIabdUpdateAmountFormats
import uk.gov.hmrc.tai.model.nps2.IabdType.NewEstimatedPay
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.MongoConstants

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class TaxAccountNpsConnectorSpec extends PlaySpec
    with MockitoSugar with MongoConstants {

  "taxAccount" must{
    "return Tax Account as Json in the response" in {
      val taxYear = TaxYear(2017)

      val mockHttpHandler = mock[HttpHandler]
      when(mockHttpHandler.getFromApi(any(), any())(any()))
        .thenReturn(Future.successful(jsonResponse))

      val sut = createSUT(mock[NpsConfig], mock[TaxAccountUrls], mock[IabdUrls], mock[NpsIabdUpdateAmountFormats], mockHttpHandler)
      val result = Await.result(sut.taxAccount(randomNino, taxYear), 5 seconds)

      result mustBe jsonResponse
    }
  }

  "updateTaxCodeIncome" must {
    "update nps with the new tax code income" in {
      val taxYear = TaxYear()

      val mockHttpHandler = mock[HttpHandler]
      when(mockHttpHandler.postToApi(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      val sut = createSUT(mock[NpsConfig], mock[TaxAccountUrls], mock[IabdUrls], mock[NpsIabdUpdateAmountFormats], mockHttpHandler)
      val result = Await.result(sut.updateTaxCodeAmount(randomNino, taxYear, 1, 1, NewEstimatedPay.code, 1, 12345), 5 seconds)

      result mustBe HodUpdateSuccess
    }

    "return a failure status if the update fails" in {
      val taxYear = TaxYear()

      val mockHttpHandler = mock[HttpHandler]
      when(mockHttpHandler.postToApi(any(), any(), any())(any(), any()))
        .thenReturn(Future.failed(new RuntimeException))

      val sut = createSUT(mock[NpsConfig], mock[TaxAccountUrls], mock[IabdUrls], mock[NpsIabdUpdateAmountFormats], mockHttpHandler)
      val result = Await.result(sut.updateTaxCodeAmount(randomNino, taxYear, 1, 1, NewEstimatedPay.code, 1, 12345), 5 seconds)

      result mustBe HodUpdateFailure
    }
  }

  private val originatorId = "blom"

  private def randomNino: Nino = new Generator(new Random).nextNino

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val jsonResponse = Json.obj(
    "taxYear" -> 2017,
    "totalLiability" -> Json.obj(
      "untaxedInterest" -> Json.obj(
        "totalTaxableIncome" -> 123)),
    "incomeSources" -> Json.arr(
      Json.obj(
        "employmentId" -> 1,
        "taxCode" -> "1150L",
        "name" -> "Employer1",
        "basisOperation" -> 1),
      Json.obj(
        "employmentId" -> 2,
        "taxCode" -> "1100L",
        "name" -> "Employer2",
        "basisOperation" -> 2)))

  private def createSUT(config: NpsConfig,
                        taxAccountUrls: TaxAccountUrls,
                        iabdUrls: IabdUrls,
                        formats: NpsIabdUpdateAmountFormats,
                        httpHandler: HttpHandler) =
    new TaxAccountNpsConnector(config, taxAccountUrls, iabdUrls, formats, httpHandler)
}
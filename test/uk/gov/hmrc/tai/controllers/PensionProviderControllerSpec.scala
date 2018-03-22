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

package uk.gov.hmrc.tai.controllers

import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.test.Helpers.{contentAsJson, status, _}
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.model.api.ApiResponse
import uk.gov.hmrc.tai.model.domain.AddPensionProvider
import uk.gov.hmrc.tai.service.PensionProviderService

import scala.concurrent.Future

class PensionProviderControllerSpec extends PlaySpec with MockitoSugar {

  "addPensionProvider" must {
    "return envelop Id" when {
      "called with valid add employment request" in {
        val envelopeId = "envelopId"
        val pensionProvider = AddPensionProvider("pensionProviderName", new LocalDate("2017-06-09"), "1234", "Yes", Some("123456789"))
        val json = Json.toJson(pensionProvider)
        val nino = nextNino

        val mockPensionProviderService = mock[PensionProviderService]
        when(mockPensionProviderService.addPensionProvider(Matchers.eq(nino), Matchers.eq(pensionProvider))(any()))
          .thenReturn(Future.successful(envelopeId))

        val sut = new PensionProviderController(mockPensionProviderService)
        val result = sut.addPensionProvider(nino)(FakeRequest("POST", "/", FakeHeaders(), json)
          .withHeaders(("content-type", "application/json")))

        status(result) mustBe OK
        contentAsJson(result).as[ApiResponse[String]] mustBe ApiResponse(envelopeId, Nil)
      }
    }
  }

  private def nextNino = new Generator().nextNino
  private implicit val hc = HeaderCarrier()
}

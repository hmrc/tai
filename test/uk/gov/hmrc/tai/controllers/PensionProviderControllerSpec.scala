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

package uk.gov.hmrc.tai.controllers

import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.when
import play.api.libs.json.Json
import play.api.test.Helpers.{contentAsJson, status, *}
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.tai.model.api.ApiResponse
import uk.gov.hmrc.tai.model.domain.{AddPensionProvider, IncorrectPensionProvider}
import uk.gov.hmrc.tai.service.PensionProviderService
import uk.gov.hmrc.tai.util.BaseSpec

import java.time.LocalDate
import scala.concurrent.Future

class PensionProviderControllerSpec extends BaseSpec {

  "addPensionProvider" must {
    "return envelope Id" when {
      "called with valid add pension request" in {
        val envelopeId = "envelopId"
        val pensionProvider =
          AddPensionProvider("pensionProviderName", LocalDate.parse("2017-06-09"), "1234", "Yes", Some("123456789"))
        val json = Json.toJson(pensionProvider)

        val mockPensionProviderService = mock[PensionProviderService]
        when(mockPensionProviderService.addPensionProvider(meq(nino), meq(pensionProvider))(any()))
          .thenReturn(Future.successful(envelopeId))

        val sut =
          new PensionProviderController(mockPensionProviderService, loggedInAuthenticationAuthJourney, cc)
        val result = sut.addPensionProvider(nino)(
          FakeRequest("POST", "/", FakeHeaders(), json)
            .withHeaders(("content-type", "application/json"))
        )

        status(result) mustBe OK
        contentAsJson(result).as[ApiResponse[String]] mustBe ApiResponse(envelopeId, Nil)
      }
    }
  }

  "incorrectPensionProvider" must {
    "return an envelope Id" when {
      "called with valid incorrect pension provider request" in {
        val envelopeId = "envelopeId"
        val pensionProvider = IncorrectPensionProvider("whatYouToldUs", "Yes", Some("123123"))
        val id = 1
        val mockPensionProviderService = mock[PensionProviderService]
        when(
          mockPensionProviderService
            .incorrectPensionProvider(meq(nino), meq(id), meq(pensionProvider))(any(), any())
        )
          .thenReturn(Future.successful(envelopeId))

        val sut = new PensionProviderController(mockPensionProviderService, loggedInAuthenticationAuthJourney, cc)
        val result = sut.incorrectPensionProvider(nino, id)(
          FakeRequest("POST", "/", FakeHeaders(), Json.toJson(pensionProvider))
            .withHeaders(("content-type", "application/json"))
        )

        status(result) mustBe OK
        contentAsJson(result).as[ApiResponse[String]] mustBe ApiResponse(envelopeId, Nil)
      }
    }
  }
}

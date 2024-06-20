/*
 * Copyright 2024 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, ok, post, urlEqualTo}
import play.api.Application
import play.api.http.Status.{UNAUTHORIZED, _}
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.benefits.connectors.PertaxConnector
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.tai.model.PertaxResponse
import uk.gov.hmrc.tai.util.{BaseSpec, WireMockHelper}

class PertaxConnectorSpec extends BaseSpec with WireMockHelper {

  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .configure(
      "microservice.services.pertax.port" -> server.port()
    )
    .build()

  lazy val pertaxConnector: PertaxConnector = inject[PertaxConnector]

  def authoriseUrl = s"/pertax/authorise"

  "PertaxConnector" must {
    "return a PertaxResponse with ACCESS_GRANTED code" in {
      server.stubFor(
        post(urlEqualTo(authoriseUrl))
          .willReturn(ok("{\"code\": \"ACCESS_GRANTED\", \"message\": \"Access granted\"}"))
      )

      val result = pertaxConnector.pertaxPostAuthorise.value.futureValue
        .getOrElse(PertaxResponse("INCORRECT RESPONSE", "INCORRECT"))
      result mustBe PertaxResponse("ACCESS_GRANTED", "Access granted")
    }

    "return a PertaxResponse with NO_HMRC_PT_ENROLMENT code with a redirect link" in {
      server.stubFor(
        post(urlEqualTo(authoriseUrl))
          .willReturn(
            ok(
              "{\"code\": \"NO_HMRC_PT_ENROLMENT\", \"message\": \"There is no valid HMRC PT enrolment\", \"redirect\": \"/tax-enrolment-assignment-frontend/account\"}"
            )
          )
      )

      val result = pertaxConnector.pertaxPostAuthorise.value.futureValue
        .getOrElse(PertaxResponse("INCORRECT RESPONSE", "INCORRECT"))
      result mustBe PertaxResponse(
        "NO_HMRC_PT_ENROLMENT",
        "There is no valid HMRC PT enrolment"
      )
    }

    "return a PertaxResponse with INVALID_AFFINITY code and an errorView" in {
      server.stubFor(
        post(urlEqualTo(authoriseUrl))
          .willReturn(
            ok(
              "{\"code\": \"INVALID_AFFINITY\", \"message\": \"The user is neither an individual or an organisation\", \"errorView\": {\"url\": \"/path/for/partial\", \"statusCode\": 401}}"
            )
          )
      )

      val result = pertaxConnector.pertaxPostAuthorise.value.futureValue
        .getOrElse(PertaxResponse("INCORRECT RESPONSE", "INCORRECT"))
      result mustBe PertaxResponse(
        "INVALID_AFFINITY",
        "The user is neither an individual or an organisation"
      )
    }

    "return a PertaxResponse with MCI_RECORD code and an errorView" in {
      server.stubFor(
        post(urlEqualTo(authoriseUrl))
          .willReturn(
            ok(
              "{\"code\": \"MCI_RECORD\", \"message\": \"Manual correspondence indicator is set\", \"errorView\": {\"url\": \"/path/for/partial\", \"statusCode\": 423}}"
            )
          )
      )

      val result = pertaxConnector.pertaxPostAuthorise.value.futureValue

      result mustBe a[Right[_, PertaxResponse]]
    }

    "return a UpstreamErrorResponse from a client or server error" when {

      List(
        BAD_REQUEST,
        NOT_FOUND,
        INTERNAL_SERVER_ERROR,
        SERVICE_UNAVAILABLE,
        UNAUTHORIZED
      ).foreach { error =>
        s"an $error is returned from the backend" in {

          server.stubFor(
            post(urlEqualTo(authoriseUrl)).willReturn(
              aResponse()
                .withStatus(error)
            )
          )

          val result = pertaxConnector.pertaxPostAuthorise.value.futureValue.swap
            .getOrElse(UpstreamErrorResponse("INCORRECT RESPONSE", IM_A_TEAPOT))
          result.statusCode mustBe error
        }
      }
    }
  }
}

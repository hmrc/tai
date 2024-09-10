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

package uk.gov.hmrc.tai.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.mockito.ArgumentMatchersSugar.eqTo
import play.api.Application
import play.api.http.Status._
import play.api.inject.bind
import play.api.libs.json.{JsArray, JsValue, Json}
import uk.gov.hmrc.auth.core.AuthorisedFunctions
import uk.gov.hmrc.http._
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.tai.auth.MicroserviceAuthorisedFunctions
import uk.gov.hmrc.tai.model.admin.HipToggleEmploymentDetails
import uk.gov.hmrc.tai.model.HodResponse
import uk.gov.hmrc.tai.model.nps2.NpsFormatter
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.Future
import scala.util.Random

class DefaultEmploymentDetailsConnectorSpec extends ConnectorBaseSpec with NpsFormatter {

  def intGen: Int = Random.nextInt(50)

  val year: Int = TaxYear().year
  val etag: Int = intGen
  val iabdType: Int = intGen
  val empSeqNum: Int = intGen

  val hipBaseUrl: String = s"/v1/api/employment/employee/${nino.nino}"
  val employmentsUrl: String = s"$hipBaseUrl/tax-year/$year/employment-details"
  lazy val sut: DefaultEmploymentDetailsConnector = inject[DefaultEmploymentDetailsConnector]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .disable[uk.gov.hmrc.tai.modules.LocalGuiceModule]
    .overrides(
      bind[AuthorisedFunctions].to[MicroserviceAuthorisedFunctions].eagerly(),
      bind[RtiConnector].to[DefaultRtiConnector],
      bind[TaxCodeHistoryConnector].to[DefaultTaxCodeHistoryConnector],
      bind[IabdConnector].to[DefaultIabdConnector],
      bind[EmploymentDetailsConnector].to[DefaultEmploymentDetailsConnector],
      bind[TaxAccountConnector].to[DefaultTaxAccountConnector]
    )
    .build()

  def verifyOutgoingUpdateHeaders(requestPattern: RequestPatternBuilder): Unit =
    server.verify(
      requestPattern
        .withHeader("Gov-Uk-Originator-Id", equalTo(hipOriginatorId))
        .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
        .withHeader(HeaderNames.xRequestId, equalTo(requestId))
        .withHeader(
          "CorrelationId",
          matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
        )
    )

  val employment = s"""{
                      |  "sequenceNumber": $intGen,
                      |  "startDate": "28/02/2023",
                      |  "taxDistrictNumber": "${intGen.toString}",
                      |  "payeNumber": "${intGen.toString}",
                      |  "employerName": "Big corp",
                      |  "employmentType": 1,
                      |  "worksNumber": "${intGen.toString}",
                      |  "cessationPayThisEmployment": $intGen
                      |}""".stripMargin

  val employmentAsJson: JsValue = Json.toJson(employment)

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleEmploymentDetails))).thenReturn(
      Future.successful(FeatureFlag(HipToggleEmploymentDetails, isEnabled = true))
    )
  }

  "DefaultEmploymentDetailsConnector" when {
    "getEmploymentDetailsAsEitherT is called" must {
      "return employments json with success" when {
        "given a nino and a year" in {

          val employmentListJson = JsArray(Seq(employmentAsJson))

          server.stubFor(
            get(urlEqualTo(employmentsUrl))
              .withHeader("clientId", equalTo("clientId"))
              .withHeader("clientSecret", equalTo("clientSecret"))
              .willReturn(
                aResponse()
                  .withStatus(OK)
                  .withBody(employmentListJson.toString())
                  .withHeader("ETag", s"$etag")
              )
          )

          sut.getEmploymentDetailsAsEitherT(nino, year).value.futureValue mustBe
            Right(HodResponse(employmentListJson, Some(etag)))

          verifyOutgoingUpdateHeaders(getRequestedFor(urlEqualTo(employmentsUrl)))
        }
      }

      "throw an exception" when {
        "connector returns 400" in {

          val exMessage = "Invalid query"

          server.stubFor(
            get(urlEqualTo(employmentsUrl))
              .withHeader("clientId", equalTo("clientId"))
              .withHeader("clientSecret", equalTo("clientSecret"))
              .willReturn(
                aResponse()
                  .withStatus(BAD_REQUEST)
                  .withBody(exMessage)
                  .withHeader("ETag", s"$etag")
              )
          )

          val result = sut.getEmploymentDetailsAsEitherT(nino, year).value.futureValue
          result mustBe a[Left[UpstreamErrorResponse, _]]
        }

        "connector returns 404" in {

          val exMessage = "Could not find employment"

          server.stubFor(
            get(urlEqualTo(employmentsUrl))
              .withHeader("clientId", equalTo("clientId"))
              .withHeader("clientSecret", equalTo("clientSecret"))
              .willReturn(
                aResponse()
                  .withStatus(NOT_FOUND)
                  .withBody(exMessage)
                  .withHeader("ETag", s"$etag")
              )
          )

          val result = sut.getEmploymentDetailsAsEitherT(nino, year).value.futureValue
          result mustBe a[Left[UpstreamErrorResponse, _]]
        }

        "connector returns 4xx" in {

          val exMessage = "Locked record"

          server.stubFor(
            get(urlEqualTo(employmentsUrl))
              .withHeader("clientId", equalTo("clientId"))
              .withHeader("clientSecret", equalTo("clientSecret"))
              .willReturn(
                aResponse()
                  .withStatus(LOCKED)
                  .withBody(exMessage)
                  .withHeader("ETag", s"$etag")
              )
          )

          val result = sut.getEmploymentDetailsAsEitherT(nino, year).value.futureValue
          result mustBe a[Left[UpstreamErrorResponse, _]]
        }

        "connector returns 500" in {

          val exMessage = "An error occurred"

          server.stubFor(
            get(urlEqualTo(employmentsUrl))
              .withHeader("clientId", equalTo("clientId"))
              .withHeader("clientSecret", equalTo("clientSecret"))
              .willReturn(
                aResponse()
                  .withStatus(INTERNAL_SERVER_ERROR)
                  .withBody(exMessage)
                  .withHeader("ETag", s"$etag")
              )
          )

          val result = sut.getEmploymentDetailsAsEitherT(nino, year).value.futureValue
          result mustBe a[Left[UpstreamErrorResponse, _]]
        }

        "connector returns 5xx" in {

          val exMessage = "Could not reach gateway"

          server.stubFor(
            get(urlEqualTo(employmentsUrl))
              .withHeader("clientId", equalTo("clientId"))
              .withHeader("clientSecret", equalTo("clientSecret"))
              .willReturn(
                aResponse()
                  .withStatus(BAD_GATEWAY)
                  .withBody(exMessage)
                  .withHeader("ETag", s"$etag")
              )
          )

          val result = sut.getEmploymentDetailsAsEitherT(nino, year).value.futureValue
          result mustBe a[Left[UpstreamErrorResponse, _]]
        }
      }
    }
  }
}

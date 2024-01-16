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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, equalTo, get, getRequestedFor, matching, ok, urlEqualTo}
import org.mockito.ArgumentMatchersSugar.eqTo
import play.api.libs.json.{JsResultException, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.{BAD_REQUEST, IM_A_TEAPOT, INTERNAL_SERVER_ERROR, NOT_FOUND, SERVICE_UNAVAILABLE}
import uk.gov.hmrc.http.{BadRequestException, HeaderNames, HttpException, NotFoundException}
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.controllers.predicates.AuthenticatedRequest
import uk.gov.hmrc.tai.factory.{TaxCodeHistoryFactory, TaxCodeRecordFactory}
import uk.gov.hmrc.tai.model.TaxCodeHistory
import uk.gov.hmrc.tai.model.admin.{RtiCallToggle, TaxCodeHistoryFromIfToggle}
import uk.gov.hmrc.tai.model.domain.income.BasisOperation.{Primary, Secondary}
import uk.gov.hmrc.tai.model.tai.TaxYear

import java.net.URL
import scala.concurrent.Future

class TaxCodeHistoryConnectorSpec extends ConnectorBaseSpec {


  private val taxYear = TaxYear()

  lazy val desConfig = inject[DesConfig]
  lazy val desUrls = inject[TaxCodeChangeFromDesUrl]
  lazy val taxCodeChangeFromDesUrl: String = {
    val path = new URL(desUrls.taxCodeChangeFromDesUrl(nino, taxYear))
    s"${path.getPath}?${path.getQuery}"
  }

  lazy val ifUrls = inject[TaxCodeChangeFromIfUrl]
  lazy val taxCodeChangeFromIfUrl: String = {
    val path = new URL(ifUrls.taxCodeChangeUrl(nino, taxYear))
    s"${path.getPath}?${path.getQuery}"
  }

  def createSut(): TaxCodeHistoryConnector = new DefaultTaxCodeHistoryConnector(inject[HttpHandler], desConfig, desUrls, ifUrls, mockFeatureFlagService)

  implicit val authenticatedRequest = AuthenticatedRequest(FakeRequest(), nino)

  override def beforeEach(): Unit = {
    server.resetAll()
    reset(mockFeatureFlagService)
    when(mockFeatureFlagService.get(eqTo[FeatureFlagName](RtiCallToggle))).thenReturn(
      Future.successful(FeatureFlag(RtiCallToggle, isEnabled = false))
    )
  }

  case class desIFToggle(name: String, toggleState: Boolean)

  "taxCodeHistory" when {
    List( desIFToggle("IF", true),
          desIFToggle("DES", false)
    ).foreach { toggle =>
      lazy val url = if(toggle.toggleState) taxCodeChangeFromIfUrl else taxCodeChangeFromDesUrl
      s"toggled to use ${toggle.name}" must {
        "return tax code change json" when {
          "payroll number is returned" in {
            val expectedJsonResponse = TaxCodeHistoryFactory.createTaxCodeHistoryJson(nino)

            server.stubFor(
              get(urlEqualTo(url)).willReturn(ok(expectedJsonResponse.toString))
            )

            when(mockFeatureFlagService.get(TaxCodeHistoryFromIfToggle))
              .thenReturn(Future.successful(FeatureFlag(TaxCodeHistoryFromIfToggle, isEnabled = toggle.toggleState)))

            val result = createSut().taxCodeHistory(nino, taxYear).futureValue

            result mustEqual TaxCodeHistoryFactory.createTaxCodeHistory(nino)

            server.verify(
              getRequestedFor(urlEqualTo(url))
                .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
                .withHeader(HeaderNames.xRequestId, equalTo(requestId))
                .withHeader(
                  "CorrelationId",
                  matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")))
          }
          "payroll number is not returned" in {
            val taxCodeRecord = Seq(
              TaxCodeRecordFactory.createNoPayrollNumberJson(employmentType = Primary),
              TaxCodeRecordFactory.createNoPayrollNumberJson(employmentType = Secondary)
            )

            val expectedJsonResponse = TaxCodeHistoryFactory.createTaxCodeHistoryJson(nino, taxCodeRecord)

            server.stubFor(
              get(urlEqualTo(url)).willReturn(ok(expectedJsonResponse.toString))
            )

            when(mockFeatureFlagService.get(TaxCodeHistoryFromIfToggle))
              .thenReturn(Future.successful(FeatureFlag(TaxCodeHistoryFromIfToggle, isEnabled = toggle.toggleState)))

            val result = createSut().taxCodeHistory(nino, taxYear).futureValue

            result mustEqual TaxCodeHistory(
              nino.nino,
              Seq(
                TaxCodeRecordFactory.createPrimaryEmployment(payrollNumber = None),
                TaxCodeRecordFactory.createSecondaryEmployment(payrollNumber = None)
              )
            )

            server.verify(
              getRequestedFor(urlEqualTo(url))
                .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
                .withHeader(HeaderNames.xRequestId, equalTo(requestId))
                .withHeader(
                  "CorrelationId",
                  matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")))
          }
        }

        "respond with a JsResultException when given invalid json" in {
          val expectedJsonResponse = Json.obj(
            "invalid" -> "invalidjson"
          )

          server.stubFor(
            get(urlEqualTo(url)).willReturn(ok(expectedJsonResponse.toString))
          )

          when(mockFeatureFlagService.get(TaxCodeHistoryFromIfToggle))
            .thenReturn(Future.successful(FeatureFlag(TaxCodeHistoryFromIfToggle, isEnabled = toggle.toggleState)))

          val connector = createSut()

          val result = connector.taxCodeHistory(nino, taxYear).failed.futureValue

          result mustBe a[JsResultException]
        }
        "return an error" when {
          "a 400 occurs" in {

            server.stubFor(get(urlEqualTo(url)).willReturn(aResponse().withStatus(BAD_REQUEST)))

            when(mockFeatureFlagService.get(TaxCodeHistoryFromIfToggle))
              .thenReturn(Future.successful(FeatureFlag(TaxCodeHistoryFromIfToggle, isEnabled = toggle.toggleState)))

            val connector = createSut()

            val result = connector.taxCodeHistory(nino, taxYear).failed.futureValue

            result mustBe a[BadRequestException]
          }

          "a 404 occurs" in {

            server.stubFor(get(urlEqualTo(url)).willReturn(aResponse().withStatus(NOT_FOUND)))

            when(mockFeatureFlagService.get(TaxCodeHistoryFromIfToggle))
              .thenReturn(Future.successful(FeatureFlag(TaxCodeHistoryFromIfToggle, isEnabled = toggle.toggleState)))

            val connector = createSut()

            val result = connector.taxCodeHistory(nino, taxYear).failed.futureValue

            result mustBe a[NotFoundException]
          }

          List(
            IM_A_TEAPOT,
            INTERNAL_SERVER_ERROR,
            SERVICE_UNAVAILABLE
          ).foreach { httpResponse =>
            s"a $httpResponse occurs" in {

              server.stubFor(get(urlEqualTo(url)).willReturn(aResponse().withStatus(httpResponse)))

              when(mockFeatureFlagService.get(TaxCodeHistoryFromIfToggle))
                .thenReturn(Future.successful(FeatureFlag(TaxCodeHistoryFromIfToggle, isEnabled = toggle.toggleState)))

              val connector = createSut()

              val result = connector.taxCodeHistory(nino, taxYear).failed.futureValue

              result mustBe a[HttpException]
            }
          }
        }
      }
    }
  }


}

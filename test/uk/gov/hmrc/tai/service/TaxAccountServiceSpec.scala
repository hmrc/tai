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

package uk.gov.hmrc.tai.service

import com.codahale.metrics.Timer
import data.NpsData
import org.joda.time.LocalDate
import org.mockito.Matchers.{any, eq => Meq}
import org.mockito.Mockito.{never, times, verify, when}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.test.Helpers.OK
import uk.gov.hmrc.crypto.Protected
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.tai.config.{FeatureTogglesConfig, MongoConfig, NpsConfig}
import uk.gov.hmrc.tai.connectors._
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.model.nps.{NpsDate, NpsEmployment, Person, PersonDetails}
import uk.gov.hmrc.tai.model.nps2.MongoFormatter
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.TaiConstants

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class TaxAccountServiceSpec extends PlaySpec with MockitoSugar with MongoFormatter {

  implicit val hc = HeaderCarrier(sessionId = Some(SessionId("some session id")))

  "taiData" must {
    "return a session data instance" when {
      "cacheConnector returns some data" in {
        val mockTaiService = mock[TaiService]
        when(mockTaiService.getAutoUpdateResults(any(), any())(any()))
          .thenReturn(Future.successful((employments, Nil, npsEmployment, Nil, Nil)))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.find[SessionData](any(), any())(any(), Meq(hc)))
          .thenReturn(Future.successful(Some(sessionData())))
        when(mockCacheConnector.createOrUpdate[SessionData](any(), any(), any())(any(), Meq(hc)))
          .thenReturn(Future.successful(sessionData()))

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getCalculatedTaxAccountRawResponse(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.toJson("")))))

        val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
        when(mockCitizenDetailsConnector.getPersonDetails(any())(any(), any()))
          .thenReturn(Future.successful(gatekeeperPersonDetails))

        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEnabled)
          .thenReturn(true)

        val sut = createSut(
          mockTaiService,
          mockCacheConnector,
          mockCitizenDetailsConnector,
          mockNpsConnector,
          mock[DesConnector],
          mock[Metrics],
          mock[NpsConfig],
          mockMongoConfig,
          mock[FeatureTogglesConfig]
        )

        val result = Await.result(sut.taiData(nino, 2017)(hc), 5.seconds)

        result.nino mustBe nino.nino

        verify(mockCacheConnector, times(1))
          .find[SessionData](Meq(cacheId), any())(any(), Meq(hc))
      }

      "cacheConnector returns None" in {
        val mockTaiService = mock[TaiService]
        when(mockTaiService.getAutoUpdateResults(any(), any())(any()))
          .thenReturn(Future.successful((employments, Nil, npsEmployment, Nil, Nil)))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.find[SessionData](any(), any())(any(), Meq(hc)))
          .thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdate[SessionData](any(), any(), any())(any(), Meq(hc)))
          .thenAnswer(reflectedSessionAnswer)

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getCalculatedTaxAccountRawResponse(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.toJson("")))))

        val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
        when(mockCitizenDetailsConnector.getPersonDetails(any())(any(), any()))
          .thenReturn(Future.successful(gatekeeperPersonDetails))

        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEnabled)
          .thenReturn(true)

        val sut = createSut(
          mockTaiService,
          mockCacheConnector,
          mockCitizenDetailsConnector,
          mockNpsConnector,
          mock[DesConnector],
          mock[Metrics],
          mock[NpsConfig],
          mockMongoConfig,
          mock[FeatureTogglesConfig]
        )

        Await.result(sut.taiData(nino, 2017)(hc), 5.seconds) mustBe sessionData(
          gateKeeperTaxSummaryDetails,
          gatekeeperTaiRoot)

        verify(mockCacheConnector, times(1))
          .find[SessionData](Meq(cacheId), any())(any(), Meq(hc))
        verify(mockCacheConnector, times(1))
          .createOrUpdate[SessionData](Meq(cacheId), any(), any())(any(), Meq(hc))
      }
    }

    "return session data containing only gatekeeper details" when {
      "the user is not permitted to access the service" in {
        val mockTaiService = mock[TaiService]
        when(mockTaiService.getAutoUpdateResults(any(), any())(any()))
          .thenReturn(Future.successful((employments, Nil, npsEmployment, Nil, Nil)))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.find[SessionData](any(), any())(any(), Meq(hc)))
          .thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdate[SessionData](any(), any(), any())(any(), Meq(hc)))
          .thenAnswer(reflectedSessionAnswer)

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getCalculatedTaxAccountRawResponse(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.toJson("")))))

        val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
        when(mockCitizenDetailsConnector.getPersonDetails(any())(any(), any()))
          .thenReturn(Future.successful(gatekeeperPersonDetails))

        val sut = createSut(
          mockTaiService,
          mockCacheConnector,
          mockCitizenDetailsConnector,
          mockNpsConnector,
          mock[DesConnector],
          mock[Metrics],
          mock[NpsConfig],
          mock[MongoConfig],
          mock[FeatureTogglesConfig]
        )

        val sessionData = Await.result(sut.taiData(nino, 2017)(hc), 5.seconds)

        sessionData.taxSummaryDetailsCY mustBe gateKeeperTaxSummaryDetails
        verify(mockCitizenDetailsConnector, times(1))
          .getPersonDetails(any())(any(), any())
      }
    }

    "store session data in unencrypted form" when {
      "encryption is disabled and data is not already held in cache" in {
        val mockTaiService = mock[TaiService]
        when(mockTaiService.getAutoUpdateResults(any(), any())(any()))
          .thenReturn(Future.successful((employments, Nil, npsEmployment, Nil, Nil)))
        when(mockTaiService.getCalculatedTaxAccount(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(taxSummaryDetails))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.find[SessionData](any(), any())(any(), Meq(hc)))
          .thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdate[SessionData](any(), any(), any())(any(), Meq(hc)))
          .thenAnswer(reflectedSessionAnswer)

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getCalculatedTaxAccountRawResponse(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.toJson("")))))

        val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
        when(mockCitizenDetailsConnector.getPersonDetails(any())(any(), any()))
          .thenReturn(Future.successful(nonGatekeeperPersonDetails))

        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEnabled)
          .thenReturn(true)

        val mockTimerContext = mock[Timer.Context]
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSut(
          mockTaiService,
          mockCacheConnector,
          mockCitizenDetailsConnector,
          mockNpsConnector,
          mock[DesConnector],
          mockMetrics,
          mock[NpsConfig],
          mockMongoConfig,
          mock[FeatureTogglesConfig]
        )

        Await.result(sut.taiData(nino, 2017)(hc), 5.seconds)

        verify(mockCacheConnector, times(1))
          .createOrUpdate[SessionData](Meq(cacheId), Meq(sessionData()), any())(any(), Meq(hc))
      }
    }

    "return session data from NPS" when {
      "cache is not enabled" in {
        val mockTaiService = mock[TaiService]
        when(mockTaiService.getAutoUpdateResults(any(), any())(any()))
          .thenReturn(Future.successful((employments, Nil, npsEmployment, Nil, Nil)))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.find[SessionData](any(), any())(any(), Meq(hc)))
          .thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdate[SessionData](any(), any(), any())(any(), Meq(hc)))
          .thenAnswer(reflectedSessionAnswer)

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getCalculatedTaxAccountRawResponse(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.toJson("")))))

        val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
        when(mockCitizenDetailsConnector.getPersonDetails(any())(any(), any()))
          .thenReturn(Future.successful(gatekeeperPersonDetails))

        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEnabled)
          .thenReturn(false)

        val sut = createSut(
          mockTaiService,
          mockCacheConnector,
          mockCitizenDetailsConnector,
          mockNpsConnector,
          mock[DesConnector],
          mock[Metrics],
          mock[NpsConfig],
          mockMongoConfig,
          mock[FeatureTogglesConfig]
        )

        val sessionData = Await.result(sut.taiData(nino, 2017)(hc), 5.seconds)

        sessionData.taxSummaryDetailsCY mustBe gateKeeperTaxSummaryDetails

        verify(mockCitizenDetailsConnector, times(1))
          .getPersonDetails(any())(any(), any())
        verify(mockCacheConnector, never())
          .find[SessionData](cacheId)
      }

      "cache is enabled" in {
        val mockTaiService = mock[TaiService]
        when(mockTaiService.getAutoUpdateResults(any(), any())(any()))
          .thenReturn(Future.successful((employments, Nil, npsEmployment, Nil, Nil)))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.find[SessionData](any(), any())(any(), Meq(hc)))
          .thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdate[SessionData](any(), any(), any())(any(), Meq(hc)))
          .thenAnswer(reflectedSessionAnswer)

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getCalculatedTaxAccountRawResponse(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.toJson("")))))

        val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
        when(mockCitizenDetailsConnector.getPersonDetails(any())(any(), any()))
          .thenReturn(Future.successful(gatekeeperPersonDetails))

        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEnabled)
          .thenReturn(true)

        val sut = createSut(
          mockTaiService,
          mockCacheConnector,
          mockCitizenDetailsConnector,
          mockNpsConnector,
          mock[DesConnector],
          mock[Metrics],
          mock[NpsConfig],
          mockMongoConfig,
          mock[FeatureTogglesConfig]
        )

        val sessionData = Await.result(sut.taiData(nino, 2017)(hc), 5.seconds)

        sessionData.taxSummaryDetailsCY mustBe gateKeeperTaxSummaryDetails
        verify(mockCitizenDetailsConnector, times(1)).getPersonDetails(any())(any(), any())
        verify(mockCacheConnector, times(1)).find[SessionData](cacheId)
      }
    }
  }

  "version" must {
    "return the version" in {

      val sessionIdValue = "ABCD1234"

      val sd = sessionData().copy(taiRoot = Some(nonGatekeeperTaiRoot.copy(version = 1)))

      val mockTaiService = mock[TaiService]
      when(mockTaiService.getAutoUpdateResults(any(), any())(any()))
        .thenReturn(Future.successful((employments, Nil, npsEmployment, Nil, Nil)))

      val mockCacheConnector = mock[CacheConnector]
      when(mockCacheConnector.find[SessionData](any(), any())(any(), Meq(hc)))
        .thenReturn(Future.successful(Some(sd)))
      when(mockCacheConnector.createOrUpdate[SessionData](any(), any(), any())(any(), Meq(hc)))
        .thenReturn(Future.successful(sd))

      val mockNpsConnector = mock[NpsConnector]
      when(mockNpsConnector.getCalculatedTaxAccountRawResponse(any(), any())(any()))
        .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.toJson("")))))

      val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
      when(mockCitizenDetailsConnector.getPersonDetails(any())(any(), any()))
        .thenReturn(Future.successful(gatekeeperPersonDetails))

      val mockMongoConfig = mock[MongoConfig]
      when(mockMongoConfig.mongoEnabled)
        .thenReturn(true)

      val sut = createSut(
        mockTaiService,
        mockCacheConnector,
        mockCitizenDetailsConnector,
        mockNpsConnector,
        mock[DesConnector],
        mock[Metrics],
        mock[NpsConfig],
        mockMongoConfig,
        mock[FeatureTogglesConfig]
      )

      val version =
        Await.result(sut.version(nino, year)(hc), 5.seconds)

      version mustBe Some(1)
    }

    "return None" when {
      "tai root is None" in {

        val sessionIdValue = "ABCD1234"

        val sd = sessionData().copy(taiRoot = None)

        val mockTaiService = mock[TaiService]
        when(mockTaiService.getAutoUpdateResults(any(), any())(any()))
          .thenReturn(Future.successful((employments, Nil, npsEmployment, Nil, Nil)))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.find[SessionData](any(), any())(any(), Meq(hc)))
          .thenReturn(Future.successful(Some(sd)))
        when(mockCacheConnector.createOrUpdate[SessionData](any(), any(), any())(any(), Meq(hc)))
          .thenReturn(Future.successful(sd))

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getCalculatedTaxAccountRawResponse(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.toJson("")))))

        val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
        when(mockCitizenDetailsConnector.getPersonDetails(any())(any(), any()))
          .thenReturn(Future.successful(gatekeeperPersonDetails))

        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEnabled)
          .thenReturn(true)

        val sut = createSut(
          mockTaiService,
          mockCacheConnector,
          mockCitizenDetailsConnector,
          mockNpsConnector,
          mock[DesConnector],
          mock[Metrics],
          mock[NpsConfig],
          mockMongoConfig,
          mock[FeatureTogglesConfig]
        )

        val version =
          Await.result(sut.version(nino, year)(hc), 5.seconds)

        version mustBe None
      }
    }
  }

  "personDetails" must {
    "return the persons details from citizenDetails and convert it to TaiRoot" in {
      val mockTaiService = mock[TaiService]
      when(mockTaiService.getAutoUpdateResults(any(), any())(any()))
        .thenReturn(Future.successful((employments, Nil, npsEmployment, Nil, Nil)))

      val mockCacheConnector = mock[CacheConnector]
      when(mockCacheConnector.find[SessionData](any(), any())(any(), Meq(hc)))
        .thenReturn(Future.successful(None))
      when(mockCacheConnector.createOrUpdate[SessionData](any(), any(), any())(any(), Meq(hc)))
        .thenAnswer(reflectedSessionAnswer)

      val mockNpsConnector = mock[NpsConnector]
      when(mockNpsConnector.getCalculatedTaxAccountRawResponse(any(), any())(any()))
        .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.toJson("")))))

      val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
      when(mockCitizenDetailsConnector.getPersonDetails(any())(any(), any()))
        .thenReturn(Future.successful(nonGatekeeperPersonDetails))

      val sut = createSut(
        mockTaiService,
        mockCacheConnector,
        mockCitizenDetailsConnector,
        mockNpsConnector,
        mock[DesConnector],
        mock[Metrics],
        mock[NpsConfig],
        mock[MongoConfig],
        mock[FeatureTogglesConfig]
      )

      val awaitResult = Await.result(sut.personDetails(nino)(hc), 5.seconds)
      awaitResult mustBe nonGatekeeperTaiRoot
    }
  }

  "calculatedTaxAccountRawResponse" must {
    "return a raw response" when {
      "calling des" in {
        val mockTaiService = mock[TaiService]
        when(mockTaiService.getAutoUpdateResults(any(), any())(any()))
          .thenReturn(Future.successful((employments, Nil, npsEmployment, Nil, Nil)))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.find[SessionData](any(), any())(any(), Meq(hc)))
          .thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdate[SessionData](any(), any(), any())(any(), Meq(hc)))
          .thenAnswer(reflectedSessionAnswer)

        val mockNpsConnector = mock[NpsConnector]

        val mockDesConnector = mock[DesConnector]
        when(mockDesConnector.getCalculatedTaxAccountRawResponseFromDes(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.toJson("")))))

        val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
        when(mockCitizenDetailsConnector.getPersonDetails(any())(any(), any()))
          .thenReturn(Future.successful(gatekeeperPersonDetails))

        val mockFeatureToggleConfig = mock[FeatureTogglesConfig]
        when(mockFeatureToggleConfig.desEnabled)
          .thenReturn(true)

        val sut = createSut(
          mockTaiService,
          mockCacheConnector,
          mockCitizenDetailsConnector,
          mockNpsConnector,
          mockDesConnector,
          mock[Metrics],
          mock[NpsConfig],
          mock[MongoConfig],
          mockFeatureToggleConfig
        )

        Await.result(sut.calculatedTaxAccountRawResponse(nino, 2016)(hc), 5.seconds)

        verify(mockDesConnector, times(1))
          .getCalculatedTaxAccountRawResponseFromDes(any(), any())(any())
        verify(mockNpsConnector, never)
          .getCalculatedTaxAccountRawResponse(any(), any())(any())
      }

      "calling nps" in {
        val mockTaiService = mock[TaiService]
        when(mockTaiService.getAutoUpdateResults(any(), any())(any()))
          .thenReturn(Future.successful((employments, Nil, npsEmployment, Nil, Nil)))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.find[SessionData](any(), any())(any(), Meq(hc)))
          .thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdate[SessionData](any(), any(), any())(any(), Meq(hc)))
          .thenAnswer(reflectedSessionAnswer)

        val mockDesConnector = mock[DesConnector]

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getCalculatedTaxAccountRawResponse(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.toJson("")))))

        val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
        when(mockCitizenDetailsConnector.getPersonDetails(any())(any(), any()))
          .thenReturn(Future.successful(gatekeeperPersonDetails))

        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEnabled)
          .thenReturn(true)

        val sut = createSut(
          mockTaiService,
          mockCacheConnector,
          mockCitizenDetailsConnector,
          mockNpsConnector,
          mockDesConnector,
          mock[Metrics],
          mock[NpsConfig],
          mockMongoConfig,
          mock[FeatureTogglesConfig]
        )

        Await.result(sut.calculatedTaxAccountRawResponse(nino, 2016)(hc), 5.seconds)

        verify(mockDesConnector, never)
          .getCalculatedTaxAccountRawResponseFromDes(any(), any())(any())
        verify(mockNpsConnector, times(1))
          .getCalculatedTaxAccountRawResponse(any(), any())(any())

      }
    }
  }

  "taxSummaryDetails" must {
    "return tax summary details" in {
      val taxSummaryDetails = NpsData.getTaxSummary

      val mockTaiService = mock[TaiService]
      when(mockTaiService.getAutoUpdateResults(any(), any())(any()))
        .thenReturn(Future.successful((employments, Nil, npsEmployment, Nil, Nil)))
      when(mockTaiService.getCalculatedTaxAccount(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(taxSummaryDetails))

      val mockCacheConnector = mock[CacheConnector]
      when(mockCacheConnector.find[SessionData](any(), any())(any(), Meq(hc)))
        .thenReturn(Future.successful(None))
      when(mockCacheConnector.createOrUpdate[SessionData](any(), any(), any())(any(), Meq(hc)))
        .thenAnswer(reflectedSessionAnswer)

      val mockNpsConnector = mock[NpsConnector]
      when(mockNpsConnector.getCalculatedTaxAccountRawResponse(any(), any())(any()))
        .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.toJson("")))))

      val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
      when(mockCitizenDetailsConnector.getPersonDetails(any())(any(), any()))
        .thenReturn(Future.successful(gatekeeperPersonDetails))

      val mockTimerContext = mock[Timer.Context]
      val mockMetrics = mock[Metrics]
      when(mockMetrics.startTimer(any()))
        .thenReturn(mockTimerContext)

      val sut = createSut(
        mockTaiService,
        mockCacheConnector,
        mockCitizenDetailsConnector,
        mockNpsConnector,
        mock[DesConnector],
        mockMetrics,
        mock[NpsConfig],
        mock[MongoConfig],
        mock[FeatureTogglesConfig]
      )

      val summaryDetails = Await.result(sut.taxSummaryDetails(nino, 2014)(hc), 5 seconds)

      summaryDetails mustBe taxSummaryDetails
    }

    "return appropriate npserror" when {
      "api throws service unavailable exception" in {
        val failureMsg = Json.obj(
          "message"          -> "Service Unavailable",
          "statusCode"       -> 503,
          "appStatusMessage" -> "Service Unavailable",
          "requestUri"       -> s"nps/person/${nino.nino}/tax-account/2014/calculation"
        )

        val mockTaiService = mock[TaiService]
        when(mockTaiService.getAutoUpdateResults(any(), any())(any()))
          .thenReturn(Future.successful((employments, Nil, npsEmployment, Nil, Nil)))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.find[SessionData](any(), any())(any(), Meq(hc)))
          .thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdate[SessionData](any(), any(), any())(any(), Meq(hc)))
          .thenAnswer(reflectedSessionAnswer)

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getCalculatedTaxAccountRawResponse(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, responseJson = Some(failureMsg))))

        val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
        when(mockCitizenDetailsConnector.getPersonDetails(any())(any(), any()))
          .thenReturn(Future.successful(gatekeeperPersonDetails))

        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEnabled)
          .thenReturn(true)

        val mockTimerContext = mock[Timer.Context]
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSut(
          mockTaiService,
          mockCacheConnector,
          mockCitizenDetailsConnector,
          mockNpsConnector,
          mock[DesConnector],
          mockMetrics,
          mock[NpsConfig],
          mockMongoConfig,
          mock[FeatureTogglesConfig]
        )

        val summaryDetails = sut.taxSummaryDetails(nino, 2014)(hc)

        val ex = the[NpsError] thrownBy Await.result(summaryDetails, 5 seconds)
        Json.parse(ex.message) mustBe failureMsg

      }

      "api throws bad request exception" in {
        val failureMsg = "Cannot complete a Coding Calculation without a Primary Employment"

        val mockTaiService = mock[TaiService]
        when(mockTaiService.getAutoUpdateResults(any(), any())(any()))
          .thenReturn(Future.successful((employments, Nil, npsEmployment, Nil, Nil)))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.find[SessionData](any(), any())(any(), Meq(hc)))
          .thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdate[SessionData](any(), any(), any())(any(), Meq(hc)))
          .thenAnswer(reflectedSessionAnswer)

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getCalculatedTaxAccountRawResponse(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, responseString = Some(failureMsg))))

        val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
        when(mockCitizenDetailsConnector.getPersonDetails(any())(any(), any()))
          .thenReturn(Future.successful(gatekeeperPersonDetails))

        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEnabled)
          .thenReturn(true)

        val mockTimerContext = mock[Timer.Context]
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSut(
          mockTaiService,
          mockCacheConnector,
          mockCitizenDetailsConnector,
          mockNpsConnector,
          mock[DesConnector],
          mockMetrics,
          mock[NpsConfig],
          mockMongoConfig,
          mock[FeatureTogglesConfig]
        )

        val summaryDetails = sut.taxSummaryDetails(nino, 2014)(hc)

        val ex = the[NpsError] thrownBy Await.result(summaryDetails, 5 seconds)
        ex.message mustBe failureMsg

      }
    }
  }

  "updateTaiData" must {
    "call cache connector with unencrypted data" when {
      "cache is enabled and mongo encryption is disabled" in {
        val mockTaiService = mock[TaiService]
        when(mockTaiService.getAutoUpdateResults(any(), any())(any()))
          .thenReturn(Future.successful((employments, Nil, npsEmployment, Nil, Nil)))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.find[SessionData](any(), any())(any(), Meq(hc)))
          .thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdate[SessionData](any(), any(), any())(any(), Meq(hc)))
          .thenAnswer(reflectedSessionAnswer)

        val mockDesConnector = mock[DesConnector]
        when(mockDesConnector.getCalculatedTaxAccountRawResponseFromDes(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.toJson("")))))

        val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
        when(mockCitizenDetailsConnector.getPersonDetails(any())(any(), any()))
          .thenReturn(Future.successful(gatekeeperPersonDetails))

        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEnabled)
          .thenReturn(true)
        when(mockMongoConfig.mongoEncryptionEnabled)
          .thenReturn(false)

        val sut = createSut(
          mockTaiService,
          mockCacheConnector,
          mockCitizenDetailsConnector,
          mock[NpsConnector],
          mock[DesConnector],
          mock[Metrics],
          mock[NpsConfig],
          mockMongoConfig,
          mock[FeatureTogglesConfig]
        )

        val data = Await.result(sut.updateTaiData(nino, sessionData())(hc), 5.seconds)

        data mustBe sessionData()
        verify(mockCacheConnector, times(1))
          .createOrUpdate[SessionData](any(), Meq(sessionData()), any())(any(), Meq(hc))
        verify(mockCacheConnector, never())
          .createOrUpdate[Protected[SessionData]](any(), Meq(Protected(sessionData())), any())(any(), Meq(hc))
      }
    }

    "not call cache connector" when {
      "cache is disabled" in {
        val mockTaiService = mock[TaiService]
        when(mockTaiService.getAutoUpdateResults(any(), any())(any()))
          .thenReturn(Future.successful((employments, Nil, npsEmployment, Nil, Nil)))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.find[SessionData](any(), any())(any(), Meq(hc)))
          .thenReturn(Future.successful(None))
        when(mockCacheConnector.createOrUpdate[SessionData](any(), any(), any())(any(), Meq(hc)))
          .thenAnswer(reflectedSessionAnswer)

        val mockDesConnector = mock[DesConnector]
        when(mockDesConnector.getCalculatedTaxAccountRawResponseFromDes(any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.toJson("")))))

        val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
        when(mockCitizenDetailsConnector.getPersonDetails(any())(any(), any()))
          .thenReturn(Future.successful(gatekeeperPersonDetails))

        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEnabled)
          .thenReturn(false)

        val sut = createSut(
          mockTaiService,
          mockCacheConnector,
          mockCitizenDetailsConnector,
          mock[NpsConnector],
          mock[DesConnector],
          mock[Metrics],
          mock[NpsConfig],
          mockMongoConfig,
          mock[FeatureTogglesConfig]
        )

        val data = Await.result(sut.updateTaiData(nino, sessionData())(hc), 5.seconds)

        data mustBe sessionData()
        verify(mockCacheConnector, never())
          .createOrUpdate[SessionData](any(), Meq(sessionData()), any())(any(), Meq(hc))
        verify(mockCacheConnector, never())
          .createOrUpdate[Protected[SessionData]](any(), Meq(Protected(sessionData())), any())(any(), Meq(hc))
      }
    }
  }

  "invalidateTaiData" must {
    "remove the session data" when {
      "cache is enabled" in {
        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.removeById(any())(Meq(hc)))
          .thenReturn(Future.successful(true))

        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEnabled)
          .thenReturn(true)
        when(mockMongoConfig.mongoEncryptionEnabled)
          .thenReturn(false)

        val sut = createSut(
          mock[TaiService],
          mockCacheConnector,
          mock[CitizenDetailsConnector],
          mock[NpsConnector],
          mock[DesConnector],
          mock[Metrics],
          mock[NpsConfig],
          mockMongoConfig,
          mock[FeatureTogglesConfig]
        )

        sut.invalidateTaiCacheData(nino)(hc)

        verify(mockCacheConnector, times(1))
          .removeById(any())(Meq(hc))
      }
    }

    "not call remove data operation" when {
      "cache is disabled" in {
        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.removeById(any())(Meq(hc)))
          .thenReturn(Future.successful(true))

        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEnabled)
          .thenReturn(false)
        when(mockMongoConfig.mongoEncryptionEnabled)
          .thenReturn(false)

        val sut = createSut(
          mock[TaiService],
          mockCacheConnector,
          mock[CitizenDetailsConnector],
          mock[NpsConnector],
          mock[DesConnector],
          mock[Metrics],
          mock[NpsConfig],
          mockMongoConfig,
          mock[FeatureTogglesConfig]
        )

        sut.invalidateTaiCacheData(nino)(hc)

        verify(mockCacheConnector, never())
          .createOrUpdate(any(), any(), any())(any(), Meq(hc))
      }
    }
  }

  private val nino = new Generator(new Random).nextNino
  private val cacheId = CacheId(nino)
  private val year = TaxYear().year
  private val taxSummaryDetails = TaxSummaryDetails(nino = nino.nino, version = 0)

  private def sessionData(
    taxSummaryDetails: TaxSummaryDetails = taxSummaryDetails,
    taiRoot: TaiRoot = nonGatekeeperTaiRoot) =
    SessionData(nino = nino.nino, taxSummaryDetailsCY = taxSummaryDetails, taiRoot = Some(taiRoot))

  private val nonGatekeeperTaiRoot = TaiRoot(
    nino = nino.nino,
    version = 0,
    title = "",
    firstName = "",
    secondName = None,
    surname = "",
    name = " ",
    manualCorrespondenceInd = false,
    deceasedIndicator = None
  )

  private val gatekeeperTaiRoot = TaiRoot(
    nino = nino.nino,
    version = 0,
    title = "",
    firstName = "",
    secondName = None,
    surname = "",
    name = " ",
    manualCorrespondenceInd = true,
    deceasedIndicator = None
  )

  private val gatekeeperPersonDetails = PersonDetails(
    "0",
    Person(
      firstName = None,
      middleName = None,
      lastName = None,
      initials = None,
      title = None,
      honours = None,
      sex = None,
      dateOfBirth = None,
      nino = nino,
      manualCorrespondenceInd = Some(true),
      deceased = None
    )
  )

  private val nonGatekeeperPersonDetails = PersonDetails(
    "0",
    Person(
      firstName = None,
      middleName = None,
      lastName = None,
      initials = None,
      title = None,
      honours = None,
      sex = None,
      dateOfBirth = None,
      nino = nino,
      manualCorrespondenceInd = None,
      deceased = None
    )
  )

  private val gateKeeper = GateKeeper(
    gateKeepered = true,
    List(
      GateKeeperRule(
        Some(TaiConstants.mciGateKeeperType),
        Some(TaiConstants.mciGatekeeperId),
        Some(TaiConstants.mciGatekeeperDescr))))

  private val gateKeeperTaxSummaryDetails = TaxSummaryDetails(
    nino = nino.nino,
    version = 0,
    increasesTax = None,
    decreasesTax = None,
    totalLiability = None,
    adjustedNetIncome = BigDecimal(0),
    extensionReliefs = None,
    gateKeeper = Some(gateKeeper),
    taxCodeDetails = None,
    incomeData = None,
    cyPlusOneChange = None,
    cyPlusOneSummary = None,
    accounts = Nil,
    ceasedEmploymentDetail = None
  )

  private val npsEmployment = List(
    nps2.NpsEmployment(
      Some("EMPLOYER1"),
      isPrimary = true,
      1,
      Some("00021109"),
      126,
      Nil,
      None,
      new LocalDate(2005, 11, 1)))

  private val employments = List(
    NpsEmployment(
      1,
      NpsDate(new LocalDate(2005, 11, 7)),
      None,
      "126",
      "P32",
      Some("EMPLOYER1"),
      1,
      Some(1),
      Some("00021109"),
      None,
      None,
      Some(false),
      Some(false),
      Some(false),
      Some(false),
      Some(false),
      None
    ))

  private def createSut(
    taiService: TaiService,
    cacheConnector: CacheConnector,
    citizenDetailsConnector: CitizenDetailsConnector,
    nps: NpsConnector,
    des: DesConnector,
    metrics: Metrics,
    hodConfig: NpsConfig,
    mongoConfig: MongoConfig,
    featureTogglesConfig: FeatureTogglesConfig) =
    new TaxAccountService(
      taiService,
      cacheConnector,
      citizenDetailsConnector,
      nps,
      des,
      metrics,
      hodConfig,
      mongoConfig,
      featureTogglesConfig)

  private def reflectedSessionAnswer = new Answer[Future[SessionData]]() {
    override def answer(invocation: InvocationOnMock): Future[SessionData] = {
      val suppliedSession: SessionData = invocation.getArguments()(1).asInstanceOf[SessionData]
      Future.successful(suppliedSession)
    }
  }

  private def reflectedProtectedSessionAnswer = new Answer[Future[Protected[SessionData]]]() {
    override def answer(invocation: InvocationOnMock): Future[Protected[SessionData]] = {
      val suppliedSession: Protected[SessionData] = invocation.getArguments()(1).asInstanceOf[Protected[SessionData]]
      Future.successful(suppliedSession)
    }
  }
}

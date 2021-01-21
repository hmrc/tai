/*
 * Copyright 2021 HM Revenue & Customs
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

import data.NpsData
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.{CyPlusOneConfig, FeatureTogglesConfig, NpsConfig}
import uk.gov.hmrc.tai.connectors.{CitizenDetailsConnector, DesConnector, NpsConnector, RtiConnector}
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.model.nps._
import uk.gov.hmrc.tai.model.nps2.{IabdType, NpsFormatter, TaxAccount}
import uk.gov.hmrc.tai.model.rti.{PayFrequency, RtiData, RtiEmployment, RtiPayment, RtiStatus}
import uk.gov.hmrc.tai.model.tai.{AnnualAccount, TaxYear}
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class TaiServiceSpec extends BaseSpec with NpsFormatter {

  "getTaiRoot" should {
    val version = "101"
    val fakeCidPerson = Person(None, None, None, None, None, None, None, None, Nino(nino.nino), None, None)
    val fakePersonDetails = PersonDetails(version, fakeCidPerson)

    val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
    when(mockCitizenDetailsConnector.getPersonDetails(any())(any(), any()))
      .thenReturn(Future.successful(fakePersonDetails))

    val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
    when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
    when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

    val mockNpsConfig = mock[NpsConfig]
    when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

    val mockCyPlusOneConfig = mock[CyPlusOneConfig]
    when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
    when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

    val sut = createSUT(
      mock[RtiConnector],
      mock[NpsConnector],
      mock[DesConnector],
      mockCitizenDetailsConnector,
      mock[AutoUpdatePayService],
      mock[NextYearComparisonService],
      mock[Auditor],
      mockFeatureTogglesConfig,
      mockNpsConfig,
      mockCyPlusOneConfig
    )

    val result: TaiRoot = Await.result(sut.getTaiRoot(Nino(nino.nino)), timeoutDuration)

    "call citizenDetails service with the nino given" in {
      verify(mockCitizenDetailsConnector, times(1))
        .getPersonDetails(Nino(nino.nino))
    }
    "return correct result" in {
      result mustBe fakePersonDetails.toTaiRoot
    }
  }

  "getAutoUpdateResults" should {
    val mockRtiConnector = mock[RtiConnector]
    when(mockRtiConnector.getRTI(Nino(nino.nino), TaxYear(taxYear).prev))
      .thenReturn(Future.successful((Some(rtiDataPY), rtiStatus)))
    when(mockRtiConnector.getRTI(Nino(nino.nino), TaxYear(taxYear)))
      .thenReturn(Future.successful((Some(rtiDataCY), rtiStatus)))

    val mockNpsConnector = mock[NpsConnector]
    when(mockNpsConnector.getEmployments(Nino(nino.nino), taxYear))
      .thenReturn(Future.successful((npsEmploymentList, nps2EmploymentList, version, Nil)))
    when(mockNpsConnector.getIabdsForType(any(), any(), any())(any())).thenReturn(Future.successful(fakeIabds))

    val mockAutoUpdatePayService = mock[AutoUpdatePayService]
    when(mockAutoUpdatePayService.updateIncomes(any(), any(), any(), any(), any(), any())(any())).thenReturn(rtiCalc)

    val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
    when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
    when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

    val mockNpsConfig = mock[NpsConfig]
    when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

    val mockCyPlusOneConfig = mock[CyPlusOneConfig]
    when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
    when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

    val sut = createSUT(
      mockRtiConnector,
      mockNpsConnector,
      mock[DesConnector],
      mock[CitizenDetailsConnector],
      mockAutoUpdatePayService,
      mock[NextYearComparisonService],
      mock[Auditor],
      mockFeatureTogglesConfig,
      mockNpsConfig,
      mockCyPlusOneConfig
    )

    val result = Await.result(sut.getAutoUpdateResults(Nino(nino.nino), taxYear), timeoutDuration)

    "return correct answer" in {
      val annualAccounts = Seq(
        AnnualAccount(TaxYear().prev, None, Some(rtiDataPY), Some(rtiStatus)),
        AnnualAccount(TaxYear(), None, Some(rtiDataCY), Some(rtiStatus)))

      val expectedResult
        : (List[NpsEmployment], List[RtiCalc], List[nps2.NpsEmployment], List[GateKeeperRule], Seq[AnnualAccount]) =
        (npsEmploymentList, rtiCalc, nps2EmploymentList, Nil, annualAccounts)

      result mustBe expectedResult
    }
    "call getEmployments from nps" in {
      verify(mockNpsConnector, times(1))
        .getEmployments(Nino(nino.nino), taxYear)
    }
    "call getRTI from rti" in {
      verify(mockRtiConnector, times(1))
        .getRTI(Nino(nino.nino), TaxYear(taxYear).prev)
    }
    "call updateIncomes from autoUpdatePayService" in {
      verify(mockAutoUpdatePayService, times(1))
        .updateIncomes(Nino(nino.nino), taxYear, npsEmploymentList, fakeIabds, version, Some(rtiDataCY))
    }
  }

  "isNotCeasedOrCurrentYearCeasedEmployment" should {
    val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
    when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
    when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

    val mockNpsConfig = mock[NpsConfig]
    when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

    val mockCyPlusOneConfig = mock[CyPlusOneConfig]
    when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
    when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

    val sut = createSUT(
      mock[RtiConnector],
      mock[NpsConnector],
      mock[DesConnector],
      mock[CitizenDetailsConnector],
      mock[AutoUpdatePayService],
      mock[NextYearComparisonService],
      mock[Auditor],
      mockFeatureTogglesConfig,
      mockNpsConfig,
      mockCyPlusOneConfig
    )

    "return false" when {
      "employments list is empty" in {
        val result = Await.result(sut.isNotCeasedOrCurrentYearCeasedEmployment(Nil), timeoutDuration)
        result mustBe false
      }

      "there is one employment with end date as start Of Current Tax Year" in {
        val endDate = Some(NpsDate(TaxYear().start.withYear(taxYear)))
        val employmentList = List(npsEmployment.copy(endDate = endDate))

        val result = Await.result(sut.isNotCeasedOrCurrentYearCeasedEmployment(employmentList), timeoutDuration)
        result mustBe false
      }

      "there is one employment with end date as start Of Next Tax Year" in {
        val endDate = Some(NpsDate(TaxYear().start.withYear(taxYear + 1)))
        val employmentList = List(npsEmployment.copy(endDate = endDate))
        val result = Await.result(sut.isNotCeasedOrCurrentYearCeasedEmployment(employmentList), timeoutDuration)
        result mustBe false
      }
    }
    "return true" when {
      "there is one employment without end date" in {
        val employmentList = List(npsEmployment.copy(endDate = None))
        val result = Await.result(sut.isNotCeasedOrCurrentYearCeasedEmployment(employmentList), timeoutDuration)
        result mustBe true
      }
      "there is one employment with end date as one day after start Of Next Tax Year" in {
        val endDate = Some(NpsDate(TaxYear().start.withYear(taxYear).plusDays(1)))
        val employmentList = List(npsEmployment.copy(endDate = endDate))
        val result = Await.result(sut.isNotCeasedOrCurrentYearCeasedEmployment(employmentList), timeoutDuration)
        result mustBe true
      }
      "there is one employment with end date as one day before start Of Next Tax Year" in {
        val endDate = Some(NpsDate(TaxYear().start.withYear(taxYear + 1).minusDays(1)))
        val employmentList = List(npsEmployment.copy(endDate = endDate))
        val result = Await.result(sut.isNotCeasedOrCurrentYearCeasedEmployment(employmentList), timeoutDuration)
        result mustBe true
      }
      "there are multiple employments with one of them without an end date" in {
        val employmentList = List(
          npsEmployment.copy(endDate = None),
          npsEmployment.copy(endDate = Some(NpsDate(new LocalDate(taxYear, 4, 6)))))
        val result = Await.result(sut.isNotCeasedOrCurrentYearCeasedEmployment(employmentList), timeoutDuration)
        result mustBe true
      }
    }
  }

  "fetchIabdsForType" when {
    "isNotCeasedEmployment is false" should {
      "return Nil" in {
        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
        when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

        val mockCyPlusOneConfig = mock[CyPlusOneConfig]
        when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
        when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

        val sut = createSUT(
          mock[RtiConnector],
          mock[NpsConnector],
          mock[DesConnector],
          mock[CitizenDetailsConnector],
          mock[AutoUpdatePayService],
          mock[NextYearComparisonService],
          mock[Auditor],
          mockFeatureTogglesConfig,
          mockNpsConfig,
          mockCyPlusOneConfig
        )

        val result =
          Await.result(sut.fetchIabdsForType(Nino(nino.nino), taxYear, isNotCeasedEmployment = false), timeoutDuration)
        result mustBe Nil
      }
    }
    "isNotCeasedEmployment is true and" when {
      "desCall is true" should {
        "return the correct result from des" in {
          val mockDesConnector = mock[DesConnector]
          when(mockDesConnector.getIabdsForTypeFromDes(any(), any(), any())(any()))
            .thenReturn(Future.successful(fakeIabds))

          val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
          when(mockFeatureTogglesConfig.desEnabled)
            .thenReturn(true)
          when(mockFeatureTogglesConfig.desUpdateEnabled)
            .thenReturn(false)

          val mockNpsConfig = mock[NpsConfig]
          when(mockNpsConfig.autoUpdatePayEnabled)
            .thenReturn(Some(false))

          val mockCyPlusOneConfig = mock[CyPlusOneConfig]
          when(mockCyPlusOneConfig.cyPlusOneEnabled)
            .thenReturn(Some(false))
          when(mockCyPlusOneConfig.cyPlusOneEnableDate)
            .thenReturn(Some("10/10"))

          val sut = createSUT(
            mock[RtiConnector],
            mock[NpsConnector],
            mockDesConnector,
            mock[CitizenDetailsConnector],
            mock[AutoUpdatePayService],
            mock[NextYearComparisonService],
            mock[Auditor],
            mockFeatureTogglesConfig,
            mockNpsConfig,
            mockCyPlusOneConfig
          )

          val result =
            Await.result(sut.fetchIabdsForType(Nino(nino.nino), taxYear, isNotCeasedEmployment = true), timeoutDuration)

          result mustBe fakeIabds
          verify(mockDesConnector, times(1))
            .getIabdsForTypeFromDes(Nino(nino.nino), taxYear, IabdType.NewEstimatedPay.code)
        }
      }
      "desCall is false" should {
        "return the correct result from nps" in {
          val mockNpsConnector = mock[NpsConnector]
          when(mockNpsConnector.getIabdsForType(any(), any(), any())(any()))
            .thenReturn(Future.successful(fakeIabds))

          val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
          when(mockFeatureTogglesConfig.desEnabled)
            .thenReturn(false)
          when(mockFeatureTogglesConfig.desUpdateEnabled)
            .thenReturn(false)

          val mockNpsConfig = mock[NpsConfig]
          when(mockNpsConfig.autoUpdatePayEnabled)
            .thenReturn(Some(false))

          val mockCyPlusOneConfig = mock[CyPlusOneConfig]
          when(mockCyPlusOneConfig.cyPlusOneEnabled)
            .thenReturn(Some(false))
          when(mockCyPlusOneConfig.cyPlusOneEnableDate)
            .thenReturn(Some("10/10"))

          val sut = createSUT(
            mock[RtiConnector],
            mockNpsConnector,
            mock[DesConnector],
            mock[CitizenDetailsConnector],
            mock[AutoUpdatePayService],
            mock[NextYearComparisonService],
            mock[Auditor],
            mockFeatureTogglesConfig,
            mockNpsConfig,
            mockCyPlusOneConfig
          )

          val result =
            Await.result(sut.fetchIabdsForType(Nino(nino.nino), taxYear, isNotCeasedEmployment = true), timeoutDuration)

          result mustBe fakeIabds
          verify(mockNpsConnector, times(1))
            .getIabdsForType(Nino(nino.nino), taxYear, IabdType.NewEstimatedPay.code)
        }
      }
    }
  }

  "fetchRtiCurrent" when {
    "isNotCeasedEmployment is false" should {
      "return empty RtiData with 404 RtiStatus" in {
        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
        when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

        val mockCyPlusOneConfig = mock[CyPlusOneConfig]
        when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
        when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

        val sut = createSUT(
          mock[RtiConnector],
          mock[NpsConnector],
          mock[DesConnector],
          mock[CitizenDetailsConnector],
          mock[AutoUpdatePayService],
          mock[NextYearComparisonService],
          mock[Auditor],
          mockFeatureTogglesConfig,
          mockNpsConfig,
          mockCyPlusOneConfig
        )

        val result =
          Await.result(sut.fetchRtiCurrent(Nino(nino.nino), taxYear, isNotCeasedEmployment = false), timeoutDuration)
        val expectedResult: (Option[RtiData], RtiStatus) = (None, RtiStatus(404, "Employment ceased"))

        result mustBe expectedResult
      }
    }

    "isNotCeasedEmployment is true" should {
      "return the correct result from rti" in {
        val mockRtiConnector = mock[RtiConnector]
        when(mockRtiConnector.getRTI(Nino(nino.nino), TaxYear(taxYear)))
          .thenReturn(Future.successful((Some(rtiDataCY), rtiStatus)))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
        when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

        val mockCyPlusOneConfig = mock[CyPlusOneConfig]
        when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
        when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

        val sut = createSUT(
          mockRtiConnector,
          mock[NpsConnector],
          mock[DesConnector],
          mock[CitizenDetailsConnector],
          mock[AutoUpdatePayService],
          mock[NextYearComparisonService],
          mock[Auditor],
          mockFeatureTogglesConfig,
          mockNpsConfig,
          mockCyPlusOneConfig
        )

        val result =
          Await.result(sut.fetchRtiCurrent(Nino(nino.nino), taxYear, isNotCeasedEmployment = true), timeoutDuration)
        val expectedResult: (Option[RtiData], RtiStatus) = (Some(rtiDataCY), rtiStatus)

        result mustBe expectedResult
        verify(mockRtiConnector, times(1))
          .getRTI(Nino(nino.nino), TaxYear(taxYear))
      }
    }
  }

  "getIabd" should {
    "return the iabd from nps when desCall is false" in {
      val mockNpsConnector = mock[NpsConnector]
      when(mockNpsConnector.getIabds(any(), any())(any())).thenReturn(Future.successful(fakeIabds))

      val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
      when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
      when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

      val mockNpsConfig = mock[NpsConfig]
      when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

      val mockCyPlusOneConfig = mock[CyPlusOneConfig]
      when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
      when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

      val sut = createSUT(
        mock[RtiConnector],
        mockNpsConnector,
        mock[DesConnector],
        mock[CitizenDetailsConnector],
        mock[AutoUpdatePayService],
        mock[NextYearComparisonService],
        mock[Auditor],
        mockFeatureTogglesConfig,
        mockNpsConfig,
        mockCyPlusOneConfig
      )

      val result = Await.result(sut.getIabd(Nino(nino.nino), taxYear), timeoutDuration)

      result mustBe fakeIabds
      verify(mockNpsConnector, times(1))
        .getIabds(Nino(nino.nino), taxYear)
    }
    "return the iabd from des when desCall is true" in {
      val mockDesConnector = mock[DesConnector]
      when(mockDesConnector.getIabdsFromDes(any(), any())(any())).thenReturn(Future.successful(fakeIabds))

      val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
      when(mockFeatureTogglesConfig.desEnabled).thenReturn(true)
      when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

      val mockNpsConfig = mock[NpsConfig]
      when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

      val mockCyPlusOneConfig = mock[CyPlusOneConfig]
      when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
      when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

      val sut = createSUT(
        mock[RtiConnector],
        mock[NpsConnector],
        mockDesConnector,
        mock[CitizenDetailsConnector],
        mock[AutoUpdatePayService],
        mock[NextYearComparisonService],
        mock[Auditor],
        mockFeatureTogglesConfig,
        mockNpsConfig,
        mockCyPlusOneConfig
      )

      val result = Await.result(sut.getIabd(Nino(nino.nino), taxYear), timeoutDuration)

      result mustBe fakeIabds
      verify(mockDesConnector, times(1))
        .getIabdsFromDes(Nino(nino.nino), taxYear)
    }
  }

  "getCalculatedTaxAccountFromConnector" should {
    "return the correct tuple from nps when desCall is false" in {
      val mockNpsConnector = mock[NpsConnector]
      when(mockNpsConnector.getCalculatedTaxAccount(any(), any())(any()))
        .thenReturn(Future.successful((npsTaxAccount, version, Json.toJson(fakeSummary))))

      val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
      when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
      when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

      val mockNpsConfig = mock[NpsConfig]
      when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

      val mockCyPlusOneConfig = mock[CyPlusOneConfig]
      when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
      when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

      val sut = createSUT(
        mock[RtiConnector],
        mockNpsConnector,
        mock[DesConnector],
        mock[CitizenDetailsConnector],
        mock[AutoUpdatePayService],
        mock[NextYearComparisonService],
        mock[Auditor],
        mockFeatureTogglesConfig,
        mockNpsConfig,
        mockCyPlusOneConfig
      )

      val result = Await.result(sut.getCalculatedTaxAccountFromConnector(Nino(nino.nino), taxYear), timeoutDuration)
      val expectedResult: (NpsTaxAccount, Int, JsValue) = (npsTaxAccount, version, Json.toJson(fakeSummary))

      result mustBe expectedResult
      verify(mockNpsConnector, times(1))
        .getCalculatedTaxAccount(Nino(nino.nino), taxYear)
    }
    "return the correct tuple from des when desCall is true" in {
      val mockDesConnector = mock[DesConnector]
      when(mockDesConnector.getCalculatedTaxAccountFromDes(any(), any())(any()))
        .thenReturn(Future.successful((npsTaxAccount, version, Json.toJson(fakeSummary))))

      val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
      when(mockFeatureTogglesConfig.desEnabled).thenReturn(true)
      when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

      val mockNpsConfig = mock[NpsConfig]
      when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

      val mockCyPlusOneConfig = mock[CyPlusOneConfig]
      when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
      when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

      val sut = createSUT(
        mock[RtiConnector],
        mock[NpsConnector],
        mockDesConnector,
        mock[CitizenDetailsConnector],
        mock[AutoUpdatePayService],
        mock[NextYearComparisonService],
        mock[Auditor],
        mockFeatureTogglesConfig,
        mockNpsConfig,
        mockCyPlusOneConfig
      )

      val result = Await.result(sut.getCalculatedTaxAccountFromConnector(Nino(nino.nino), taxYear), timeoutDuration)
      val expectedResult: (NpsTaxAccount, Int, JsValue) = (npsTaxAccount, version, Json.toJson(fakeSummary))

      result mustBe expectedResult
      verify(mockDesConnector, times(1))
        .getCalculatedTaxAccountFromDes(Nino(nino.nino), taxYear)
    }
  }

  "getCalculatedTaxAccount" should {
    "return TaxSummaryDetails without next year account" when {
      "invokeCYPlusOne is false" in {
        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getIabds(any(), any())(any())).thenReturn(Future.successful(fakeIabds))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
        when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

        val mockCyPlusOneConfig = mock[CyPlusOneConfig]
        when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
        when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

        val sut = createSUT(
          mock[RtiConnector],
          mockNpsConnector,
          mock[DesConnector],
          mock[CitizenDetailsConnector],
          mock[AutoUpdatePayService],
          mock[NextYearComparisonService],
          mock[Auditor],
          mockFeatureTogglesConfig,
          mockNpsConfig,
          mockCyPlusOneConfig
        )

        val annualAccounts = Seq(
          AnnualAccount(TaxYear().prev, None, Some(rtiDataPY), Some(rtiStatus)),
          AnnualAccount(TaxYear(taxYear), None, Some(rtiDataCY), Some(rtiStatus)))

        val npsTaxAccountJson = NpsData.getNpsTaxAccountJson

        val result = Await.result(
          sut.getCalculatedTaxAccount(
            Nino(nino.nino),
            taxYear,
            (npsEmploymentList, rtiCalc, nps2EmploymentList, Nil, annualAccounts),
            (npsTaxAccountJson, version)),
          timeoutDuration
        )

        val npsTaxAccount = npsTaxAccountJson.as[NpsTaxAccount]
        val taxAccount = npsTaxAccountJson.as[TaxAccount]
        val cyPyAnnualAccounts = annualAccounts.map(_.copy(nps = Some(taxAccount.withEmployments(nps2EmploymentList))))
        val taxSummary = npsTaxAccount
          .toTaxSummary(
            version,
            npsEmploymentList,
            fakeIabds,
            rtiCalc,
            cyPyAnnualAccounts.filter(_.year == TaxYear()).toList)
          .copy(accounts = cyPyAnnualAccounts)

        result mustBe taxSummary
      }
    }

    "return TaxSummaryDetails with next year account" when {
      "invokeCYPlusOne is true" in {
        val mockRtiConnector = mock[RtiConnector]

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getIabds(any(), any())(any())).thenReturn(Future.successful(fakeIabds))
        when(mockNpsConnector.getCalculatedTaxAccount(any(), any())(any()))
          .thenReturn(Future.successful((NpsTaxAccount(Some(""), Some(1)), 0, Json.toJson(fakeSummary))))

        val mockDesConnector = mock[DesConnector]
        val mockAutoUpdatePayService = mock[AutoUpdatePayService]
        val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
        val mockNextYearComparisonService = mock[NextYearComparisonService]
        val mockAuditor = mock[Auditor]

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
        when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

        val mockCyPlusOneConfig = mock[CyPlusOneConfig]
        when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(true))
        when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(None)

        val sut = createSUT(
          mockRtiConnector,
          mockNpsConnector,
          mockDesConnector,
          mockCitizenDetailsConnector,
          mockAutoUpdatePayService,
          mockNextYearComparisonService,
          mockAuditor,
          mockFeatureTogglesConfig,
          mockNpsConfig,
          mockCyPlusOneConfig
        )

        val annualAccounts = Seq(
          AnnualAccount(TaxYear().prev, None, Some(rtiDataPY), Some(rtiStatus)),
          AnnualAccount(TaxYear(taxYear), None, Some(rtiDataCY), Some(rtiStatus)))

        val npsTaxAccountJson = NpsData.getNpsTaxAccountJson

        val result = Await.result(
          sut.getCalculatedTaxAccount(
            Nino(nino.nino),
            taxYear,
            (npsEmploymentList, rtiCalc, nps2EmploymentList, Nil, annualAccounts),
            (npsTaxAccountJson, version)),
          timeoutDuration
        )

        verify(mockNpsConnector, times(1))
          .getCalculatedTaxAccount(Nino(nino.nino), taxYear + 1)

        val npsTaxAccount = npsTaxAccountJson.as[NpsTaxAccount]
        val taxAccount = npsTaxAccountJson.as[TaxAccount]
        val cyPyAnnualAccounts = annualAccounts.map(_.copy(nps = Some(taxAccount.withEmployments(nps2EmploymentList))))
        val taxSummary = npsTaxAccount
          .toTaxSummary(
            version,
            npsEmploymentList,
            fakeIabds,
            rtiCalc,
            cyPyAnnualAccounts.filter(_.year == TaxYear()).toList)
          .copy(accounts = cyPyAnnualAccounts)

        result mustBe taxSummary
      }
    }
  }

  "appendCYPlusOneToTaxSummary" should {
    "return correct TaxSummaryDetails" in {
      val taxSummaryDetails = TaxSummaryDetails(nino.nino, version)

      val mockNpsConnector = mock[NpsConnector]
      when(mockNpsConnector.getCalculatedTaxAccount(any(), any())(any()))
        .thenReturn(Future.successful((npsTaxAccount, version, Json.toJson(fakeSummary))))

      val mockNextYearComparisonService = mock[NextYearComparisonService]
      when(mockNextYearComparisonService.stripCeasedFromNps(any())).thenReturn(npsTaxAccount)
      when(mockNextYearComparisonService.proccessTaxSummaryWithCYPlusOne(any(), any())).thenReturn(taxSummaryDetails)

      val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
      when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
      when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

      val mockNpsConfig = mock[NpsConfig]
      when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

      val mockCyPlusOneConfig = mock[CyPlusOneConfig]
      when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
      when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

      val sut = createSUT(
        mock[RtiConnector],
        mockNpsConnector,
        mock[DesConnector],
        mock[CitizenDetailsConnector],
        mock[AutoUpdatePayService],
        mockNextYearComparisonService,
        mock[Auditor],
        mockFeatureTogglesConfig,
        mockNpsConfig,
        mockCyPlusOneConfig
      )

      val result = Await.result(
        sut.appendCYPlusOneToTaxSummary(Nino(nino.nino), taxYear, Nil, version, List(npsEmployment), taxSummaryDetails),
        timeoutDuration)
      val expectedAccounts =
        List(AnnualAccount(TaxYear(taxYear + 1), Some(TaxAccount(None, None, 0, Map.empty, Nil, Nil)), None, None))

      val expectedResult = TaxSummaryDetails(nino = nino.nino, version = version, accounts = expectedAccounts)

      result mustBe expectedResult
    }
  }

  "invokeCYPlusOne" should {
    val mockRtiConnector = mock[RtiConnector]
    val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
    when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
    when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

    val mockNpsConfig = mock[NpsConfig]
    when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

    val mockCyPlusOneConfig = mock[CyPlusOneConfig]
    when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
    when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

    val sut = createSUT(
      mockRtiConnector,
      mock[NpsConnector],
      mock[DesConnector],
      mock[CitizenDetailsConnector],
      mock[AutoUpdatePayService],
      mock[NextYearComparisonService],
      mock[Auditor],
      mockFeatureTogglesConfig,
      mockNpsConfig,
      mockCyPlusOneConfig
    )

    "when cyPlusOneEnabled is true" when {
      "return false when cyPlusOneEnableDate is defined, and current date is before this date" in {
        when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(true))

        val result: Boolean = sut.invokeCYPlusOne(new LocalDate("2018-10-09"))
        result mustBe false
      }

      "return true when cyPlusOneEnableDate is not defined" in {
        when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(None)
        when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(true))

        val result: Boolean = sut.invokeCYPlusOne(new LocalDate("2018-10-11"))
        result mustBe true
      }
    }

    "when cyPlusOneEnabled is false" when {
      "return false" in {
        when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))

        val result: Boolean = sut.invokeCYPlusOne(new LocalDate("2018-10-11"))
        result mustBe false
      }
    }

    "when cyPlusOneEnabled is None" when {
      "return false" in {
        when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(None)

        val result: Boolean = sut.invokeCYPlusOne(new LocalDate("2018-10-11"))
        result mustBe false
      }
    }
  }

  "updateEmployments" should {

    val requestVersion = 3
    "invoke the correct HOD update method, and return an appropriate response" when {

      "EmploymentAmounts are present which show different values old to new, and desUpdateCall is true" in {
        val version = "123"
        val fakeCidPerson = Person(None, None, None, None, None, None, None, None, Nino(nino.nino), None, None)
        val fakePersonDetails = PersonDetails(version, fakeCidPerson)
        val employmentAmounts = List(EmploymentAmount("", "", 1, 20, 10), EmploymentAmount("", "", 2, 30, 40))

        val editEmployments = IabdUpdateEmploymentsRequest(requestVersion, employmentAmounts)

        val mockDesConnector = mock[DesConnector]
        when(mockDesConnector.updateEmploymentDataToDes(any(), any(), any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(200)))

        val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
        when(mockCitizenDetailsConnector.getPersonDetails(any())(any(), any()))
          .thenReturn(Future.successful(fakePersonDetails))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
        when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(true)

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

        val mockCyPlusOneConfig = mock[CyPlusOneConfig]
        when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
        when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

        val sut = createSUT(
          mock[RtiConnector],
          mock[NpsConnector],
          mockDesConnector,
          mockCitizenDetailsConnector,
          mock[AutoUpdatePayService],
          mock[NextYearComparisonService],
          mock[Auditor],
          mockFeatureTogglesConfig,
          mockNpsConfig,
          mockCyPlusOneConfig
        )

        val sessionIdValue = "the update session id"
        val hcWithSessionID: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(sessionIdValue)))

        val result: IabdUpdateEmploymentsResponse =
          Await.result(sut.updateEmployments(Nino(nino.nino), taxYear, 3, editEmployments)(hcWithSessionID), 5 seconds)

        result.transaction mustBe TransactionId(sessionIdValue)
        result.version mustBe version.toInt + 2
        result.iabdType mustBe 3
        result.newAmounts mustBe employmentAmounts

        verify(mockDesConnector, times(2))
          .updateEmploymentDataToDes(any(), any(), any(), any(), any(), any())(any())
      }

      "EmploymentAmounts are present which show different values old to new, and desUpdateCall is false" in {
        val version = "123"
        val fakeCidPerson = Person(None, None, None, None, None, None, None, None, Nino(nino.nino), None, None)
        val fakePersonDetails = PersonDetails(version, fakeCidPerson)
        val empAmt1 = EmploymentAmount("", "", 1, 20, 10)
        val empAmt2 = EmploymentAmount("", "", 2, 30, 40)
        val employmentAmounts = List(empAmt1, empAmt2)
        val editEmployments = IabdUpdateEmploymentsRequest(requestVersion, employmentAmounts)

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.updateEmploymentData(any(), any(), any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(200)))

        val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
        when(mockCitizenDetailsConnector.getPersonDetails(any())(any(), any()))
          .thenReturn(Future.successful(fakePersonDetails))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
        when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

        val mockCyPlusOneConfig = mock[CyPlusOneConfig]
        when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
        when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

        val sut = createSUT(
          mock[RtiConnector],
          mockNpsConnector,
          mock[DesConnector],
          mockCitizenDetailsConnector,
          mock[AutoUpdatePayService],
          mock[NextYearComparisonService],
          mock[Auditor],
          mockFeatureTogglesConfig,
          mockNpsConfig,
          mockCyPlusOneConfig
        )

        val sessionIdValue = "the update session id"
        val hcWithSessionID: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(sessionIdValue)))

        val result: IabdUpdateEmploymentsResponse =
          Await.result(sut.updateEmployments(Nino(nino.nino), taxYear, 3, editEmployments)(hcWithSessionID), 5 seconds)

        result.transaction mustBe TransactionId(sessionIdValue)
        result.version mustBe version.toInt + 2
        result.iabdType mustBe 3
        result.newAmounts mustBe employmentAmounts

        verify(mockNpsConnector, times(2))
          .updateEmploymentData(any(), any(), any(), any(), any(), any())(any())
      }
    }
    "bypass any HOD update call, and return an appropriate response" when {
      "No EmploymentAmounts are present which show different values old to new" in {
        val employmentAmounts = List(EmploymentAmount("", "", 1, 20, 20), EmploymentAmount("", "", 2, 30, 30))
        val editEmployments = IabdUpdateEmploymentsRequest(requestVersion, employmentAmounts)

        val mockNpsConnector = mock[NpsConnector]

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
        when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

        val mockCyPlusOneConfig = mock[CyPlusOneConfig]
        when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
        when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

        val sut = createSUT(
          mock[RtiConnector],
          mockNpsConnector,
          mock[DesConnector],
          mock[CitizenDetailsConnector],
          mock[AutoUpdatePayService],
          mock[NextYearComparisonService],
          mock[Auditor],
          mockFeatureTogglesConfig,
          mockNpsConfig,
          mockCyPlusOneConfig
        )

        val sessionIdValue = "the update session id"
        val hcWithSessionID: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(sessionIdValue)))

        val result: IabdUpdateEmploymentsResponse =
          Await.result(sut.updateEmployments(Nino(nino.nino), taxYear, 3, editEmployments)(hcWithSessionID), 5 seconds)

        result.transaction mustBe TransactionId(sessionIdValue)
        result.version mustBe requestVersion
        result.iabdType mustBe 3
        result.newAmounts mustBe Nil

        verify(mockNpsConnector, times(0))
          .updateEmploymentData(any(), any(), any(), any(), any(), any())(any())
      }
    }
  }

  "getIadbUpdateAmount" should {
    "return IabdUpdateAmount with Customer Entered source value" when {
      "npsUpdateSourceEnabled is true" in {
        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
        when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(true))

        val mockCyPlusOneConfig = mock[CyPlusOneConfig]
        when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
        when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

        val sut = createSUT(
          mock[RtiConnector],
          mock[NpsConnector],
          mock[DesConnector],
          mock[CitizenDetailsConnector],
          mock[AutoUpdatePayService],
          mock[NextYearComparisonService],
          mock[Auditor],
          mockFeatureTogglesConfig,
          mockNpsConfig,
          mockCyPlusOneConfig
        )

        val result = sut.getIadbUpdateAmount(employmentAmount)

        result mustBe IabdUpdateAmount(
          employmentSequenceNumber = employmentAmount.employmentId,
          grossAmount = employmentAmount.newAmount,
          source = Some(0))
      }
    }
    "return IabdUpdateAmount without Customer Entered source value" when {
      "npsUpdateSourceEnabled is false" in {
        val mockRtiConnector = mock[RtiConnector]

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
        when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

        val mockCyPlusOneConfig = mock[CyPlusOneConfig]
        when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
        when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

        val sut = createSUT(
          mockRtiConnector,
          mock[NpsConnector],
          mock[DesConnector],
          mock[CitizenDetailsConnector],
          mock[AutoUpdatePayService],
          mock[NextYearComparisonService],
          mock[Auditor],
          mockFeatureTogglesConfig,
          mockNpsConfig,
          mockCyPlusOneConfig
        )

        val result = sut.getIadbUpdateAmount(employmentAmount)

        result mustBe IabdUpdateAmount(
          employmentSequenceNumber = employmentAmount.employmentId,
          grossAmount = employmentAmount.newAmount)

      }
      "npsUpdateSourceEnabled is None" in {
        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
        when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(None)

        val mockCyPlusOneConfig = mock[CyPlusOneConfig]
        when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
        when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

        val sut = createSUT(
          mock[RtiConnector],
          mock[NpsConnector],
          mock[DesConnector],
          mock[CitizenDetailsConnector],
          mock[AutoUpdatePayService],
          mock[NextYearComparisonService],
          mock[Auditor],
          mockFeatureTogglesConfig,
          mockNpsConfig,
          mockCyPlusOneConfig
        )

        val result = sut.getIadbUpdateAmount(employmentAmount)
        result mustBe IabdUpdateAmount(
          employmentSequenceNumber = employmentAmount.employmentId,
          grossAmount = employmentAmount.newAmount)
      }
    }

    "Set the update amount source to be internet calculated" when {
      "the des update flag is set to true" in {
        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
        when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(true)

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(true))

        val mockCyPlusOneConfig = mock[CyPlusOneConfig]
        when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
        when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

        val sut = createSUT(
          mock[RtiConnector],
          mock[NpsConnector],
          mock[DesConnector],
          mock[CitizenDetailsConnector],
          mock[AutoUpdatePayService],
          mock[NextYearComparisonService],
          mock[Auditor],
          mockFeatureTogglesConfig,
          mockNpsConfig,
          mockCyPlusOneConfig
        )

        val result = sut.getIadbUpdateAmount(employmentAmount)

        result mustBe IabdUpdateAmount(
          employmentSequenceNumber = employmentAmount.employmentId,
          grossAmount = employmentAmount.newAmount,
          source = Some(39))
      }
      "the des update flag is set to false" in {
        val mockRtiConnector = mock[RtiConnector]

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
        when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(true))

        val mockCyPlusOneConfig = mock[CyPlusOneConfig]
        when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
        when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

        val sut = createSUT(
          mockRtiConnector,
          mock[NpsConnector],
          mock[DesConnector],
          mock[CitizenDetailsConnector],
          mock[AutoUpdatePayService],
          mock[NextYearComparisonService],
          mock[Auditor],
          mockFeatureTogglesConfig,
          mockNpsConfig,
          mockCyPlusOneConfig
        )

        val result = sut.getIadbUpdateAmount(employmentAmount)
        result mustBe IabdUpdateAmount(
          employmentSequenceNumber = employmentAmount.employmentId,
          grossAmount = employmentAmount.newAmount,
          source = Some(0))
      }
    }
  }

  "sessionOrUUID" should {
    "return the sessionId from a HeaderCarrier" when {
      "HeaderCarrier has an assigned sessionId" in {
        val testSessionId = "testSessionId"
        val testHeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(testSessionId)))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
        when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

        val mockCyPlusOneConfig = mock[CyPlusOneConfig]
        when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
        when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

        val sut = createSUT(
          mock[RtiConnector],
          mock[NpsConnector],
          mock[DesConnector],
          mock[CitizenDetailsConnector],
          mock[AutoUpdatePayService],
          mock[NextYearComparisonService],
          mock[Auditor],
          mockFeatureTogglesConfig,
          mockNpsConfig,
          mockCyPlusOneConfig
        )

        val result = sut.sessionOrUUID(testHeaderCarrier)
        result mustBe testSessionId
      }
    }

    "returns a randomly generated sessionId" when {
      "HeaderCarrier has None assigned as a sessionId" in {
        val testHeaderCarrier = HeaderCarrier(sessionId = None)

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled).thenReturn(false)
        when(mockFeatureTogglesConfig.desUpdateEnabled).thenReturn(false)

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(false))

        val mockCyPlusOneConfig = mock[CyPlusOneConfig]
        when(mockCyPlusOneConfig.cyPlusOneEnabled).thenReturn(Some(false))
        when(mockCyPlusOneConfig.cyPlusOneEnableDate).thenReturn(Some("10/10"))

        val sut = createSUT(
          mock[RtiConnector],
          mock[NpsConnector],
          mock[DesConnector],
          mock[CitizenDetailsConnector],
          mock[AutoUpdatePayService],
          mock[NextYearComparisonService],
          mock[Auditor],
          mockFeatureTogglesConfig,
          mockNpsConfig,
          mockCyPlusOneConfig
        )

        val result = sut.sessionOrUUID(testHeaderCarrier)
        result must not be empty
        result must not contain "-"
      }
    }
  }

  private def createSUT(
    rti: RtiConnector,
    nps: NpsConnector,
    des: DesConnector,
    cid: CitizenDetailsConnector,
    autoUpdatePayService: AutoUpdatePayService,
    nextYearComparisonService: NextYearComparisonService,
    Auditor: Auditor,
    featureTogglesConfig: FeatureTogglesConfig,
    npsConfig: NpsConfig,
    cyPlusOneConfig: CyPlusOneConfig) =
    new TaiService(
      rti,
      nps,
      des,
      cid,
      autoUpdatePayService,
      nextYearComparisonService,
      Auditor,
      featureTogglesConfig,
      npsConfig,
      cyPlusOneConfig)

  private val employmentAmount = EmploymentAmount("", "", 1, 20, 20)
  private lazy val timeoutDuration: Duration = 5 seconds

  private lazy val taxYear = TaxYear().year
  private lazy val employmentName = Some("employmentName1")
  private lazy val worksNumber = Some("00000")
  private lazy val rtiDataCY = RtiData(
    nino.nino,
    TaxYear(2016),
    "M1464687373867",
    List(
      RtiEmployment(
        "111",
        "A00",
        "00000",
        List(RtiPayment(
          PayFrequency.Weekly,
          new LocalDate(2005, 11, 7),
          new LocalDate(2005, 11, 7),
          1000,
          1000,
          200,
          200,
          worksNumber,
          isOccupationalPension = false,
          None,
          Some(2),
          None,
          Some(0),
          Some(0)
        )),
        Nil,
        Some("00000"),
        16
      ))
  )

  private lazy val rtiDataPY = rtiDataCY.copy(taxYear = TaxYear(2015))
  private lazy val rtiStatus = RtiStatus(200, "")
  private lazy val rtiCalc = List(
    RtiCalc(1, Some(new LocalDate(2005, 11, 7)), Some(PayFrequency.Weekly), 1, 1, "EMPLOYER1", 11950, Some(31070.0)))

  private lazy val version = 0
  private lazy val npsTaxAccount = NpsTaxAccount(Some(""), Some(1))
  private lazy val nps2EmploymentList = List(
    nps2.NpsEmployment(employmentName, isPrimary = true, 1, worksNumber, 126, Nil, None, new LocalDate(2005, 11, 1)))
  private lazy val npsEmployment = NpsEmployment(
    1,
    NpsDate(new LocalDate(2005, 11, 7)),
    None,
    "111",
    "A00",
    employmentName,
    1,
    Some(1),
    worksNumber,
    None,
    None,
    Some(false),
    Some(false),
    Some(false),
    Some(false),
    Some(false),
    None
  )

  private lazy val npsEmploymentList = List(npsEmployment)
  private lazy val iabd: NpsIabdRoot = NpsIabdRoot("", Some(0), 0, Some(BigDecimal(0)))
  private lazy val fakeIabds = List(iabd)
  private lazy val totalLiability: NpsTotalLiability = NpsTotalLiability(totalLiability = Some(BigDecimal(11111.12)))
  private lazy val fakeSummary =
    NpsTaxAccount(nino = Some(nino.nino), taxYear = Some(taxYear), totalLiability = Some(totalLiability))
}

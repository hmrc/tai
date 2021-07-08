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

import org.joda.time.{Days, LocalDate}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.{never, times, verify, when}
import play.api.http.Status.OK
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.tai.config.{FeatureTogglesConfig, NpsConfig}
import uk.gov.hmrc.tai.connectors.{DesConnector, NpsConnector}
import uk.gov.hmrc.tai.model.{IabdUpdateAmount, RtiCalc}
import uk.gov.hmrc.tai.model.enums.APITypes.APITypes
import uk.gov.hmrc.tai.model.helpers.IncomeHelper
import uk.gov.hmrc.tai.model.nps.{NpsDate, NpsEmployment, NpsIabdRoot}
import uk.gov.hmrc.tai.model.nps2.Income.{Ceased, Live}
import uk.gov.hmrc.tai.model.nps2.{IabdType, Income}
import uk.gov.hmrc.tai.model.rti.{PayFrequency, RtiData, RtiEmployment, RtiPayment}
import uk.gov.hmrc.tai.model.tai.{NINE_MONTHS, SIX_MONTHS, THREE_MONTHS, TaxYear}
import uk.gov.hmrc.tai.util.{BaseSpec, TaiConstants}

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class AutoUpdatePayServiceSpec extends BaseSpec {

  "updateIncomes" must {
    "not return any updated incomes" when {
      "the auto update flag is set to false" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        sut.updateIncomes(nino, CurrentYear, Nil, Nil, 23, None)(HeaderCarrier()) mustBe Nil
      }

      "the auto update flag is set to true and no RTI data is provided" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        sut.updateIncomes(nino, CurrentYear, Nil, Nil, 23, None)(HeaderCarrier()) mustBe Nil
      }

      "the auto update flag is set to true, but an exception is thrown" in {
        val exception = new IllegalArgumentException("failed")

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        when(mockIncomeHelper.isEditableByAutoUpdateService(any(), any()))
          .thenReturn(true)

        when(mockDesConnector.updateEmploymentDataToDes(any(), anyInt, anyInt, anyInt, any(), any())(any()))
          .thenThrow(exception)

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        sut.updateIncomes(nino, CurrentYear, List(npsEmployment), List(iabdRoot), 1, Some(rtiData))(HeaderCarrier()) mustBe Nil
      }
    }

    "return a list of RtiCalc" when {
      "the auto update flag is set to true" in {
        val employments =
          List(NpsEmployment(1, npsDateStartOfYear, None, "", "123", None, 1, Some(Live.code), Some("1000")))
        val rtiData =
          Some(RtiData("", TaxYear(CurrentYear), "", List(RtiEmployment("", "123", "", Nil, Nil, Some("1000"), 1))))
        val expectedResult = List(RtiCalc(1, None, None, 1, 1, "", BigDecimal(0), None))

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)

        val mockIncomeHelper = mock[IncomeHelper]
        when(mockIncomeHelper.isEditableByAutoUpdateService(any(), any()))
          .thenReturn(true)

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        sut.updateIncomes(nino, CurrentYear, employments, Nil, 23, rtiData)(HeaderCarrier()) mustBe expectedResult
      }
    }
  }
  "updateCeasedAndRtiIncomes" must {
    "return a successful list of RTI calculations" in {
      val mockNpsConfig = mock[NpsConfig]
      when(mockNpsConfig.autoUpdatePayEnabled)
        .thenReturn(Some(true))
      when(mockNpsConfig.updateSourceEnabled)
        .thenReturn(Some(false))
      when(mockNpsConfig.postCalcEnabled)
        .thenReturn(Some(true))

      val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
      when(mockFeatureTogglesConfig.desEnabled)
        .thenReturn(true)
      when(mockFeatureTogglesConfig.desUpdateEnabled)
        .thenReturn(true)

      val mockIncomeHelper = mock[IncomeHelper]
      when(mockIncomeHelper.isEditableByAutoUpdateService(any(), any()))
        .thenReturn(true)

      val mockDesConnector = mock[DesConnector]
      when(mockDesConnector.updateEmploymentDataToDes(any(), anyInt, anyInt, anyInt, any(), any())(any()))
        .thenReturn(Future.successful(HttpResponse(OK, "")))

      val sut =
        createSUT(mock[NpsConnector], mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

      val result =
        sut.updateCeasedAndRtiIncomes(nino, CurrentYear, List(npsEmployment), List(iabdRoot), 1, Some(rtiData))(
          HeaderCarrier())

      result mustBe Success(Nil)
    }

    "handle an exception" when {
      "the des call fails" in {
        val exception = new IllegalArgumentException("failed")

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        when(mockFeatureTogglesConfig.desUpdateEnabled)
          .thenReturn(true)

        val mockIncomeHelper = mock[IncomeHelper]
        when(mockIncomeHelper.isEditableByAutoUpdateService(any(), any()))
          .thenReturn(true)

        val mockDesConnector = mock[DesConnector]
        when(mockDesConnector.updateEmploymentDataToDes(any(), anyInt, anyInt, anyInt, any(), any())(any()))
          .thenThrow(exception)

        val sut =
          createSUT(mock[NpsConnector], mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        sut.updateCeasedAndRtiIncomes(nino, CurrentYear, List(npsEmployment), List(iabdRoot), 1, Some(rtiData))(
          HeaderCarrier()) mustBe Failure(exception)

      }
    }
  }

  "getCeasedIncomeFinalSalaries" must {

    "Get the ceased income final salaries" when {
      "the ceased employment has a cessation pay greater than zero and it ceased within the current year" in {
        val employments = List(
          NpsEmployment(
            1,
            npsDateStartOfYear,
            Some(npsDateCurrentTaxYear),
            "",
            "123",
            None,
            1,
            Some(Ceased.code),
            Some("1000"),
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            Some(100)))

        val mockNpsConnector = mock[NpsConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        when(mockFeatureTogglesConfig.desUpdateEnabled)
          .thenReturn(true)

        val mockDesConnector = mock[DesConnector]
        when(mockDesConnector.updateEmploymentDataToDes(any(), anyInt, anyInt, anyInt, any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, "")))

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        sut.getCeasedIncomeFinalSalaries(employments, List(iabdRoot)) mustBe List(
          IabdUpdateAmount(1, 100, None, None, Some(46)))
      }
    }
    "Set the update amount source to be internet calculated" when {
      "the des update flag is set to true" in {
        val employments = List(
          NpsEmployment(
            1,
            npsDateStartOfYear,
            Some(npsDateCurrentTaxYear),
            "",
            "123",
            None,
            1,
            Some(Ceased.code),
            Some("1000"),
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            Some(100)))

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        when(mockFeatureTogglesConfig.desUpdateEnabled)
          .thenReturn(true)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        sut.getCeasedIncomeFinalSalaries(employments, List(iabdRoot)) mustBe List(
          IabdUpdateAmount(1, 100, None, None, Some(46)))
      }
      "the des update flag is set to false" in {
        val employments = List(
          NpsEmployment(
            1,
            npsDateStartOfYear,
            Some(npsDateCurrentTaxYear),
            "",
            "123",
            None,
            1,
            Some(Ceased.code),
            Some("1000"),
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            Some(100)))

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        when(mockFeatureTogglesConfig.desUpdateEnabled)
          .thenReturn(false)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        sut.getCeasedIncomeFinalSalaries(employments, List(iabdRoot)) mustBe List(
          IabdUpdateAmount(1, 100, None, None, Some(1)))
      }
    }

    "Not get the ceased income final salaries" when {
      "the employment status is live" in {
        val employments = List(
          NpsEmployment(
            1,
            npsDateStartOfYear,
            Some(npsDateCurrentTaxYear),
            "",
            "123",
            None,
            1,
            Some(Live.code),
            Some("1000"),
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            Some(100)))

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        sut.getCeasedIncomeFinalSalaries(employments, List(iabdRoot)) mustBe List()
      }

      "the cessation pay this employment is zero" in {
        val employments = List(
          NpsEmployment(
            1,
            npsDateStartOfYear,
            Some(npsDateCurrentTaxYear),
            "",
            "123",
            None,
            1,
            Some(Ceased.code),
            Some("1000"),
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            Some(0)))

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        sut.getCeasedIncomeFinalSalaries(employments, List(iabdRoot)) mustBe List()
      }

      "the end date is not the current tax year" in {
        val currentYear = new LocalDate(2015, 1, 1)
        val employments = List(
          NpsEmployment(
            1,
            NpsDate(currentYear),
            Some(NpsDate(new LocalDate(2016, 4, 12))),
            "",
            "123",
            None,
            1,
            Some(Ceased.code),
            Some("1000"),
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            Some(100)
          ))

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        sut.getCeasedIncomeFinalSalaries(employments, List(iabdRoot)) mustBe List()
      }

      "the gross amount and the estimated pay are the same" in {
        val employments = List(
          NpsEmployment(
            1,
            npsDateStartOfYear,
            Some(npsDateCurrentTaxYear),
            "",
            "123",
            None,
            1,
            Some(Ceased.code),
            Some("1000"),
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            Some(1000)))

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        sut.getCeasedIncomeFinalSalaries(employments, List(iabdRoot)) mustBe List()
      }
    }
  }

  "ceaseEmploymentAmountDifferent" must {

    "compare ceased employments" when {
      "the sequence numbers don't match and the amounts are different" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val iabdRoots = List(NpsIabdRoot(nino.value, Some(2), 23, Some(1000)))

        sut.ceaseEmploymentAmountDifferent(npsEmploymentWithCessationPay, iabdRoots) mustBe false
      }

      "the sequence numbers match and the amounts are the same" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        sut.ceaseEmploymentAmountDifferent(npsEmploymentWithCessationPay, List(iabdRoot)) mustBe false
      }

      "the amounts are different and sequence numbers match" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val employment = NpsEmployment(
          1,
          npsDateStartOfYear,
          Some(npsDateCurrentTaxYear),
          "",
          "123",
          None,
          1,
          Some(Ceased.code),
          Some("1000"),
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          Some(1))

        sut.ceaseEmploymentAmountDifferent(employment, List(iabdRoot)) mustBe true
      }
      "there is no cessation pay this employment for the given employment" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val employment = NpsEmployment(
          1,
          npsDateStartOfYear,
          Some(npsDateCurrentTaxYear),
          "",
          "123",
          None,
          1,
          Some(Ceased.code),
          Some("1000"),
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None)

        sut.ceaseEmploymentAmountDifferent(employment, List(iabdRoot)) mustBe false
      }
    }
  }
  "getRtiUpdateAmounts" must {
    "return RTI updates for the current year" when {
      "employments and RTIData match" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        when(mockFeatureTogglesConfig.desUpdateEnabled)
          .thenReturn(true)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val employments = NpsEmployment(1, npsDateStartOfYear, None, "", "123", None, 1, Some(Live.code), Some("1000"))

        val rtiData = RtiData("", TaxYear(CurrentYear), "", List(rtiEmp))

        sut.getRtiUpdateAmounts(nino, CurrentYear, List(employments), Some(rtiData))(HeaderCarrier()) mustBe (
          (
            List(IabdUpdateAmount(1, 104000, None, None, Some(46))),
            List(IabdUpdateAmount(1, 104000, None, None, Some(46))),
            List(
              RtiCalc(
                1,
                Some(new LocalDate(CurrentYear, 6, 20)),
                Some(PayFrequency.FourWeekly),
                1,
                1,
                "",
                20000,
                Some(104000)))
          ))
      }
    }

    "not return any RTI update amounts for the current year or the next year" when {
      "no employments are given" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val rtiData =
          RtiData("", TaxYear(CurrentYear), "", List(RtiEmployment("", "123", "", Nil, Nil, Some("1000"), 1)))

        sut.getRtiUpdateAmounts(nino, CurrentYear, Nil, Some(rtiData))(HeaderCarrier()) mustBe ((Nil, Nil, Nil))
      }

      "no rti data is given" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        sut.getRtiUpdateAmounts(nino, CurrentYear, List(npsEmploymentWithCessationPay), None)(HeaderCarrier()) mustBe (
          (
            Nil,
            Nil,
            Nil))
      }

      "the employments do not match the RTI data" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val rtiData =
          RtiData("", TaxYear(CurrentYear), "", List(RtiEmployment("", "123", "", Nil, Nil, Some("1000"), 1)))

        val employment = NpsEmployment(
          1,
          npsDateStartOfYear,
          Some(npsDateCurrentTaxYear),
          "",
          "3221",
          None,
          1,
          Some(Ceased.code),
          Some("1000"),
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          Some(1000))

        sut.getRtiUpdateAmounts(nino, CurrentYear, List(employment), Some(rtiData))(HeaderCarrier()) mustBe (
          (
            Nil,
            Nil,
            Nil))
      }
    }

    "return update amounts for the current year and / or the next year" when {
      "updates are enabled and estimated pay values are provided" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        when(mockFeatureTogglesConfig.desUpdateEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        val nyExpected = npsUpdateAmount.copy(grossAmount = 2000)

        sut.getUpdateAmounts(1, Some(1000), Some(2000)) mustBe ((Some(npsUpdateAmount), Some(nyExpected)))
      }
      "updates are disabled and estimated pay values are provided" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        val cyExpected = npsUpdateAmount.copy(source = None)
        val nyExpected = cyExpected.copy(grossAmount = 2000)

        sut.getUpdateAmounts(1, Some(1000), Some(2000)) mustBe ((Some(cyExpected), Some(nyExpected)))
      }
    }
    "not return any update amount for the next year" when {
      "no estimated value for the next year is provided" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        val cyExpected = IabdUpdateAmount(
          employmentSequenceNumber = 1,
          grossAmount = 1000,
          source = None
        )

        sut.getUpdateAmounts(1, Some(1000), None) mustBe ((Some(cyExpected), None))
      }
    }
    "not return any update amount for the current year or the next year" when {
      "no estimated value for the current year is provided" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        sut.getUpdateAmounts(1, None, None) mustBe ((None, None))
        sut.getUpdateAmounts(1, None, Some(1000)) mustBe ((None, None))
      }
    }
  }
  "estimatedPay" must {
    "return the current and next years estimated pay" when {
      "when it is a mid year estimated pay period" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(false))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.FourWeekly,
          new LocalDate(CurrentYear, 4, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(20000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10)
        )

        val rtiEmp = RtiEmployment("", "123", "", List(payment), Nil, Some("1000"), 1)

        val npsEmployment = NpsEmployment(
          1,
          NpsDate(new LocalDate(CurrentYear, 4, 23)),
          Some(NpsDate(new LocalDate(CurrentYear, 4, 30))),
          "",
          "123",
          None,
          1,
          Some(Ceased.code),
          Some("1000"),
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          Some(1000)
        )

        sut.estimatedPay(rtiEmp: RtiEmployment, npsEmployment: NpsEmployment) mustBe ((Some(20000), Some(20000)))
      }
      "when it is a continuous estimated pay period " in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        val payment = RtiPayment(
          PayFrequency.FourWeekly,
          new LocalDate(CurrentYear, 4, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(20000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10)
        )

        val rtiEmp = RtiEmployment("", "123", "", List(payment), Nil, Some("1000"), 1)

        val npsEmployment = NpsEmployment(
          1,
          NpsDate(new LocalDate(CurrentYear, 4, 23)),
          Some(NpsDate(new LocalDate(CurrentYear, 3, 30))),
          "",
          "123",
          None,
          1,
          Some(Ceased.code),
          Some("1000"),
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          Some(1000)
        )

        sut.estimatedPay(rtiEmp: RtiEmployment, npsEmployment: NpsEmployment) mustBe ((Some(20000), Some(20000)))
      }
    }
  }

  "getMidYearEstimatedPay" must {
    "return the mid year estimated pay values for the current year and next year" when {
      "the frequency is weekly" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled).thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled).thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled).thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        sut.getMidYearEstimatedPay(
          PayFrequency.Weekly,
          rtiEmp,
          NpsDate(new LocalDate(CurrentYear, 5, 20)),
          Income.Live.code) mustBe ((expectedPay, Some(228125)))
      }
      "the frequency is Fortnightly" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        sut.getMidYearEstimatedPay(
          PayFrequency.Fortnightly,
          rtiEmp,
          NpsDate(new LocalDate(CurrentYear, 5, 20)),
          Income.Live.code) mustBe ((expectedPay, Some(228125)))
      }
      "the frequency is FourWeekly" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled).thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled).thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        sut.getMidYearEstimatedPay(
          PayFrequency.FourWeekly,
          rtiEmp,
          NpsDate(new LocalDate(CurrentYear, 5, 20)),
          Income.Live.code) mustBe ((expectedPay, Some(228125)))
      }
      "the frequency is Monthly" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        sut.getMidYearEstimatedPay(
          PayFrequency.Monthly,
          rtiEmp,
          NpsDate(new LocalDate(CurrentYear, 5, 20)),
          Income.Live.code) mustBe ((expectedPay, Some(228125)))
      }
      "the frequency is Quarterly" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        sut.getMidYearEstimatedPay(
          PayFrequency.Quarterly,
          rtiEmp,
          NpsDate(new LocalDate(CurrentYear, 5, 20)),
          Income.Live.code) mustBe ((Some(80000), Some(80000)))
      }
      "the frequency is BiAnnually" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        sut.getMidYearEstimatedPay(
          PayFrequency.BiAnnually,
          rtiEmp,
          NpsDate(new LocalDate(CurrentYear, 5, 20)),
          Income.Live.code) mustBe ((Some(40000), Some(40000)))
      }
      "the frequency is Annually" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        sut.getMidYearEstimatedPay(
          PayFrequency.Annually,
          rtiEmp,
          NpsDate(new LocalDate(CurrentYear, 5, 20)),
          Income.Live.code) mustBe ((Some(20000), Some(20000)))
      }
    }
    "return the mid year estimated pay values for the current year" when {
      "the frequency is OneOff" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        val payment = RtiPayment(
          PayFrequency.FourWeekly,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(20000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10)
        )

        val rtiEmp = RtiEmployment("", "123", "", List(payment), Nil, Some("1000"), 1)

        sut.getMidYearEstimatedPay(
          PayFrequency.OneOff,
          rtiEmp,
          NpsDate(new LocalDate(CurrentYear, 5, 20)),
          Income.Live.code) mustBe ((Some(20000), None))
      }
    }
    "return the taxable pay year to date value as the mid year estimated pay values for the current year and the next year" when {
      s"the frequency is Irregular and the employment type is live and the taxable pay to date is greater than the default primary pay amount (${TaiConstants.DEFAULT_PRIMARY_PAY})" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        val payment = RtiPayment(
          PayFrequency.Irregular,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(20000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10)
        )

        val rtiEmp = RtiEmployment("", "123", "", List(payment), Nil, Some("1000"), 1)

        sut.getMidYearEstimatedPay(
          PayFrequency.Irregular,
          rtiEmp,
          NpsDate(new LocalDate(CurrentYear, 5, 20)),
          Income.Live.code) mustBe ((Some(20000), Some(20000)))
      }
    }
    "return the primary pay value as the mid year estimated pay values for the current year and the next year" when {
      s"the frequency is Irregular and the employment type is live and the taxable pay to date is less than the default primary pay amount (${TaiConstants.DEFAULT_PRIMARY_PAY})" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        val payment = RtiPayment(
          PayFrequency.Irregular,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(8000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10)
        )

        val rtiEmp = RtiEmployment("", "123", "", List(payment), Nil, Some("1000"), 1)

        sut.getMidYearEstimatedPay(
          PayFrequency.Irregular,
          rtiEmp,
          NpsDate(new LocalDate(CurrentYear, 5, 20)),
          Income.Live.code) mustBe ((Some(15000), Some(15000)))
      }
    }
    "return the secondary pay value as the mid year estimated pay values for the current year and the next year" when {
      s"the frequency is Irregular and the employment type is not live and the taxable pay to date is less than the default secondary pay amount (${TaiConstants.DEFAULT_SECONDARY_PAY})" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        val payment = RtiPayment(
          PayFrequency.Irregular,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(3000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10)
        )

        val rtiEmp = RtiEmployment("", "123", "", List(payment), Nil, Some("1000"), 1)

        sut.getMidYearEstimatedPay(PayFrequency.Irregular, rtiEmp, NpsDate(new LocalDate(CurrentYear, 5, 20)), 3) mustBe (
          (
            Some(5000),
            Some(5000)))
      }
    }
  }
  "getContinuousEstimatedPay" must {
    "return the continuous estimated pay value" when {
      "the pay frequency is weekly" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        val payment = RtiPayment(
          PayFrequency.Weekly,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(3000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10)
        )

        val rtiEmp = RtiEmployment("", "123", "", List(payment), Nil, Some("1000"), 1)

        sut.getContinuousEstimatedPay(PayFrequency.Weekly, rtiEmp, 1) mustBe Some(15600)
      }

      "the pay frequency is fortnightly" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.Fortnightly,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(3000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10)
        )

        val rtiEmp = RtiEmployment("", "123", "", List(payment), Nil, Some("1000"), 1)

        sut.getContinuousEstimatedPay(PayFrequency.Fortnightly, rtiEmp, 1) mustBe Some(15600)
      }

      "the pay frequency is four-weekly" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.FourWeekly,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(3000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10)
        )

        val rtiEmp = RtiEmployment("", "123", "", List(payment), Nil, Some("1000"), 1)

        sut.getContinuousEstimatedPay(PayFrequency.FourWeekly, rtiEmp, 1) mustBe Some(15600)
      }

      "the pay frequency is monthly" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.Monthly,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(3000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10),
          Some(2)
        )

        val rtiEmp = RtiEmployment("", "123", "", List(payment), Nil, Some("1000"), 1)

        sut.getContinuousEstimatedPay(PayFrequency.Monthly, rtiEmp, 1) mustBe Some(18000)
      }

      "the pay frequency is quarterly" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.Quarterly,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(3000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10),
          Some(2)
        )

        val rtiEmp = RtiEmployment("", "123", "", List(payment), Nil, Some("1000"), 1)

        sut.getContinuousEstimatedPay(PayFrequency.Quarterly, rtiEmp, 1) mustBe Some(6000)
      }

      "the pay frequency is biannually" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.BiAnnually,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(3000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10),
          Some(2)
        )

        val rtiEmp = RtiEmployment("", "123", "", List(payment), Nil, Some("1000"), 1)

        sut.getContinuousEstimatedPay(PayFrequency.BiAnnually, rtiEmp, 1) mustBe Some(3000)
      }

      "the pay frequency is annually" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        sut.getContinuousEstimatedPay(PayFrequency.Annually, rtiEmp, 1) mustBe Some(20000)
      }

      "the pay frequency is one-off" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        sut.getContinuousEstimatedPay(PayFrequency.OneOff, rtiEmp, 1) mustBe Some(20000)
      }
    }

    "return the taxable pay year to date value as the continuous estimated pay values for the current year" when {
      s"the frequency is Irregular and the employment type is live and the taxable pay to date is greater than the default primary pay amount (${TaiConstants.DEFAULT_PRIMARY_PAY})" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        sut.getContinuousEstimatedPay(PayFrequency.Irregular, rtiEmp, 1) mustBe Some(20000)
      }
    }

    "return the primary pay value as the continuous estimated pay values for the current year" when {
      s"the frequency is Irregular and the employment type is live and the taxable pay to date is less than the default primary pay amount (${TaiConstants.DEFAULT_PRIMARY_PAY})" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.Irregular,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(8000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10)
        )

        val rtiEmp = RtiEmployment("", "123", "", List(payment), Nil, Some("1000"), 1)

        sut.getContinuousEstimatedPay(PayFrequency.Irregular, rtiEmp, 1) mustBe Some(15000)
      }
    }

    "return the secondary pay value as the continuous estimated pay values for the current year" when {
      s"the frequency is Irregular and the employment type is not live and the taxable pay to date is less than the default secondary pay amount (${TaiConstants.DEFAULT_SECONDARY_PAY})" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.Irregular,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(3000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10)
        )

        val rtiEmp = RtiEmployment("", "123", "", List(payment), Nil, Some("1000"), 1)

        sut.getContinuousEstimatedPay(PayFrequency.Irregular, rtiEmp, 1) mustBe Some(15000)
      }
    }
  }
  "getEstPayWithMonthNo" must {
    "return the estimated pay" when {
      "the number of months is one and the month of tax year is one" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.Irregular,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(3000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10),
          Some(1)
        )
        val noOfMonths = 1

        sut.getEstPayWithMonthNo(payment, noOfMonths) mustBe Some(3000)
      }

      "the number of months is minus one and the month of tax year is two" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.Irregular,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(3000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10),
          Some(2)
        )
        val noOfMonths = -1

        sut.getEstPayWithMonthNo(payment, noOfMonths) mustBe Some(-1500)
      }

      "the number of months is minus two and the month of tax year is minus two" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.Irregular,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(3000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10),
          Some(-2)
        )
        val noOfMonths = -2

        sut.getEstPayWithMonthNo(payment, noOfMonths) mustBe Some(3000)
      }

      "the number of months is two and the month of tax year is 12" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.Irregular,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(3000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10),
          Some(12)
        )
        val noOfMonths = 2

        sut.getEstPayWithMonthNo(payment, noOfMonths) mustBe Some(500)
      }

      "the number of months is six and the month of tax year is two" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.Irregular,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(3000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10),
          Some(2)
        )
        val noOfMonths = 6

        sut.getEstPayWithMonthNo(payment, noOfMonths) mustBe Some(9000)
      }
    }

    "return nothing for the estimated pay" when {
      "there is no month of tax year" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.Irregular,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(3000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10),
          None
        )
        val noOfMonths = 1

        sut.getEstPayWithMonthNo(payment, noOfMonths) mustBe None
      }
    }
  }

  "getRemainingBiAnnual" must {
    s"return ${SIX_MONTHS.remainingBiAnnual}" when {
      "the last paid on date is before the middle of the tax year" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.Monthly,
          new LocalDate(CurrentYear, 8, 25),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(3000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          None,
          None
        )

        sut.getRemainingBiAnnual(payment) mustBe SIX_MONTHS.remainingBiAnnual
      }

      "the last paid on date is the day before the middle of the tax year" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.Monthly,
          new LocalDate(CurrentYear, 10, 5),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(3000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          None,
          None
        )

        sut.getRemainingBiAnnual(payment) mustBe SIX_MONTHS.remainingBiAnnual
      }
    }

    s"return 0" when {
      "the last paid on date is after the middle of the tax year" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled).thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled).thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled).thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.Monthly,
          new LocalDate(NextYear, 1, 1),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(3000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          None,
          None
        )

        sut.getRemainingBiAnnual(payment) mustBe 0
      }

      "the last paid on date is the day after the middle of the tax year" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled).thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled).thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled).thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled).thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.Monthly,
          midPointofTaxYearPlusOneDay,
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(3000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          None,
          None
        )

        sut.getRemainingBiAnnual(payment) mustBe 0
      }
    }
  }
  "getRemainingQuarter" must {
    s"return ${NINE_MONTHS.remainingQuarter}" when {
      s"days between is more than ${NINE_MONTHS.days}" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val daysBetween = 274

        sut.getRemainingQuarter(daysBetween) mustBe NINE_MONTHS.remainingQuarter
      }
    }

    s"return ${SIX_MONTHS.remainingQuarter}" when {
      s"days between is one less than ${NINE_MONTHS.days}" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val daysBetween = 272

        sut.getRemainingQuarter(daysBetween) mustBe SIX_MONTHS.remainingQuarter
      }

      s"days between is one more than ${SIX_MONTHS.days}" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val daysBetween = 183

        sut.getRemainingQuarter(daysBetween) mustBe SIX_MONTHS.remainingQuarter
      }
    }

    s"return ${THREE_MONTHS.remainingQuarter}" when {
      s"days between is one less than ${SIX_MONTHS.days}" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val daysBetween = 181

        sut.getRemainingQuarter(daysBetween) mustBe THREE_MONTHS.remainingQuarter
      }

      s"days between is one more than ${THREE_MONTHS.days}" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val daysBetween = 91

        sut.getRemainingQuarter(daysBetween) mustBe THREE_MONTHS.remainingQuarter
      }
    }

    "return 0" when {
      s"days between is one less than ${THREE_MONTHS.days}" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val daysBetween = 89

        sut.getRemainingQuarter(daysBetween) mustBe 0
      }

      s"days is ${NINE_MONTHS.days}" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val daysBetween = NINE_MONTHS.days

        sut.getRemainingQuarter(daysBetween) mustBe 0
      }

      s"days is ${SIX_MONTHS.days}" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val daysBetween = SIX_MONTHS.days

        sut.getRemainingQuarter(daysBetween) mustBe 0
      }

      s"days is ${THREE_MONTHS.days}" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val daysBetween = THREE_MONTHS.days

        sut.getRemainingQuarter(daysBetween) mustBe 0
      }
    }
  }

  "getFinalEstPayWithDefault" must {
    "return the taxable pay year to date amount" when {
      "the estimated pay is less than the taxable pay year to date amount" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.Irregular,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(8000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10)
        )

        val rtiEmp = RtiEmployment("", "123", "", List(payment), Nil, Some("1000"), 1)

        sut.getFinalEstPayWithDefault(Some(1000), rtiEmp) mustBe Some(8000)
      }
    }
    "return the estimated pay" when {
      "the estimated pay is greater than the taxable pay year to date amount" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val payment = RtiPayment(
          PayFrequency.Irregular,
          new LocalDate(CurrentYear, 6, 20),
          new LocalDate(CurrentYear, 4, 20),
          BigDecimal(20),
          BigDecimal(8000),
          BigDecimal(0),
          BigDecimal(0),
          None,
          isOccupationalPension = false,
          None,
          Some(10)
        )

        val rtiEmp = RtiEmployment("", "123", "", List(payment), Nil, Some("1000"), 1)

        sut.getFinalEstPayWithDefault(Some(10000), rtiEmp) mustBe Some(10000)
      }
      "the estimated pay is not present" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        sut.getFinalEstPayWithDefault(None, rtiEmp) mustBe None
      }
    }
  }

  "compareTaxDistrictNos" must {
    val mockNpsConnector = mock[NpsConnector]
    val mockDesConnector = mock[DesConnector]

    val mockNpsConfig = mock[NpsConfig]
    when(mockNpsConfig.autoUpdatePayEnabled)
      .thenReturn(Some(true))
    when(mockNpsConfig.updateSourceEnabled)
      .thenReturn(Some(true))
    when(mockNpsConfig.postCalcEnabled)
      .thenReturn(Some(true))

    val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
    when(mockFeatureTogglesConfig.desEnabled)
      .thenReturn(true)
    val mockIncomeHelper = mock[IncomeHelper]

    val sut = createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

    "return true" when {
      "employmentTaxDistrictNo and officeNo are the same" in {
        sut.compareTaxDistrictNos("test", "test") mustBe true
        sut.compareTaxDistrictNos("1", "1") mustBe true
      }
    }

    "return false" when {
      "the values do not match for a string value and are not numbers" in {
        sut.compareTaxDistrictNos("test", "foo") mustBe false
        sut.compareTaxDistrictNos("1", "1 ") mustBe false
      }
    }
  }

  "findEmployment" must {
    val mockNpsConnector = mock[NpsConnector]
    val mockDesConnector = mock[DesConnector]

    val mockNpsConfig = mock[NpsConfig]
    when(mockNpsConfig.autoUpdatePayEnabled)
      .thenReturn(Some(true))
    when(mockNpsConfig.updateSourceEnabled)
      .thenReturn(Some(true))
    when(mockNpsConfig.postCalcEnabled)
      .thenReturn(Some(true))

    val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
    when(mockFeatureTogglesConfig.desEnabled)
      .thenReturn(true)
    val mockIncomeHelper = mock[IncomeHelper]

    val sut = createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

    "return an NPS employment" when {
      "a single matching employment is given" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val rtiEmp = RtiEmployment("23", "123", "", Nil, Nil, Some("1000"), 1)

        val npsEmployment = NpsEmployment(
          1,
          NpsDate(new LocalDate(CurrentYear, 4, 23)),
          Some(NpsDate(new LocalDate(CurrentYear, 3, 30))),
          "23",
          "123",
          None,
          1,
          Some(Ceased.code),
          Some("1000"),
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          Some(1000)
        )

        sut.findEmployment(rtiEmp, List(npsEmployment)) mustBe Some(npsEmployment)
      }

      "the given rti employment matches multiple nps employments" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val rtiEmp = RtiEmployment("23", "123", "", Nil, Nil, Some("1001"), 1)

        val npsEmployment1 = NpsEmployment(
          1,
          NpsDate(new LocalDate(CurrentYear, 4, 23)),
          Some(NpsDate(new LocalDate(CurrentYear, 3, 30))),
          "23",
          "123",
          None,
          1,
          Some(Ceased.code),
          Some("1001"),
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          Some(1000)
        )

        val npsEmployment2 = NpsEmployment(
          1,
          NpsDate(new LocalDate(CurrentYear, 4, 23)),
          Some(NpsDate(new LocalDate(CurrentYear, 3, 30))),
          "23",
          "123",
          None,
          1,
          Some(Ceased.code),
          Some("1000"),
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          Some(1000)
        )

        sut.findEmployment(rtiEmp, List(npsEmployment1, npsEmployment2)) mustBe Some(npsEmployment1)
      }
    }

    "not return any employment" when {
      "there no employments are provided" in {
        val rtiEmp = RtiEmployment("23", "123", "", Nil, Nil, Some("1000"), 1)

        sut.findEmployment(rtiEmp, Nil) mustBe None
      }

      "the given employment\'s tax district number does not match an rti employment office ref number" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val rtiEmp = RtiEmployment("23", "123", "", Nil, Nil, Some("1000"), 1)

        val npsEmployment = NpsEmployment(
          1,
          NpsDate(new LocalDate(CurrentYear, 4, 23)),
          Some(NpsDate(new LocalDate(CurrentYear, 3, 30))),
          "24",
          "123",
          None,
          1,
          Some(Ceased.code),
          Some("1000"),
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          Some(1000)
        )

        sut.findEmployment(rtiEmp, List(npsEmployment)) mustBe None
      }

      "the given employment\'s PAYE number does not match an rti employment PAYE ref" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val rtiEmp = RtiEmployment("23", "123", "", Nil, Nil, Some("1000"), 1)

        val npsEmployment = NpsEmployment(
          1,
          NpsDate(new LocalDate(CurrentYear, 4, 23)),
          Some(NpsDate(new LocalDate(CurrentYear, 3, 30))),
          "23",
          "1234",
          None,
          1,
          Some(Ceased.code),
          Some("1000"),
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          Some(1000)
        )

        sut.findEmployment(rtiEmp, List(npsEmployment)) mustBe None
      }

      "the rti employment current pay id doesn\'t match any of the employment\'s works numbers" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val rtiEmp = RtiEmployment("23", "123", "", Nil, Nil, Some("1000"), 1)

        val npsEmployment1 = NpsEmployment(
          1,
          NpsDate(new LocalDate(CurrentYear, 4, 23)),
          Some(NpsDate(new LocalDate(CurrentYear, 3, 30))),
          "23",
          "123",
          None,
          1,
          Some(Ceased.code),
          Some("1001"),
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          Some(1000)
        )

        val npsEmployment2 = NpsEmployment(
          1,
          NpsDate(new LocalDate(CurrentYear, 4, 23)),
          Some(NpsDate(new LocalDate(CurrentYear, 3, 30))),
          "23",
          "123",
          None,
          1,
          Some(Ceased.code),
          Some("1002"),
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          Some(1000)
        )

        sut.findEmployment(rtiEmp, List(npsEmployment1, npsEmployment2)) mustBe None
      }
    }
  }

  "updateEmploymentData" must {
    "call the DES updateEmploymentDataToDes with the correct parameters" when {
      "the update DES indicator is set" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        when(mockFeatureTogglesConfig.desUpdateEnabled)
          .thenReturn(true)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        val ninoCaptor = ArgumentCaptor.forClass(classOf[Nino])
        val taxYearCaptor = ArgumentCaptor.forClass(classOf[Int])
        val iadbTypeCaptor = ArgumentCaptor.forClass(classOf[Int])
        val versionCaptor = ArgumentCaptor.forClass(classOf[Int])
        val updateAmountsCaptor = ArgumentCaptor.forClass(classOf[List[IabdUpdateAmount]])
        val apiTypesCaptor = ArgumentCaptor.forClass(classOf[APITypes])

        val npsUpdate2 = npsUpdateAmount.copy(grossAmount = 3000)

        val rtiCalc = RtiCalc(1, None, None, 1, 1, "EmployerName", 35000, None)

        val iabdRoot = NpsIabdRoot(nino.value, Some(1), IabdType.NewEstimatedPay.code, Some(1000))

        when(mockDesConnector.updateEmploymentDataToDes(any(), anyInt, anyInt, anyInt, any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, "")))

        val result: Seq[HttpResponse] = 
          sut.updateEmploymentData(
            nino,
            CurrentYear,
            Nil,
            List(npsUpdateAmount),
            List(npsUpdate2),
            List(rtiCalc),
            1,
            List(iabdRoot))(HeaderCarrier()).futureValue

        result.length mustBe 2

        verify(mockDesConnector, times(2)).updateEmploymentDataToDes(
          ninoCaptor.capture,
          taxYearCaptor.capture,
          iadbTypeCaptor.capture,
          versionCaptor.capture,
          updateAmountsCaptor.capture,
          apiTypesCaptor.capture)(any())

        ninoCaptor.getAllValues.toList mustBe List(nino, nino)
        taxYearCaptor.getAllValues.toList mustBe List(CurrentYear, NextYear)
        iadbTypeCaptor.getAllValues.toList mustBe List(IabdType.NewEstimatedPay.code, IabdType.NewEstimatedPay.code)
        versionCaptor.getAllValues.toList mustBe List(1, 2)
        updateAmountsCaptor.getAllValues.toList mustBe List(
          List(IabdUpdateAmount(1, 1000, None, None, Some(46))),
          List(IabdUpdateAmount(1, 3000, None, None, Some(46))))
        apiTypesCaptor.getAllValues.toList mustBe List(null, null)

      }
    }
    "not call the DES connector" when {
      "there are no updates in the current year" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        when(mockFeatureTogglesConfig.desUpdateEnabled)
          .thenReturn(true)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val rtiCalc = RtiCalc(1, None, None, 1, 1, "EmployerName", 35000, None)

        when(mockDesConnector.updateEmploymentDataToDes(any(), anyInt, anyInt, anyInt, any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, "")))

        val result: Seq[HttpResponse] = 
          sut.updateEmploymentData(nino, CurrentYear, Nil, Nil, Nil, List(rtiCalc), 1, Nil)(HeaderCarrier()).futureValue

        result.length mustBe 0

        verify(mockDesConnector, never).updateEmploymentDataToDes(any(), any(), any(), any(), any(), any())(any())
      }
      "the update DES indicator is not set" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        when(mockFeatureTogglesConfig.desUpdateEnabled)
          .thenReturn(false)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val rtiCalc = RtiCalc(1, None, None, 1, 1, "EmployerName", 35000, None)

        when(mockNpsConnector.updateEmploymentData(any(), anyInt, anyInt, anyInt, any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, "")))

        val result: Seq[HttpResponse] = 
          sut.updateEmploymentData(nino, CurrentYear, Nil, List(npsUpdateAmount), Nil, List(rtiCalc), 1, Nil)(
            HeaderCarrier()).futureValue

        result.length mustBe 2

        verify(mockDesConnector, never).updateEmploymentDataToDes(any(), any(), any(), any(), any(), any())(any())
      }
      "the employment does not match the rti calc" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        when(mockFeatureTogglesConfig.desUpdateEnabled)
          .thenReturn(true)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val npsUpdate2 = npsUpdateAmount.copy(grossAmount = 3000)

        val rtiCalc = RtiCalc(1, None, None, 2, 1, "EmployerName", 35000, None)

        val iabdRoot = NpsIabdRoot(nino.value, Some(1), IabdType.NewEstimatedPay.code, Some(1000))

        when(mockDesConnector.updateEmploymentDataToDes(any(), anyInt, anyInt, anyInt, any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, "")))

        val result: Seq[HttpResponse] = 
          sut.updateEmploymentData(
            nino,
            CurrentYear,
            Nil,
            List(npsUpdateAmount),
            List(npsUpdate2),
            List(rtiCalc),
            1,
            List(iabdRoot))(HeaderCarrier()).futureValue

        result.length mustBe 0

        verify(mockDesConnector, never).updateEmploymentDataToDes(any(), any(), any(), any(), any(), any())(any())

      }
    }
    "call the NPS updateEmploymentData with the correct parameters" when {
      "the update DES indicator is not set" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        when(mockFeatureTogglesConfig.desUpdateEnabled)
          .thenReturn(false)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        val ninoCaptor = ArgumentCaptor.forClass(classOf[Nino])
        val taxYearCaptor = ArgumentCaptor.forClass(classOf[Int])
        val iadbTypeCaptor = ArgumentCaptor.forClass(classOf[Int])
        val versionCaptor = ArgumentCaptor.forClass(classOf[Int])
        val updateAmountsCaptor = ArgumentCaptor.forClass(classOf[List[IabdUpdateAmount]])
        val apiTypesCaptor = ArgumentCaptor.forClass(classOf[APITypes])

        val npsUpdate2 = npsUpdateAmount.copy(grossAmount = 3000)

        val rtiCalc = RtiCalc(1, None, None, 1, 1, "EmployerName", 35000, None)

        when(mockNpsConnector.updateEmploymentData(any(), anyInt, anyInt, anyInt, any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, "")))

        val result: Seq[HttpResponse] = 
          sut.updateEmploymentData(
            nino,
            CurrentYear,
            Nil,
            List(npsUpdateAmount),
            List(npsUpdate2),
            List(rtiCalc),
            1,
            Nil)(HeaderCarrier()).futureValue

        result.length mustBe 2

        verify(mockNpsConnector, times(2)).updateEmploymentData(
          ninoCaptor.capture,
          taxYearCaptor.capture,
          iadbTypeCaptor.capture,
          versionCaptor.capture,
          updateAmountsCaptor.capture,
          apiTypesCaptor.capture)(any())

        ninoCaptor.getAllValues.toList mustBe List(nino, nino)
        taxYearCaptor.getAllValues.toList mustBe List(CurrentYear, NextYear)
        iadbTypeCaptor.getAllValues.toList mustBe List(IabdType.NewEstimatedPay.code, IabdType.NewEstimatedPay.code)
        versionCaptor.getAllValues.toList mustBe List(1, 2)
        updateAmountsCaptor.getAllValues.toList mustBe List(
          List(IabdUpdateAmount(1, 1000, None, None, Some(46))),
          List(IabdUpdateAmount(1, 3000, None, None, Some(46))))
        apiTypesCaptor.getAllValues.toList mustBe List(null, null)

      }
    }
    "not call the NPS connector" when {
      "there are no updates in the current year" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        when(mockFeatureTogglesConfig.desUpdateEnabled)
          .thenReturn(false)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val rtiCalc = RtiCalc(1, None, None, 1, 1, "EmployerName", 35000, None)

        when(mockNpsConnector.updateEmploymentData(any(), anyInt, anyInt, anyInt, any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, "")))

        val resp: Seq[HttpResponse] = 
          sut.updateEmploymentData(nino, CurrentYear, Nil, Nil, Nil, List(rtiCalc), 1, Nil)(HeaderCarrier()).futureValue

        resp.length mustBe 0

        verify(mockNpsConnector, never).updateEmploymentData(any(), any(), any(), any(), any(), any())(any())
      }
      "the update DES indicator is set" in {

        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        when(mockFeatureTogglesConfig.desUpdateEnabled)
          .thenReturn(true)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)

        val rtiCalc = RtiCalc(1, None, None, 1, 1, "EmployerName", 35000, None)

        when(mockDesConnector.updateEmploymentDataToDes(any(), anyInt, anyInt, anyInt, any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, "")))

        val resp: Seq[HttpResponse] = 
          sut.updateEmploymentData(nino, CurrentYear, Nil, List(npsUpdateAmount), Nil, List(rtiCalc), 1, Nil)(
            HeaderCarrier()).futureValue

        resp.length mustBe 2

        verify(mockNpsConnector, never).updateEmploymentData(any(), any(), any(), any(), any(), any())(any())
      }
    }
    "Return the response for the current year update" when {
      "the next year update fails" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        when(mockFeatureTogglesConfig.desUpdateEnabled)
          .thenReturn(true)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        val rtiCalc = RtiCalc(1, None, None, 1, 1, "EmployerName", 35000, None)

        when(mockDesConnector.updateEmploymentDataToDes(any(), anyInt, anyInt, anyInt, any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, "")))
          .thenReturn(Future.failed(new RuntimeException("second call failed")))

        val resp: Seq[HttpResponse] = 
          sut.updateEmploymentData(nino, CurrentYear, Nil, List(npsUpdateAmount), Nil, List(rtiCalc), 1, Nil)(
            HeaderCarrier()).futureValue

        resp.length mustBe 1

        verify(mockNpsConnector, never).updateEmploymentData(any(), any(), any(), any(), any(), any())(any())

      }
    }
  }

  "forceUpdateEmploymentData" must {
    "return false" when {
      "totalPayToDate is NOT defined" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        when(mockFeatureTogglesConfig.desUpdateEnabled)
          .thenReturn(true)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        val result = sut.forceUpdateEmploymentData(totalPayToDate = None, originalAmount = Some(BigDecimal("100")))

        result mustBe false
      }
      "originalAmount is NOT defined" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        when(mockFeatureTogglesConfig.desUpdateEnabled)
          .thenReturn(true)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        val result = sut.forceUpdateEmploymentData(totalPayToDate = None, originalAmount = Some(BigDecimal("100")))

        result mustBe false
      }
      "originalAmount is BIGGER than totalPayToDate" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        when(mockFeatureTogglesConfig.desUpdateEnabled)
          .thenReturn(true)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        val result = sut
          .forceUpdateEmploymentData(totalPayToDate = Some(BigDecimal("100")), originalAmount = Some(BigDecimal("200")))

        result mustBe false
      }
      "originalAmount is EQUAL to totalPayToDate" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        when(mockFeatureTogglesConfig.desUpdateEnabled)
          .thenReturn(true)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        val result = sut
          .forceUpdateEmploymentData(totalPayToDate = Some(BigDecimal("100")), originalAmount = Some(BigDecimal("100")))

        result mustBe false
      }
    }
    "return true" when {
      "totalPayDate is BIGGER than originalAmount" in {
        val mockNpsConnector = mock[NpsConnector]
        val mockDesConnector = mock[DesConnector]

        val mockNpsConfig = mock[NpsConfig]
        when(mockNpsConfig.autoUpdatePayEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.updateSourceEnabled)
          .thenReturn(Some(true))
        when(mockNpsConfig.postCalcEnabled)
          .thenReturn(Some(true))

        val mockFeatureTogglesConfig = mock[FeatureTogglesConfig]
        when(mockFeatureTogglesConfig.desEnabled)
          .thenReturn(true)
        when(mockFeatureTogglesConfig.desUpdateEnabled)
          .thenReturn(true)

        val mockIncomeHelper = mock[IncomeHelper]

        val sut =
          createSUT(mockNpsConnector, mockDesConnector, mockFeatureTogglesConfig, mockNpsConfig, mockIncomeHelper)
        val result = sut
          .forceUpdateEmploymentData(totalPayToDate = Some(BigDecimal("200")), originalAmount = Some(BigDecimal("100")))

        result mustBe true
      }
    }
  }

  private val CurrentYear: Int = TaxYear().year
  private val NextYear: Int = CurrentYear + 1
  private val npsDateCurrentTaxYear: NpsDate = NpsDate(new LocalDate(CurrentYear, 4, 12))
  private val npsDateStartOfYear: NpsDate = NpsDate(new LocalDate(CurrentYear, 1, 1))

  val expectedPay =
    if (new LocalDate(TaxYear().year + 1, 1, 1).year().isLeap)
      Some(201250)
    else
      Some(200625)

  val daysInHalfYear: Int = Days.daysBetween(TaxYear().start, TaxYear().next.start).getDays() / 2

  val midPointofTaxYearPlusOneDay: LocalDate = TaxYear().start.plusDays(daysInHalfYear + 1)

  private val npsUpdateAmount = IabdUpdateAmount(employmentSequenceNumber = 1, grossAmount = 1000, source = Some(46))

  val payment = RtiPayment(
    PayFrequency.FourWeekly,
    new LocalDate(CurrentYear, 6, 20),
    new LocalDate(CurrentYear, 4, 20),
    BigDecimal(20),
    BigDecimal(20000),
    BigDecimal(0),
    BigDecimal(0),
    None,
    isOccupationalPension = false,
    None,
    Some(10)
  )

  val rtiEmp = RtiEmployment("", "123", "", List(payment), Nil, Some("1000"), 1)

  val rtiData = RtiData("", TaxYear(CurrentYear), "", List(RtiEmployment("", "123", "", Nil, Nil, Some("1000"), 1)))

  val iabdRoot = NpsIabdRoot(nino.value, Some(1), 23, Some(1000))

  val npsEmployment = NpsEmployment(
    1,
    npsDateStartOfYear,
    Some(npsDateCurrentTaxYear),
    "",
    "123",
    None,
    1,
    Some(Ceased.code),
    Some("1000"),
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    Some(100))

  val npsEmploymentWithCessationPay = NpsEmployment(
    1,
    npsDateStartOfYear,
    Some(npsDateCurrentTaxYear),
    "",
    "123",
    None,
    1,
    Some(Ceased.code),
    Some("1000"),
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    Some(1000))

  private def createSUT(
    nps: NpsConnector,
    des: DesConnector,
    featureTogglesConfig: FeatureTogglesConfig,
    npsConfig: NpsConfig,
    incomeHelper: IncomeHelper) =
    new AutoUpdatePayService(nps, des, featureTogglesConfig, npsConfig, incomeHelper)
}

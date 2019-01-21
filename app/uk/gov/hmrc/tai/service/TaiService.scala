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

import java.util.UUID

import com.google.inject.{Inject, Singleton}
import org.joda.time.LocalDate
import play.Logger
import play.api.libs.json.JsValue
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.calculators.EstimatedPayCalculator
import uk.gov.hmrc.tai.config.{CyPlusOneConfig, FeatureTogglesConfig, NpsConfig}
import uk.gov.hmrc.tai.connectors.{CitizenDetailsConnector, DesConnector, NpsConnector, RtiConnector}
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.nps._
import uk.gov.hmrc.tai.model.nps2.Income.Live
import uk.gov.hmrc.tai.model.nps2.{IabdType, NpsFormatter, TaxAccount}
import uk.gov.hmrc.tai.model.rti.{RtiData, RtiStatus}
import uk.gov.hmrc.tai.model.tai.{AnnualAccount, TaxYear}
import uk.gov.hmrc.tai.util.TaiConstants._
import uk.gov.hmrc.tai.util.{DateTimeHelper, TaiConstants}

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent._

@Singleton
class TaiService @Inject()(rti: RtiConnector,
                           nps: NpsConnector,
                           des: DesConnector,
                           cid: CitizenDetailsConnector,
                           autoUpdatePayService: AutoUpdatePayService,
                           nextYearComparisonService: NextYearComparisonService,
                           auditor: Auditor,
                           featureTogglesConfig: FeatureTogglesConfig,
                           npsConfig: NpsConfig,
                           cyPlusOneConfig: CyPlusOneConfig) extends NpsFormatter {

  val IabdUpdateSourceCustomerEntered: Int = if (featureTogglesConfig.desUpdateEnabled) 39 else 0

  def getTaiRoot(nino: Nino)(implicit hc: HeaderCarrier): Future[TaiRoot] = {
    cid.getPersonDetails(nino).map {
      personDetails => personDetails.toTaiRoot
    }
  }

  def getCalculatedTaxAccountPartial(nino: Nino, taxYear: Int)(implicit hc: HeaderCarrier): Future[TaxSummaryDetails] = {

    val employmentsFuture = nps.getEmployments(nino, taxYear)
    val iabdsFuture = if (featureTogglesConfig.desEnabled) des.getIabdsFromDes(nino, taxYear) else nps.getIabds(nino, taxYear)
    val taxAccountFuture = if (featureTogglesConfig.desEnabled) des.getCalculatedTaxAccountFromDes(nino, taxYear) else nps.getCalculatedTaxAccount(nino, taxYear)

    for {
      (employments, _, _, _) <- employmentsFuture
      iabds <- iabdsFuture
      (taxAccount, newVersion, _) <- taxAccountFuture
    } yield {
      taxAccount.toTaxSummary(newVersion, employments, iabds)
    }
  }

  def getAutoUpdateResults(nino: Nino, taxYear: Int)(implicit hc: HeaderCarrier):
  Future[(List[NpsEmployment], List[RtiCalc], List[nps2.NpsEmployment], List[GateKeeperRule], Seq[AnnualAccount])] = {

    val employmentsFuture = nps.getEmployments(nino, taxYear)
    val rtiPreviousFuture = rti.getRTI(nino, TaxYear().prev)
    for {
      (employments, newEmployments, currVersion, empGk) <- employmentsFuture
      isCeasedEmployment <- isNotCeasedOrCurrentYearCeasedEmployment(employments)
      allNpsEstimatedPays <- fetchIabdsForType(nino, taxYear, isCeasedEmployment)
      (rtiData, rtiStatus) <- fetchRtiCurrent(nino, taxYear, isCeasedEmployment)
      (rtiDataPY, rtiStatusPY) <- rtiPreviousFuture
    }
      yield {
        val rtiCalc = autoUpdatePayService.updateIncomes(nino, taxYear, employments, allNpsEstimatedPays, currVersion, rtiData)
        val annualAccounts = Seq(
          AnnualAccount(TaxYear().prev, None, rtiDataPY, Some(rtiStatusPY)),
          AnnualAccount(TaxYear(taxYear), None, rtiData, Some(rtiStatus))
        )
        (employments, rtiCalc, newEmployments, empGk, annualAccounts)
      }
  }

  private[service] def isNotCeasedOrCurrentYearCeasedEmployment(employments: List[NpsEmployment]): Future[Boolean] = {
    val checkCeasedEmployment = (endDate: Option[NpsDate]) => endDate match {
      case None => true
      case Some(npsDate) if (npsDate.localDate.isAfter(TaxYear().start) && npsDate.localDate.isBefore(TaxYear().next.start)) => true
      case _ => false
    }

    Future {
      employments.map(employment => employment.endDate).exists(checkCeasedEmployment(_))
    }
  }

  private[service] def fetchIabdsForType(nino: Nino, taxYear: Int, isNotCeasedEmployment: Boolean)(implicit hc: HeaderCarrier): Future[List[NpsIabdRoot]] = {
    if (isNotCeasedEmployment) {
      if (featureTogglesConfig.desEnabled) {
        des.getIabdsForTypeFromDes(nino, taxYear, IabdType.NewEstimatedPay.code)
      } else {
        nps.getIabdsForType(nino, taxYear, IabdType.NewEstimatedPay.code)
      }
    } else {
      Future {
        Nil
      }
    }
  }

  private[service] def fetchRtiCurrent(nino: Nino, taxYear: Int, isNotCeasedEmployment: Boolean)(implicit hc: HeaderCarrier): Future[(Option[RtiData], RtiStatus)] = {
    if (isNotCeasedEmployment) {
      rti.getRTI(nino, TaxYear(taxYear))
    } else {
      val ERROR_CODE = 404
      val ERROR_MESSAGE = "Employment ceased"
      Future {
        (None, RtiStatus(ERROR_CODE, ERROR_MESSAGE))
      }
    }
  }

  private[service] def getIabd(nino: Nino, taxYear: Int)(implicit hc: HeaderCarrier): Future[List[NpsIabdRoot]] = {
    if (featureTogglesConfig.desEnabled) des.getIabdsFromDes(nino, taxYear) else nps.getIabds(nino, taxYear)
  }

  private[service] def getCalculatedTaxAccountFromConnector(nino: Nino, taxYear: Int)(implicit hc: HeaderCarrier): Future[(NpsTaxAccount, Int, JsValue)] = {
    if (featureTogglesConfig.desEnabled) des.getCalculatedTaxAccountFromDes(nino, taxYear) else nps.getCalculatedTaxAccount(nino, taxYear)
  }

  def getCalculatedTaxAccount(nino: Nino, taxYear: Int, empResults: (List[NpsEmployment], List[RtiCalc],
    List[nps2.NpsEmployment], List[GateKeeperRule], Seq[AnnualAccount]), npsTaxAccount: (JsValue, Int))
                             (implicit hc: HeaderCarrier): Future[TaxSummaryDetails] = {
    val (employments, rtiCalc, npsEmployment, _, annualAccounts) = empResults
    val (npsTaxAccountJsValue, newVersion) = npsTaxAccount

    getIabd(nino, taxYear) flatMap { npsIabds =>

      val cyPyAnnualAccounts = annualAccounts.map(_.copy(nps = Some(npsTaxAccountJsValue.as[TaxAccount].withEmployments(npsEmployment))))

      val summary = npsTaxAccountJsValue.as[NpsTaxAccount].toTaxSummary(newVersion, employments, npsIabds,
        rtiCalc, cyPyAnnualAccounts.filter(_.year == TaxYear()).toList).copy(accounts = cyPyAnnualAccounts)

      if (invokeCYPlusOne(LocalDate.now())) {
        appendCYPlusOneToTaxSummary(nino, taxYear, cyPyAnnualAccounts, newVersion, employments, summary )
      } else {
        Future.successful(summary)
      }
    }
  }

  private[service] def appendCYPlusOneToTaxSummary(nino: Nino, taxYear: Int, cyPyAnnualAccounts: Seq[AnnualAccount], newVersion:Int, employments:List[NpsEmployment],
                                          summary: TaxSummaryDetails)(implicit hc: HeaderCarrier): Future[TaxSummaryDetails] = {
    getCalculatedTaxAccountFromConnector(nino, taxYear + 1) map {
      nextYear =>
        val taxAccountNY = nextYear._1
        val taxAccountNYJsValue = nextYear._3
        val taiAnnualAccountNY = AnnualAccount(TaxYear(taxYear).next, Some(taxAccountNYJsValue.as[TaxAccount]), None)
        val nextYearTaxAccount = nextYearComparisonService.stripCeasedFromNps(taxAccountNY).
          toTaxSummary(newVersion, employments.filter(x => x.employmentStatus == Some(Live.code)), accounts = List(taiAnnualAccountNY)).
          copy(accounts = List(taiAnnualAccountNY))

        nextYearComparisonService.proccessTaxSummaryWithCYPlusOne(summary, nextYearTaxAccount).copy(
          accounts = cyPyAnnualAccounts :+ taiAnnualAccountNY)
    } recover { case e: Throwable =>
      summary
    }
  }

  private[service] def invokeCYPlusOne(currentDate: LocalDate): Boolean = {
    val taxYear =  TaxYear(currentDate).year + 1
    val enabledDate = cyPlusOneConfig.cyPlusOneEnableDate.getOrElse(DEFAULT_CY_PLUS_ONE_ENABLED_DATE) + "/" + taxYear
    val CYEnabledDate = DateTimeHelper.convertToLocalDate(STANDARD_DATE_FORMAT, enabledDate)

    val isDateEnabled: Boolean = (cyPlusOneConfig.cyPlusOneEnableDate.isDefined, currentDate.isBefore(CYEnabledDate)) match {
      case (true, true) => false
      case (false, _) => true
      case (true, false) => true
    }
    cyPlusOneConfig.cyPlusOneEnabled.fold(false)(_ && isDateEnabled)
  }

  def updateEmployments(nino: Nino, taxYear: Int, iabdType: Int, editEmployments: IabdUpdateEmploymentsRequest)
                       (implicit hc: HeaderCarrier): Future[IabdUpdateEmploymentsResponse] = {
    val txId = sessionOrUUID

    val updatedEmployments = editEmployments.newAmounts.filter(x => x.newAmount != x.oldAmount)

    val editedAmounts: List[IabdUpdateAmount] = updatedEmployments.map(employment => getIadbUpdateAmount(employment))

    if (editedAmounts.nonEmpty) {
      Logger.info("Manual Update for User: " + nino.nino)
      cid.getPersonDetails(nino).flatMap { personDetails =>

        val editedAmounts: List[IabdUpdateAmount] = updatedEmployments.map(employment => getIadbUpdateAmount(employment))
        val refreshedVersion = personDetails.etag.toInt

        val updateEmpData = featureTogglesConfig.desUpdateEnabled match {
          case true => des.updateEmploymentDataToDes(nino, taxYear, iabdType, refreshedVersion, editedAmounts,
            apiType = APITypes.DesIabdUpdateEstPayManualAPI)
          case _ => nps.updateEmploymentData(nino, taxYear, iabdType, refreshedVersion, editedAmounts,
            apiType = APITypes.NpsIabdUpdateEstPayManualAPI)
        }
        updateEmpData map { currentYearUpdatedResponse =>

          featureTogglesConfig.desUpdateEnabled match {
            case true => des.updateEmploymentDataToDes(nino, taxYear + 1, iabdType, refreshedVersion + 1, editedAmounts)
            case _ => nps.updateEmploymentData(nino, taxYear + 1, iabdType, refreshedVersion + 1, editedAmounts)
          }

          val updatedResponse = new IabdUpdateEmploymentsResponse(TransactionId(txId), refreshedVersion + 2, iabdType, updatedEmployments)

          updatedResponse.newAmounts.foreach { updated =>
            auditor.sendDataEvent(
              transactionName = "Update Multiple Employments Data",
              detail = Map(
                "transactionId" -> updatedResponse.transaction.oid,
                "nino" -> nino.value,
                "year" -> taxYear.toString,
                "employmentId" -> updated.employmentId.toString,
                "newVersionNo" -> updatedResponse.version.toString,
                "newAmount" -> updated.newAmount.toString,
                "iabdType" -> updatedResponse.iabdType.toString))
          }

          updatedResponse
        }
      }
    } else {
      Future.successful(new IabdUpdateEmploymentsResponse(TransactionId(txId), editEmployments.version, iabdType, updatedEmployments))
    }
  }

  private[service] def getIadbUpdateAmount(employment: EmploymentAmount): IabdUpdateAmount = {
    val source: Option[Int] =  npsConfig.autoUpdatePayEnabled.collect{case true => IabdUpdateSourceCustomerEntered}
    IabdUpdateAmount(employmentSequenceNumber = employment.employmentId, grossAmount = employment.newAmount, source = source)
  }


  def getCalculatedEstimatedPay(payDetails: PayDetails) = {
    EstimatedPayCalculator.calculate(payDetails)
  }

  private[service] def sessionOrUUID(implicit hc: HeaderCarrier): String = {
    hc.sessionId match {
      case Some(sessionId) => sessionId.value
      case None => UUID.randomUUID().toString.replace("-", "")
    }

  }

}

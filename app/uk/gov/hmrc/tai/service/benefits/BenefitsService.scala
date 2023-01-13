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

package uk.gov.hmrc.tai.service.benefits

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.CompanyCarConnector
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.benefits._
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.templates.RemoveCompanyBenefitViewModel
import uk.gov.hmrc.tai.repositories.CompanyCarBenefitRepository
import uk.gov.hmrc.tai.service._
import uk.gov.hmrc.tai.templates.html.RemoveCompanyBenefitIForm
import uk.gov.hmrc.tai.util.IFormConstants

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success
import scala.util.control.NonFatal

@Singleton
class BenefitsService @Inject()(
  companyCarBenefitRepository: CompanyCarBenefitRepository,
  companyCarConnector: CompanyCarConnector,
  taxComponentService: CodingComponentService,
  iFormSubmissionService: IFormSubmissionService,
  cacheService: CacheService,
  auditable: Auditor)(implicit ec: ExecutionContext) {

  private val logger: Logger = Logger(getClass.getName)

  def companyCarBenefits(nino: Nino)(implicit hc: HeaderCarrier): Future[Seq[CompanyCarBenefit]] =
    benefits(nino, TaxYear()).map(_.companyCarBenefits)

  def companyCarBenefitForEmployment(nino: Nino, employmentSeqNum: Int)(
    implicit hc: HeaderCarrier): Future[Option[CompanyCarBenefit]] =
    companyCarBenefits(nino).map(allCars => allCars.find(_.employmentSeqNo == employmentSeqNum))

  def withdrawCompanyCarAndFuel(
    nino: Nino,
    employmentSeqNum: Int,
    carSeqNum: Int,
    removeCarAndFuel: WithdrawCarAndFuel)(implicit hc: HeaderCarrier): Future[String] =
    companyCarConnector.withdrawCarBenefit(nino, TaxYear(), employmentSeqNum, carSeqNum, removeCarAndFuel).andThen {
      case Success(_) => cacheService.invalidateTaiCacheData(nino)
    }

  def benefits(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[Benefits] =
    taxComponentService.codingComponents(nino, taxYear).map(filterBenefits).flatMap {
      case b @ Benefits(Seq(), otherbenefits) => Future.successful(b)
      case b                                  => reconcileCompanyCarsInBenefits(b, nino, taxYear)
    }

  def removeCompanyBenefits(nino: Nino, empId: Int, removeCompanyBenefit: RemoveCompanyBenefit)(
    implicit hc: HeaderCarrier): Future[String] =
    iFormSubmissionService.uploadIForm(
      nino,
      IFormConstants.RemoveCompanyBenefitSubmissionKey,
      "TES1",
      (person: Person) => {
        Future.successful(
          RemoveCompanyBenefitIForm(RemoveCompanyBenefitViewModel(person, removeCompanyBenefit)).toString)
      }
    ) map { envelopeId =>
      logger.info("Envelope Id for RemoveCompanyBenefit- " + envelopeId)

      auditable.sendDataEvent(
        transactionName = IFormConstants.RemoveCompanyBenefitAuditTxnName,
        detail = Map(
          "nino"                      -> nino.nino,
          "envelope Id"               -> envelopeId,
          "telephone contact allowed" -> removeCompanyBenefit.contactByPhone,
          "telephone number"          -> removeCompanyBenefit.phoneNumber.getOrElse(""),
          "Company Benefit Name"      -> removeCompanyBenefit.benefitType,
          "Amount Received"           -> removeCompanyBenefit.valueOfBenefit.getOrElse(""),
          "Date Ended"                -> removeCompanyBenefit.stopDate
        )
      )

      envelopeId
    }

  private def filterBenefits(codingComponents: Seq[CodingComponent]): Benefits = {

    val benefits = codingComponents.filter {
      _.componentType match {
        case _: BenefitComponentType => true
        case _                       => false
      }
    }

    val genericBenefits = benefits.collect {
      case CodingComponent(componentType: BenefitComponentType, employmentId, amount, _, _)
          if componentType != CarBenefit =>
        GenericBenefit(componentType, employmentId, amount)
    }
    val companyCars = codingComponents.collect {
      case CodingComponent(componentType: BenefitComponentType, employmentId, amount, _, _)
          if componentType == CarBenefit =>
        CompanyCarBenefit(
          employmentSeqNo = employmentId.getOrElse(0),
          grossAmount = amount,
          companyCars = Seq.empty[CompanyCar],
          version = None)
    }
    Benefits(companyCars, genericBenefits)
  }

  private def reconcileCompanyCarsInBenefits(benefits: Benefits, nino: Nino, taxYear: TaxYear)(
    implicit hc: HeaderCarrier): Future[Benefits] =
    companyCarBenefitRepository.carBenefit(nino, taxYear).map { c: Seq[CompanyCarBenefit] =>
      val reconciledBenefits = reconcileCarBenefits(benefits.companyCarBenefits, c)
      benefits.copy(companyCarBenefits = reconciledBenefits)
    } recover {
      case NonFatal(e) => {
        logger.warn(
          s"The PAYE company car service returned an expection in response to the request for nino ${nino.nino} " +
            s"and ${taxYear.toString}. Returning car benefit details WITHOUT company car information.",
          e
        )
        benefits
      }
    }

  private def reconcileCarBenefits(
    carBenefitsFromCodingComponents: Seq[CompanyCarBenefit],
    carBenefitsFromRepository: Seq[CompanyCarBenefit]): Seq[CompanyCarBenefit] =
    carBenefitsFromCodingComponents.map { carBenefit =>
      val matchedCarBenefit = carBenefitsFromRepository.find(_.employmentSeqNo == carBenefit.employmentSeqNo)
      matchedCarBenefit match {
        case None => carBenefit
        case Some(CompanyCarBenefit(_, _, companyCars, version)) =>
          carBenefit.copy(companyCars = companyCars, version = version)
      }
    }

}

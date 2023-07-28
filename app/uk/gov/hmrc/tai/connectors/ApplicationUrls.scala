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

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.tai.config._
import uk.gov.hmrc.tai.model.nps2.IabdType
import uk.gov.hmrc.tai.model.tai.TaxYear

@Singleton
class RtiUrls @Inject()(config: DesConfig) {

  private def paymentsUrl(nino: String) =
    s"${config.baseURL}/rti/individual/payments/nino/$nino"

  def paymentsForYearUrl(ninoWithoutSuffix: String, taxYear: TaxYear): String =
    s"${paymentsUrl(ninoWithoutSuffix)}/tax-year/${taxYear.twoDigitRange}"
}

@Singleton
class PdfUrls @Inject()(config: PdfConfig) {
  def generatePdfUrl = s"${config.baseURL}/pdf-generator-service/generate"
}

@Singleton
class FileUploadUrls @Inject()(config: FileUploadConfig) {

  def envelopesUrl = s"${config.baseURL}/file-upload/envelopes"
  def routingUrl = s"${config.baseURL}/file-routing/requests"
  def fileUrl(envelopeId: String, fileId: String) =
    s"${config.frontendBaseURL}/file-upload/upload/envelopes/$envelopeId/files/$fileId"
}

@Singleton
class PayeUrls @Inject()(config: PayeConfig) {

  def carBenefitsForYearUrl(nino: Nino, taxYear: TaxYear) =
    s"${config.baseURL}/paye/${nino.nino}/car-benefits/${taxYear.year}"
  def ninoVersionUrl(nino: Nino) = s"${config.baseURL}/paye/${nino.nino}/version"

  def removeCarBenefitUrl(nino: Nino, taxYear: TaxYear, employmentSequenceNumber: Int, carSequenceNumber: Int) =
    s"${config.baseURL}/paye/${nino.nino}/benefits/${taxYear.year}/$employmentSequenceNumber/car/$carSequenceNumber/remove"
}

@Singleton
class CitizenDetailsUrls @Inject()(config: CitizenDetailsConfig) {
  def designatoryDetailsUrl(nino: Nino) = s"${config.baseURL}/citizen-details/${nino.nino}/designatory-details"
  def etagUrl(nino: Nino) = s"${config.baseURL}/citizen-details/${nino.nino}/etag"
}

@Singleton
class BbsiUrls @Inject()(config: DesConfig) {

  def bbsiUrl(nino: Nino, taxYear: TaxYear): String = {
    def ninoWithoutSuffix(nino: Nino): String = nino.nino.take(8)

    s"${config.baseURL}/pre-population-of-investment-income/nino/${ninoWithoutSuffix(nino)}/tax-year/${taxYear.year}"
  }
}

@Singleton
class TaxAccountUrls @Inject()(npsConfig: NpsConfig, desConfig: DesConfig) {

  private val npsTaxAccountURL = (nino: Nino, taxYear: TaxYear) =>
    s"${npsConfig.baseURL}/person/${nino.nino}/tax-account/${taxYear.year}"

  def taxAccountHistoricSnapshotUrl(nino: Nino, iocdSeqNo: Int): String =
    s"${desConfig.baseURL}/pay-as-you-earn/individuals/${nino.nino}/tax-account/history/id/$iocdSeqNo"

  def taxAccountUrl(nino: Nino, taxYear: TaxYear): String =
    npsTaxAccountURL(nino, taxYear)
}

@Singleton
class IabdUrls @Inject()(npsConfig: NpsConfig, desConfig: DesConfig) {

  def npsIabdUrl(nino: Nino, taxYear: TaxYear): String =
    s"${npsConfig.baseURL}/person/${nino.nino}/iabds/${taxYear.year}"

  def npsIabdEmploymentUrl(nino: Nino, taxYear: TaxYear, iabdType: Int): String =
    s"${npsIabdUrl(nino, taxYear)}/employment/$iabdType"

  def desIabdUrl(nino: Nino, taxYear: TaxYear): String =
    s"${desConfig.baseURL}/pay-as-you-earn/individuals/${nino.nino}/iabds/tax-year/${taxYear.year}"

  def desIabdByTypeUrl(nino: Nino, taxYear: TaxYear, iabd: IabdType): String =
    s"${desConfig.baseURL}/pay-as-you-earn/individuals/${nino.nino}/iabds/tax-year/${taxYear.year}?type=${iabd.code}"

  def desIabdEmploymentUrl(nino: Nino, taxYear: TaxYear, iabdType: Int): String =
    s"${desIabdUrl(nino, taxYear)}/employment/$iabdType"
}

@Singleton
class TaxCodeChangeUrl @Inject()(config: DesConfig) {

  def taxCodeChangeUrl(nino: Nino, year: TaxYear): String = {
    s"${config.baseURL}/individuals/tax-code-history/list/${nino.nino}/${year.year}?endTaxYear=${year.year}"
  }
}

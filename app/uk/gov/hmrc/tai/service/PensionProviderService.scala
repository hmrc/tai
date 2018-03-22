/*
 * Copyright 2018 HM Revenue & Customs
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

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.model.domain.{AddPensionProvider, Person}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.templates.EmploymentPensionViewModel
import uk.gov.hmrc.tai.templates.html.PensionProviderIForm
import uk.gov.hmrc.tai.util.IFormConstants

import scala.concurrent.Future

@Singleton
class PensionProviderService @Inject()(iFormSubmissionService: IFormSubmissionService,
                                       auditable: Auditor) {

  def addPensionProvider(nino: Nino, pensionProvider: AddPensionProvider)(implicit hc: HeaderCarrier): Future[String] = {
    iFormSubmissionService.uploadIForm(nino, IFormConstants.AddPensionProviderSubmissionKey, "TES1", (person: Person) => {
      val templateModel = EmploymentPensionViewModel(TaxYear(), person, pensionProvider)
      Future.successful(PensionProviderIForm(templateModel).toString)
    }) map { envelopeId =>
      Logger.info("Envelope Id for incorrect employment- " + envelopeId)

      auditable.sendDataEvent(transactionName = IFormConstants.AddPensionProviderAuditTxnName, detail = Map(
        "nino" -> nino.nino,
        "envelope Id" -> envelopeId,
        "start-date" -> pensionProvider.startDate.toString(),
        "pensionNumber" -> pensionProvider.pensionNumber,
        "pensionProviderName" -> pensionProvider.pensionProviderName
      ))

      envelopeId
    }
  }

}

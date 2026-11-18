/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.dms

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.tai.model.templates.PdfSubmission
import uk.gov.hmrc.tai.util.TaiConstants.LondonEuropeTimezone

import java.time.{Instant, ZoneId}

case class DmsSubmissionMetadata(
  customerId: String,
  formId: String,
  source: String,
  classificationType: String,
  businessArea: String,
  timeOfReceipt: Instant
)

object DmsSubmissionMetadata {
  implicit val instantFormat: play.api.libs.json.Format[Instant] = MongoJavatimeFormats.instantFormat
  implicit val format: OFormat[DmsSubmissionMetadata] = Json.format[DmsSubmissionMetadata]

  def from(pdfSubmission: PdfSubmission): DmsSubmissionMetadata =
    DmsSubmissionMetadata(
      customerId = pdfSubmission.customerId,
      formId = pdfSubmission.formId,
      source = pdfSubmission.source,
      classificationType = pdfSubmission.classificationType,
      businessArea = pdfSubmission.businessArea,
      timeOfReceipt = pdfSubmission.hmrcReceivedAt.atZone(ZoneId.of(LondonEuropeTimezone)).toInstant
    )
}

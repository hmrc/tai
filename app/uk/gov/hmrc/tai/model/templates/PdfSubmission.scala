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

package uk.gov.hmrc.tai.model.templates

import uk.gov.hmrc.tai.util.TaiConstants.LondonEuropeTimezone

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneId}
import scala.util.Random

case class PdfSubmission(
  customerId: String, //Needs to be the nino without suffix as DMS doesn't use suffix
  formId: String,
  numberOfPages: Int,
  attachmentCount: Int = 0,
  hmrcReceivedAt: LocalDateTime = LocalDateTime.now(ZoneId.of(LondonEuropeTimezone)), //TODO extract from config
  submissionMark: String = "",
  casKey: String = "",
  businessArea: String = "PSA",
  classificationType: String = "PSA-DFS TES",
  source: String = "TAI",
  target: String = "DMS",
  store: Boolean = true) {
  val xmlCreatedAt: LocalDateTime = LocalDateTime.now()
  val submissionReference: String = {
    val maxSubmissionReferenceLength = 12
    Random.alphanumeric.take(maxSubmissionReferenceLength).mkString
  }
  val instant: Instant = Instant.now()
  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
  val formattedTime: String = formatter.format(instant.atZone(ZoneId.of(LondonEuropeTimezone)).toLocalDateTime)
  val reconciliationId: String = s"$submissionReference-$formattedTime"
  val fileFormat = "pdf"
  val mimeType: String = "application/pdf"
}

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

package uk.gov.hmrc.tai.model.upscan

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.tai.model.dms.DmsSubmissionMetadata

import java.time.Instant

case class UpscanSubmission(
  submissionId: String,
  files: Seq[UpscanUploadedFile] = Seq.empty,
  dmsMetadata: Option[DmsSubmissionMetadata] = None,
  createdAt: Instant = Instant.now()
) {
  def isComplete: Boolean = files.nonEmpty && files.forall(_.status == UpscanFileStatus.Ready)
  def hasFailure: Boolean = files.exists(_.status == UpscanFileStatus.Failed)
}

object UpscanSubmission {
  implicit val instantFormat: play.api.libs.json.Format[Instant] = MongoJavatimeFormats.instantFormat
  implicit val format: OFormat[UpscanSubmission] = Json.format[UpscanSubmission]
}

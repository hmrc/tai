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

case class UpscanUploadDetails(
  fileName: String,
  fileMimeType: String,
  uploadTimestamp: String,
  checksum: String,
  size: Long
)

object UpscanUploadDetails {
  implicit val format: OFormat[UpscanUploadDetails] = Json.format[UpscanUploadDetails]
}

case class UpscanFailureDetails(failureReason: String, message: String)

object UpscanFailureDetails {
  implicit val format: OFormat[UpscanFailureDetails] = Json.format[UpscanFailureDetails]
}

case class UpscanCallback(
  reference: String,
  fileStatus: String,
  downloadUrl: Option[String] = None,
  uploadDetails: Option[UpscanUploadDetails] = None,
  failureDetails: Option[UpscanFailureDetails] = None
)

object UpscanCallback {
  implicit val format: OFormat[UpscanCallback] = Json.format[UpscanCallback]
}

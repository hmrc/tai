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

package uk.gov.hmrc.tai.model.fileupload.formatters

import play.api.libs.json._
import uk.gov.hmrc.tai.model.fileupload.{EnvelopeFile, EnvelopeSummary}

trait FileUploadFormatters {

  val envelopeSummaryReads = new Reads[EnvelopeSummary] {
    override def reads(json: JsValue): JsResult[EnvelopeSummary] =
      if ((json \ "id").validate[String].isSuccess) {
        val envelopeId = (json \ "id").as[String]
        val envelopeStatus = (json \ "status").as[String]

        val files: Seq[JsValue] = (json \ "files").validate[JsArray] match {
          case JsSuccess(arr, _) => arr.value.toSeq
          case _                 => Nil
        }
        val envelopeFiles: Seq[EnvelopeFile] = files map { file: JsValue =>
          val fileId = (file \ "id").as[String]
          val status = (file \ "status").as[String]
          EnvelopeFile(fileId, status)
        }
        JsSuccess(EnvelopeSummary(envelopeId, envelopeStatus, envelopeFiles))
      } else {
        JsError()
      }
  }

}

object FileUploadFormatters extends FileUploadFormatters

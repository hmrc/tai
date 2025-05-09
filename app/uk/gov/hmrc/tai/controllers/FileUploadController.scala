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

package uk.gov.hmrc.tai.controllers

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.model.FileUploadCallback
import uk.gov.hmrc.tai.service.FileUploadService

import scala.concurrent.ExecutionContext

@Singleton
class FileUploadController @Inject() (fileUploadService: FileUploadService, cc: ControllerComponents)(implicit
  ec: ExecutionContext
) extends BackendController(cc) {
  private val logger: Logger = Logger(getClass.getName)
  def fileUploadCallback(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[FileUploadCallback] { fileUploadCallback =>
      fileUploadService
        .fileUploadCallback(fileUploadCallback)
        .recover { case e: Exception =>
          // We must recover exception as file upload service expects an OK response. Log warning here so as not to lose exception
          logger.warn("Exception during callback for file upload: " + e.getMessage, e)
        }
        .map(_ => Ok)
    }
  }
}

/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.tai.util

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import play.api.Application
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder

trait WireMockHelper extends BeforeAndAfterAll with BeforeAndAfterEach {
  this: Suite =>

  protected val server: WireMockServer = new WireMockServer(wireMockConfig().dynamicPort())

  val npsOriginatorId = "npsOriginatorId"

  val desOriginatorId = "desOriginatorId"

  val desPtaOriginatorId = "desPtaOriginatorId"

  implicit lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.des-hod.port"                -> server.port(),
        "microservice.services.des-hod.host"                -> "127.0.0.1",
        "microservice.services.nps-hod.port"                -> server.port(),
        "microservice.services.nps-hod.host"                -> "127.0.0.1",
        "microservice.services.citizen-details.port"        -> server.port(),
        "microservice.services.paye.port"                   -> server.port(),
        "microservice.services.file-upload.port"            -> server.port(),
        "microservice.services.file-upload-frontend.port"   -> server.port(),
        "microservice.services.pdf-generator-service.port"  -> server.port(),
        "microservice.services.nps-hod.originatorId"        -> npsOriginatorId,
        "microservice.services.des-hod.originatorId"        -> desOriginatorId,
        "microservice.services.des-hod.da-pta.originatorId" -> desPtaOriginatorId,
        "auditing.enabled"                                  -> "false"
      )
      .build()

  protected lazy val injector: Injector = app.injector

  override def beforeAll(): Unit = {
    server.start()
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    server.resetAll()
    super.beforeEach()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    server.stop()
  }

}

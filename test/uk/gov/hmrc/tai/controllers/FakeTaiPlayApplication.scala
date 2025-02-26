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

import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.{Args, Status, TestSuite}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

trait FakeTaiPlayApplication extends GuiceOneAppPerSuite with PatienceConfiguration with TestSuite {
  this: TestSuite =>

  val additionalConfiguration: Map[String, Any] = Map[String, Any]("metrics.enabled" -> false)

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure(additionalConfiguration)
      .build()

  abstract override def run(testName: Option[String], args: Args): Status =
    super[GuiceOneAppPerSuite].run(testName, args)
}

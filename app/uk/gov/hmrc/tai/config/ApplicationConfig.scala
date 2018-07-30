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

package uk.gov.hmrc.tai.config

import com.google.inject.{Inject, Singleton}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.config.ServicesConfig

trait HodConfig {
  val baseURL: String
  val environment: String
  val authorization: String
  val originatorId: String
}

abstract class BaseConfig(playEnv: Environment) extends ServicesConfig {
  override val mode = playEnv.mode
}

@Singleton
class PdfConfig @Inject()(val runModeConfiguration: Configuration, playEnv: Environment) extends BaseConfig(playEnv) {
  lazy val baseURL: String = baseUrl("pdf-generator-service")
}

@Singleton
class PayeConfig @Inject()(val runModeConfiguration: Configuration, playEnv: Environment) extends BaseConfig(playEnv) {
  lazy val baseURL: String = baseUrl("paye")
}

@Singleton
class FileUploadConfig @Inject()(val runModeConfiguration: Configuration, playEnv: Environment) extends BaseConfig(playEnv) {
  lazy val baseURL: String = baseUrl("file-upload")
  lazy val frontendBaseURL: String = baseUrl("file-upload-frontend")
  lazy val callbackUrl: String = runModeConfiguration.getString(s"$rootServices.file-upload.callbackUrl").getOrElse("")
}

@Singleton
class CitizenDetailsConfig @Inject()(val runModeConfiguration: Configuration, playEnv: Environment) extends BaseConfig(playEnv) {
  lazy val baseURL: String = baseUrl("citizen-details")
}

@Singleton
class DesConfig @Inject()(val runModeConfiguration: Configuration, playEnv: Environment) extends BaseConfig(playEnv) with HodConfig {
  lazy val baseURL: String = baseUrl("des-hod")
  lazy val environment: String = runModeConfiguration.getString(s"$rootServices.des-hod.env").getOrElse("local")
  lazy val authorization: String = "Bearer " + runModeConfiguration.getString(s"$rootServices.des-hod.authorizationToken").getOrElse("local")
  lazy val originatorId: String = runModeConfiguration.getString(s"$rootServices.des-hod.originatorId").getOrElse("")
}

@Singleton
class NpsConfig @Inject()(val runModeConfiguration: Configuration, playEnv: Environment) extends BaseConfig(playEnv) with HodConfig {
  lazy val optionalPath: Option[String] = runModeConfiguration.getConfig(s"$rootServices.nps-hod").flatMap(_.getString("path"))
  lazy val path: String = optionalPath.fold("")(path => s"$path")

  override lazy val baseURL: String = s"${baseUrl("nps-hod")}$path"
  override lazy val environment = ""
  override lazy val authorization = ""

  override lazy val originatorId: String = runModeConfiguration.getString(s"$rootServices.nps-hod.originatorId").getOrElse("local")
  lazy val autoUpdatePayEnabled: Option[Boolean] = runModeConfiguration.getBoolean("auto-update-pay.enabled")
  lazy val updateSourceEnabled: Option[Boolean] = runModeConfiguration.getBoolean("nps-update-source.enabled")
  lazy val postCalcEnabled: Option[Boolean] = runModeConfiguration.getBoolean("nps-post-calc.enabled")
}

@Singleton
class NpsJsonServiceConfig @Inject()(val runModeConfiguration: Configuration, playEnv: Environment) extends BaseConfig(playEnv) with HodConfig {
  lazy val optionalPath: Option[String] = runModeConfiguration.getConfig(s"$rootServices.nps-json-hod").flatMap(_.getString("path"))
  lazy val path: String = optionalPath.fold("")(path => s"$path")
  lazy val taxCodeURL: String = s"$baseURL/personal-tax-account/tax-code/history/api/v1"

  override lazy val baseURL: String = s"${baseUrl("nps-json-hod")}$path"
  override lazy val environment = ""
  override lazy val authorization = ""
  override lazy val originatorId: String = runModeConfiguration.getString(s"$rootServices.nps-json-hod.originatorId").getOrElse("local")
}

@Singleton
class CyPlusOneConfig @Inject()(val runModeConfiguration: Configuration, playEnv: Environment) extends BaseConfig(playEnv) {
  lazy val cyPlusOneEnabled: Option[Boolean] = runModeConfiguration.getBoolean("cy-plus-one.enabled")
  lazy val cyPlusOneEnableDate: Option[String] = runModeConfiguration.getString("cy-plus-one.startDayMonth")
}

@Singleton
class MongoConfig @Inject()(val runModeConfiguration: Configuration, playEnv: Environment) extends BaseConfig(playEnv) {
  lazy val mongoEnabled: Boolean = runModeConfiguration.getBoolean("cache.isEnabled").getOrElse(false)
  lazy val mongoEncryptionEnabled: Boolean = runModeConfiguration.getBoolean("mongo.encryption.enabled").getOrElse(true)
}

@Singleton
class FeatureTogglesConfig @Inject()(val runModeConfiguration: Configuration, playEnv: Environment) extends BaseConfig(playEnv) {
  lazy val desEnabled: Boolean = runModeConfiguration.getBoolean("tai.des.call").getOrElse(false)
  def desUpdateEnabled: Boolean = runModeConfiguration.getBoolean("tai.des.update.call").getOrElse(false)
  def taxCodeChangeEnabled: Boolean = runModeConfiguration.getBoolean("tai.taxCodeChange.enabled").getOrElse(false)
}
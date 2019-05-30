/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

trait HodConfig {
  val baseURL: String
  val environment: String
  val authorization: String
  val originatorId: String
}


@Singleton
class PdfConfig @Inject()(servicesConfig: ServicesConfig) {
  lazy val baseURL: String = servicesConfig.baseUrl("pdf-generator-service")
}

@Singleton
class PayeConfig @Inject()(servicesConfig: ServicesConfig) {
  lazy val baseURL: String = servicesConfig.baseUrl("paye")
}

@Singleton
class FileUploadConfig @Inject()(val runModeConfiguration: Configuration, servicesConfig: ServicesConfig) extends {
  lazy val baseURL: String = servicesConfig.baseUrl("file-upload")
  lazy val frontendBaseURL: String = servicesConfig.baseUrl("file-upload-frontend")
  lazy val callbackUrl = servicesConfig.getConfString("file-upload.callbackUrl", "")
}

@Singleton
class CitizenDetailsConfig @Inject()(servicesConfig: ServicesConfig) {
  lazy val baseURL: String = servicesConfig.baseUrl("citizen-details")
}

@Singleton
class DesConfig @Inject()(servicesConfig: ServicesConfig) extends HodConfig {
  lazy val baseURL: String = servicesConfig.baseUrl("des-hod")
  lazy val environment = servicesConfig.getConfString("des-hod.env", "local")
  lazy val authorization: String = "Bearer " + servicesConfig.getConfString("des-hod.authorizationToken", "local")
  lazy val daPtaOriginatorId: String = servicesConfig.getConfString("des-hod.da-pta.originatorId", "")
  lazy val originatorId: String = servicesConfig.getConfString("des-hod.originatorId", "")
}

@Singleton
class NpsConfig @Inject()(val runModeConfiguration: Configuration, servicesConfig: ServicesConfig) extends HodConfig {
  private lazy val path: String = servicesConfig.getConfString("nps-hod.path", "")

  override lazy val baseURL: String = s"${servicesConfig.baseUrl("nps-hod")}$path"
  override lazy val environment = ""
  override lazy val authorization = ""
  override lazy val originatorId: String = servicesConfig.getConfString("nps-hod.originatorId", "local")
  lazy val autoUpdatePayEnabled: Option[Boolean] = runModeConfiguration.getBoolean("auto-update-pay.enabled")
  lazy val updateSourceEnabled: Option[Boolean] = runModeConfiguration.getBoolean("nps-update-source.enabled")
  lazy val postCalcEnabled: Option[Boolean] = runModeConfiguration.getBoolean("nps-post-calc.enabled")
}

@Singleton
class CyPlusOneConfig @Inject()(val runModeConfiguration: Configuration) {
  lazy val cyPlusOneEnabled: Option[Boolean] = runModeConfiguration.getBoolean("cy-plus-one.enabled")
  lazy val cyPlusOneEnableDate: Option[String] = runModeConfiguration.getString("cy-plus-one.startDayMonth")
}

@Singleton
class MongoConfig @Inject()(val runModeConfiguration: Configuration) {
  lazy val mongoEnabled: Boolean = runModeConfiguration.getBoolean("cache.isEnabled").getOrElse(false)
  lazy val mongoEncryptionEnabled: Boolean = runModeConfiguration.getBoolean("mongo.encryption.enabled").getOrElse(true)
}

@Singleton
class FeatureTogglesConfig @Inject()(val runModeConfiguration: Configuration) {
  def desEnabled: Boolean = runModeConfiguration.getBoolean("tai.des.call").getOrElse(false)

  def desUpdateEnabled: Boolean = runModeConfiguration.getBoolean("tai.des.update.call").getOrElse(false)

  def confirmedAPIEnabled: Boolean = runModeConfiguration.getBoolean("tai.confirmedAPI.enabled").getOrElse(false)
}

@Singleton
class CacheMetricsConfig @Inject()(val runModeConfiguration: Configuration) {
  def cacheMetricsEnabled: Boolean = runModeConfiguration.getBoolean("tai.cacheMetrics.enabled").getOrElse(false)
}
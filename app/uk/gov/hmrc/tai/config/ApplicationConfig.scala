/*
 * Copyright 2021 HM Revenue & Customs
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

abstract class BaseConfig

@Singleton
class PdfConfig @Inject()(servicesConfig: ServicesConfig) extends BaseConfig {
  lazy val baseURL: String = servicesConfig.baseUrl("pdf-generator-service")
}

@Singleton
class PayeConfig @Inject()(servicesConfig: ServicesConfig) extends BaseConfig {
  lazy val baseURL: String = servicesConfig.baseUrl("paye")
}

@Singleton
class FileUploadConfig @Inject()(servicesConfig: ServicesConfig) extends BaseConfig {
  lazy val baseURL: String = servicesConfig.baseUrl("file-upload")
  lazy val frontendBaseURL: String = servicesConfig.baseUrl("file-upload-frontend")
  lazy val callbackUrl: String = servicesConfig.getConfString("file-upload.callbackUrl", "")
  lazy val intervalMs: Int = servicesConfig.getConfInt("file-upload.intervalMs", 20)
  lazy val maxAttempts: Int = servicesConfig.getConfInt("file-upload.maxAttempts", 5)
}

@Singleton
class CitizenDetailsConfig @Inject()(servicesConfig: ServicesConfig) extends BaseConfig {
  lazy val baseURL: String = servicesConfig.baseUrl("citizen-details")
}

@Singleton
class DesConfig @Inject()(servicesConfig: ServicesConfig) extends BaseConfig with HodConfig {
  lazy val baseURL: String = servicesConfig.baseUrl("des-hod")
  lazy val environment: String = servicesConfig.getConfString("des-hod.env", "local")
  lazy val authorization: String = "Bearer " + servicesConfig.getConfString("des-hod.authorizationToken", "local")
  lazy val daPtaOriginatorId: String = servicesConfig.getConfString("des-hod.da-pta.originatorId", "")
  lazy val originatorId: String = servicesConfig.getConfString("des-hod.originatorId", "")
}

@Singleton
class NpsConfig @Inject()(val runModeConfiguration: Configuration, servicesConfig: ServicesConfig)
    extends BaseConfig with HodConfig {
  lazy val path: String = servicesConfig.getConfString("nps-hod.path", "")

  override lazy val baseURL: String = s"${servicesConfig.baseUrl("nps-hod")}$path"
  override lazy val environment = ""
  override lazy val authorization = ""
  override lazy val originatorId: String = servicesConfig.getConfString("nps-hod.originatorId", "local")
  lazy val autoUpdatePayEnabled: Option[Boolean] = runModeConfiguration.getOptional[Boolean]("auto-update-pay.enabled")
  lazy val updateSourceEnabled: Option[Boolean] = runModeConfiguration.getOptional[Boolean]("nps-update-source.enabled")
  lazy val postCalcEnabled: Option[Boolean] = runModeConfiguration.getOptional[Boolean]("nps-post-calc.enabled")
}

@Singleton
class CyPlusOneConfig @Inject()(val runModeConfiguration: Configuration) extends BaseConfig {
  lazy val cyPlusOneEnabled: Option[Boolean] = runModeConfiguration.getOptional[Boolean]("cy-plus-one.enabled")
  lazy val cyPlusOneEnableDate: Option[String] = runModeConfiguration.getOptional[String]("cy-plus-one.startDayMonth")
}

@Singleton
class MongoConfig @Inject()(val runModeConfiguration: Configuration) extends BaseConfig {
  lazy val mongoEnabled: Boolean = runModeConfiguration.getOptional[Boolean]("cache.isEnabled").getOrElse(false)
  lazy val mongoEncryptionEnabled: Boolean =
    runModeConfiguration.getOptional[Boolean]("mongo.encryption.enabled").getOrElse(true)
}

@Singleton
class FeatureTogglesConfig @Inject()(val runModeConfiguration: Configuration) extends BaseConfig {
  def desEnabled: Boolean = runModeConfiguration.getOptional[Boolean]("tai.des.call").getOrElse(false)
  def desUpdateEnabled: Boolean = runModeConfiguration.getOptional[Boolean]("tai.des.update.call").getOrElse(false)
  def confirmedAPIEnabled: Boolean =
    runModeConfiguration.getOptional[Boolean]("tai.confirmedAPI.enabled").getOrElse(false)
}

@Singleton
class RtiToggleConfig @Inject()(val runModeConfiguration: Configuration) extends BaseConfig {
  def rtiEnabled: Boolean = runModeConfiguration.getOptional[Boolean]("tai.rti.call.enabled").getOrElse(true)
}

@Singleton
class CacheMetricsConfig @Inject()(val runModeConfiguration: Configuration) extends BaseConfig {
  def cacheMetricsEnabled: Boolean =
    runModeConfiguration.getOptional[Boolean]("tai.cacheMetrics.enabled").getOrElse(false)
}

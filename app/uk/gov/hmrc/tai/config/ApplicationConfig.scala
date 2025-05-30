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
class PdfConfig @Inject() (servicesConfig: ServicesConfig) extends BaseConfig {
  lazy val baseURL: String = servicesConfig.baseUrl("pdf-generator-service")
}

@Singleton
class PayeConfig @Inject() (servicesConfig: ServicesConfig) extends BaseConfig {
  lazy val baseURL: String = servicesConfig.baseUrl("paye")
}

@Singleton
class FileUploadConfig @Inject() (servicesConfig: ServicesConfig) extends BaseConfig {
  lazy val baseURL: String = servicesConfig.baseUrl("file-upload")
  lazy val frontendBaseURL: String = servicesConfig.baseUrl("file-upload-frontend")
  lazy val callbackUrl: String = servicesConfig.getConfString("file-upload.callbackUrl", "")
  lazy val intervalMs: Int = servicesConfig.getConfInt("file-upload.intervalMs", 20)
  lazy val maxAttempts: Int = servicesConfig.getConfInt("file-upload.maxAttempts", 5)
}

@Singleton
class CitizenDetailsConfig @Inject() (servicesConfig: ServicesConfig) extends BaseConfig {
  lazy val baseURL: String = servicesConfig.baseUrl("citizen-details")
}

@Singleton
class DesConfig @Inject() (servicesConfig: ServicesConfig) extends BaseConfig with HodConfig {
  val baseURL: String = servicesConfig.baseUrl("des-hod")
  val environment: String = servicesConfig.getConfString("des-hod.env", "local")
  val authorization: String = "Bearer " + servicesConfig.getConfString("des-hod.authorizationToken", "local")
  lazy val daPtaOriginatorId: String = servicesConfig.getConfString("des-hod.da-pta.originatorId", "")
  val originatorId: String = servicesConfig.getConfString("des-hod.originatorId", "")
  lazy val timeoutInMilliseconds: Int = servicesConfig.getConfInt("des-hod.timeoutInMilliseconds", 1000)
}

@Singleton
class IfConfig @Inject() (servicesConfig: ServicesConfig) extends BaseConfig with HodConfig {
  val baseURL: String = servicesConfig.baseUrl("if-hod")
  val environment: String = servicesConfig.getConfString("if-hod.env", "local")
  val authorization: String = "Bearer " + servicesConfig.getConfString("if-hod.authorizationToken", "local")
  lazy val daPtaOriginatorId: String = servicesConfig.getConfString("if-hod.da-pta.originatorId", "")
  val originatorId: String = servicesConfig.getConfString("if-hod.originatorId", "")
  lazy val timeoutInMilliseconds: Int = servicesConfig.getConfInt("if-hod.timeoutInMilliseconds", 1000)
}

@Singleton
class NpsConfig @Inject() (servicesConfig: ServicesConfig) extends BaseConfig with HodConfig {
  lazy val path: String = servicesConfig.getConfString("nps-hod.path", "")

  override val baseURL: String = s"${servicesConfig.baseUrl("nps-hod")}$path"
  override val environment = ""
  override val authorization = ""
  override val originatorId: String = servicesConfig.getConfString("nps-hod.originatorId", "local")
}

@Singleton
class HipConfig @Inject() (servicesConfig: ServicesConfig) extends BaseConfig with HodConfig {
  lazy val path: String = servicesConfig.getConfString("hip-hod.path", "")

  override val baseURL: String = s"${servicesConfig.baseUrl("hip-hod")}$path"
  override val environment = ""
  override val authorization = ""
  override val originatorId: String = servicesConfig.getConfString("hip-hod.originatorId", "local")
  lazy val clientId: String = servicesConfig.getConfString("hip-hod.clientId", "local")
  lazy val clientSecret: String = servicesConfig.getConfString("hip-hod.clientSecret", "local")
}

@Singleton
class MongoConfig @Inject() (val runModeConfiguration: Configuration) extends BaseConfig {
  lazy val mongoEnabled: Boolean = runModeConfiguration.getOptional[Boolean]("cache.isEnabled").getOrElse(false)
  lazy val mongoEncryptionEnabled: Boolean =
    runModeConfiguration.getOptional[Boolean]("mongo.encryption.enabled").getOrElse(true)
  lazy val mongoTTL: Int = runModeConfiguration.getOptional[Int]("tai.cache.expiryInSeconds").getOrElse(900)
  lazy val mongoLockTTL: Int = runModeConfiguration.getOptional[Int]("mongo.lock.expiryInMilliseconds").getOrElse(1200)
  lazy val mongoTTLUpdateIncome: Int =
    runModeConfiguration.getOptional[Int]("tai.cache.updateIncome.expiryInSeconds").getOrElse(3600 * 48)
}

@Singleton
class RtiConfig @Inject() extends BaseConfig {
  val hodRetryDelayInMillis: Int = 200
  val hodRetryMaximum: Int = 20
}
@Singleton
class CacheConfig @Inject() (val runModeConfiguration: Configuration) extends BaseConfig {
  lazy val cacheErrorInSecondsTTL: Long =
    runModeConfiguration.getOptional[Long]("tai.cache.upstream-errors.expiryInSeconds").getOrElse(0L)
}

@Singleton
class CacheMetricsConfig @Inject() (val runModeConfiguration: Configuration) extends BaseConfig {
  def cacheMetricsEnabled: Boolean =
    runModeConfiguration.getOptional[Boolean]("tai.cacheMetrics.enabled").getOrElse(false)
}

@Singleton
class PertaxConfig @Inject() (servicesConfig: ServicesConfig) extends BaseConfig {
  val pertaxUrl: String = servicesConfig.baseUrl("pertax")
}

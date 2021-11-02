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

package uk.gov.hmrc.tai.metrics

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer.Context
import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.enums.APITypes.APITypes

@Singleton
class Metrics @Inject()(metrics: com.kenshoo.play.metrics.Metrics) {

  private val registry: MetricRegistry = metrics.defaultRegistry

  val SuccessCounterSuffix = "-success-counter"
  val FailureCounterSuffix = "-failed-counter"
  val CacheHitCounter = "tai-cache-hit-counter"
  val CacheMissCounter = "tai-cache-miss-counter"
  val TimerSuffix = "-timer"

  val metricDescriptions = Map(
    APITypes.NpsPersonAPI                     -> "nps-person",
    APITypes.NpsTaxAccountAPI                 -> "nps-tax-account",
    APITypes.NpsEmploymentAPI                 -> "nps-employment",
    APITypes.RTIAPI                           -> "nps-rti",
    APITypes.NpsIabdAllAPI                    -> "nps-iabd-all",
    APITypes.NpsIabdSpecificAPI               -> "nps-iabd-specific",
    APITypes.NpsIabdUpdateEstPayManualAPI     -> "nps-iabd-estPay-manual",
    APITypes.NpsIabdUpdateEstPayAutoAPI       -> "nps-iabd-estPay-auto",
    APITypes.DesTaxAccountAPI                 -> "des-tax-account",
    APITypes.DesIabdAllAPI                    -> "des-iabd-all",
    APITypes.DesIabdSpecificAPI               -> "des-iabd-specific",
    APITypes.DesIabdUpdateEstPayManualAPI     -> "des-iabd-estPay-manual",
    APITypes.DesIabdUpdateEstPayAutoAPI       -> "des-iabd-estPay-auto",
    APITypes.DesIabdUpdateFlatRateExpensesAPI -> "des-iabd-flat-rate-expenses-update",
    APITypes.DesIabdGetFlatRateExpensesAPI    -> "des-iabd-flat-rate-expenses-get",
    APITypes.DesIabdUpdateEmployeeExpensesAPI -> "des-iabd-flat-rate-expenses-update",
    APITypes.DesIabdGetEmployeeExpensesAPI    -> "des-iabd-flat-rate-expenses-get",
    APITypes.PdfServiceAPI                    -> "pdf-service",
    APITypes.CompanyCarAPI                    -> "company-car",
    APITypes.FusCreateEnvelope                -> "create-envelope",
    APITypes.FusUploadFile                    -> "file-upload",
    APITypes.FusCloseEnvelope                 -> "close-envelope",
    APITypes.BbsiAPI                          -> "bbsi",
    APITypes.TaxCodeChangeAPI                 -> "tax-code-change",
    APITypes.TaxAccountHistoryAPI             -> "tax-account-history"
  )

  def startTimer(api: APITypes): Context = registry.timer(metricDescriptions(api) + TimerSuffix).time()
  def incrementSuccessCounter(api: APITypes): Unit =
    registry.counter(metricDescriptions(api) + SuccessCounterSuffix).inc()
  def incrementFailedCounter(api: APITypes): Unit =
    registry.counter(metricDescriptions(api) + FailureCounterSuffix).inc()

  def incrementCacheHitCounter(): Unit = registry.counter(CacheHitCounter).inc()
  def incrementCacheMissCounter(): Unit = registry.counter(CacheMissCounter).inc()
}

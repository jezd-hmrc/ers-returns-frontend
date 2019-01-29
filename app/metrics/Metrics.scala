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

package metrics

import java.util.concurrent.TimeUnit

import com.codahale.metrics.MetricRegistry
import uk.gov.hmrc.play.graphite.MicroserviceMetrics

trait Metrics {
  def csvValidationTimer(diff: Long, unit: TimeUnit): Unit

  def odsValidationTimer(diff: Long, unit: TimeUnit): Unit

  def fileUploadSize(n: Long): Unit

  def cacheTimeStore(diff: Long, unit: TimeUnit): Unit

  def cacheTimeFetch(diff: Long, unit: TimeUnit): Unit

  def accessThresholdGranted(): Unit

  def accessThresholdDenied(): Unit

  def ersConnector(diff: Long, unit: TimeUnit): Unit

  def submitReturnToBackend(diff: Long, unit: TimeUnit): Unit
}

object Metrics extends Metrics with MicroserviceMetrics {
  val registry: MetricRegistry = metrics.defaultRegistry

  override def csvValidationTimer(diff: Long, unit: TimeUnit): Unit = registry.timer("csv-validation-timer").update(diff, unit)

  override def odsValidationTimer(diff: Long, unit: TimeUnit): Unit = registry.timer("ods-validation-timer").update(diff, unit)

  override def fileUploadSize(n: Long): Unit = registry.histogram("file-upload-size").update(n)

  override def cacheTimeStore(diff: Long, unit: TimeUnit): Unit = registry.timer("store-cache-timer").update(diff, unit)

  override def cacheTimeFetch(diff: Long, unit: TimeUnit): Unit = registry.timer("fetch-cache-timer").update(diff, unit)

  override def accessThresholdGranted(): Unit = registry.counter("access-threshold-granted").inc()

  override def accessThresholdDenied(): Unit = registry.counter("access-threshold-denied").inc()

  override def ersConnector(diff: Long, unit: TimeUnit): Unit = registry.timer("validator-connector").update(diff, unit)

  override def submitReturnToBackend(diff: Long, unit: TimeUnit): Unit = registry.timer("submit-to-returns-connector").update(diff, unit)
}

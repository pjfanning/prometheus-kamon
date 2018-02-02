/* =========================================================================================
 * Copyright © 2018 PJ Fanning
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */
package com.example.prometheus.kamon

import scala.collection.concurrent.TrieMap

import io.prometheus.client.{Collector, CollectorRegistry, Gauge, Histogram}

object PrometheusMetricRegistry {

  private val collectorRegistry = CollectorRegistry.defaultRegistry
  private val gauges = TrieMap[String, Gauge]()
  private val histograms = TrieMap[String, MetricDistributionCollector]()

  def getGauge(name: String, labelNames: Seq[String], metricType: Collector.Type): Gauge = {
    def createGauge = {
      def typeText = if(metricType == Collector.Type.COUNTER) "counter" else "gauge"
      Gauge.build().name(name).help(typeText).labelNames(labelNames: _*).register(collectorRegistry)
    }
    gauges.getOrElseUpdate(name, createGauge)
  }

  def getHistogram(name: String, labelNames: Seq[String]): MetricDistributionCollector = {
    def createHistogram = new MetricDistributionCollector(name, labelNames, collectorRegistry)
    histograms.getOrElseUpdate(name, createHistogram)
  }
}

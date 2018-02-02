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

import java.util

import io.prometheus.client.Collector.MetricFamilySamples
import io.prometheus.client.{Collector, CollectorRegistry}

class MetricDistributionCollector(name: String, labelNames: Seq[String], help: String,
                                  collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry) extends Collector {
  this.register(collectorRegistry)

  private var samples: util.List[MetricFamilySamples.Sample] = util.Collections.emptyList()

  def setSamples(samples: util.List[MetricFamilySamples.Sample]): Unit = {
    this.samples = samples
  }

  override def collect(): util.List[MetricFamilySamples] = {
    val mfs = new Collector.MetricFamilySamples(name, Collector.Type.HISTOGRAM, help, samples)
    util.Collections.singletonList(mfs)
  }
}
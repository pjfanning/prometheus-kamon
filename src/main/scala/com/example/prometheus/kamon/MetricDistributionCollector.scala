package com.example.prometheus.kamon

import java.util

import io.prometheus.client.Collector.MetricFamilySamples
import io.prometheus.client.{Collector, CollectorRegistry}

class MetricDistributionCollector(name: String, labelNames: Seq[String],
                                  collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry) extends Collector {
  this.register(collectorRegistry)

  private var samples: util.List[MetricFamilySamples.Sample] = util.Collections.emptyList()

  def setSamples(samples: util.List[MetricFamilySamples.Sample]): Unit = {
    this.samples = samples
  }

  override def collect(): util.List[MetricFamilySamples] = {
    val mfs = new Collector.MetricFamilySamples(name, Collector.Type.HISTOGRAM, s"$name histogram", samples)
    util.Collections.singletonList(mfs)
  }
}
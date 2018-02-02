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
      Gauge.build().name(name).help(s"$name $typeText").labelNames(labelNames: _*).register(collectorRegistry)
    }
    gauges.getOrElseUpdate(name, createGauge)
  }

  def getHistogram(name: String, labelNames: Seq[String]): MetricDistributionCollector = {
    def createHistogram = new MetricDistributionCollector(name, labelNames, collectorRegistry)
    histograms.getOrElseUpdate(name, createHistogram)
  }
}

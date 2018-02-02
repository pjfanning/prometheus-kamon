/* =========================================================================================
 * Copyright © 2018 PJ Fanning
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

import java.time.Duration
import java.util

import scala.collection.JavaConverters._

import com.typesafe.config.Config

import io.prometheus.client.Collector
import io.prometheus.client.Collector.{MetricFamilySamples, Type}
import kamon.{Kamon, MetricReporter, Tags}
import kamon.metric.MeasurementUnit.Dimension.{Information, Time}
import kamon.metric.MeasurementUnit.{information, none, time}
import kamon.metric._

// based on kamon-prometheus kamon.prometheus.PrometheusReporter
class PrometheusJavaReporter extends MetricReporter {
  private val snapshotAccumulator = new PeriodSnapshotAccumulator(Duration.ofDays(365 * 5), Duration.ZERO)

  override def start(): Unit = {}
  override def stop(): Unit = {}
  override def reconfigure(config: Config): Unit = {}

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    snapshotAccumulator.add(snapshot)
    val currentData = snapshotAccumulator.peek()

    convertCounters(currentData.metrics.counters)
    convertGauges(currentData.metrics.gauges)
    convertHistograms(currentData.metrics.histograms)
    convertHistograms(currentData.metrics.rangeSamplers)
  }

  private def convertCounters(counters: Seq[MetricValue]): Unit = {
    counters.groupBy(_.name).foreach(convertValueMetric(Type.COUNTER, alwaysIncreasing = true))
  }

  private def convertGauges(counters: Seq[MetricValue]): Unit = {
    counters.groupBy(_.name).foreach(convertValueMetric(Type.COUNTER, alwaysIncreasing = true))
  }

  private def convertHistograms(histograms: Seq[MetricDistribution]): Unit = {
    histograms.groupBy(_.name).foreach(convertDistributionMetric)
  }

  private def charOrUnderscore(char: Char): Char =
    if(char.isLetterOrDigit || char == '_') char else '_'

  private def normalizeMetricName(metricName: String, unit: MeasurementUnit): String = {
    val normalizedMetricName = metricName.map(charOrUnderscore)

    unit.dimension match  {
      case Time         => normalizedMetricName + "_seconds"
      case Information  => normalizedMetricName + "_bytes"
      case _            => normalizedMetricName
    }
  }

  private def normalizeUnit(unit: MeasurementUnit): String = unit.dimension match  {
    case Time         => "seconds"
    case Information  => "bytes"
    case _            => ""
  }

  private def scale(value: Long, unit: MeasurementUnit): Double = unit.dimension match {
    case Time         if unit.magnitude != time.seconds.magnitude       => MeasurementUnit.scale(value, unit, time.seconds)
    case Information  if unit.magnitude != information.bytes.magnitude  => MeasurementUnit.scale(value, unit, information.bytes)
    case _ => value
  }

  private def convertValueMetric(metricType: Collector.Type, alwaysIncreasing: Boolean)(group: (String, Seq[MetricValue])): Unit = {
    val (metricName, snapshots) = group
    val unit = snapshots.headOption.map(_.unit).getOrElse(none)
    val normalizedMetricName = normalizeMetricName(metricName, unit) + {
      if (alwaysIncreasing) "_total" else ""
    }
    snapshots.foreach { metric =>
      val (labelNames, labelValues) = metric.tags.unzip
      val metricValue = scale(metric.value, metric.unit).doubleValue
      val gauge = PrometheusMetricRegistry.getGauge(normalizedMetricName, labelNames.toSeq, metricType)
      gauge.labels(labelValues.toStream: _*).set(metricValue)
    }
  }

  private def convertDistributionMetric(group: (String, Seq[MetricDistribution])): Unit = {
    val (metricName, snapshots) = group
    val unit = snapshots.headOption.map(_.unit).getOrElse(none)
    val normalizedMetricName = normalizeMetricName(metricName, unit)

    snapshots.foreach(metric => {
      if(metric.distribution.count > 0) {
        val samples = new util.ArrayList[MetricFamilySamples.Sample]()
        val (labelNames, labelValues) = metric.tags.unzip
        val labelNamesList = labelNames.toList
        val labelValuesList = labelValues.toList.asJava
        convertHistogramBuckets(normalizedMetricName, metric.tags, samples, metric)
        val sum = scale(metric.distribution.sum, metric.unit)
        samples.add(
          new MetricFamilySamples.Sample(s"${normalizedMetricName}_count", labelNamesList.asJava,
            labelValuesList, metric.distribution.count))
        samples.add(
          new MetricFamilySamples.Sample(s"${normalizedMetricName}_sum", labelNamesList.asJava,
            labelValuesList, sum))
        val collector = PrometheusMetricRegistry.getHistogram(normalizedMetricName, labelNamesList, normalizeUnit(unit))
        collector.setSamples(samples)
      }
    })

  }

  private def convertHistogramBuckets(name: String, tags: Tags,
                                      samples:  util.List[MetricFamilySamples.Sample], metric: MetricDistribution): Unit = {
    val prometheusConfig = readConfiguration(Kamon.config())
    val configuredBuckets = (metric.unit.dimension match {
      case Time         => prometheusConfig.timeBuckets
      case Information  => prometheusConfig.informationBuckets
      case _            => prometheusConfig.defaultBuckets
    }).iterator

    val distributionBuckets = metric.distribution.bucketsIterator
    var currentDistributionBucket = distributionBuckets.next()
    var currentDistributionBucketValue = scale(currentDistributionBucket.value, metric.unit)
    var inBucketCount = 0L
    var leftOver = currentDistributionBucket.frequency

    val sampleName = s"${name}_bucket"

    configuredBuckets.foreach { configuredBucket =>
      val bucketTags = tags + ("le" -> String.valueOf(configuredBucket))

      if(currentDistributionBucketValue <= configuredBucket) {
        inBucketCount += leftOver
        leftOver = 0

        while (distributionBuckets.hasNext && currentDistributionBucketValue <= configuredBucket ) {
          currentDistributionBucket = distributionBuckets.next()
          currentDistributionBucketValue = scale(currentDistributionBucket.value, metric.unit)

          if (currentDistributionBucketValue <= configuredBucket) {
            inBucketCount += currentDistributionBucket.frequency
          }
          else
            leftOver = currentDistributionBucket.frequency
        }
      }

      val (labelNames, labelValues) = bucketTags.unzip
      samples.add(
        new MetricFamilySamples.Sample(sampleName, labelNames.toList.asJava, labelValues.toList.asJava, inBucketCount.doubleValue))
    }

    while(distributionBuckets.hasNext) {
      leftOver += distributionBuckets.next().frequency
    }

    val (labelNames, labelValues) = (tags + ("le" -> "+Inf")).unzip
    samples.add(
      new MetricFamilySamples.Sample(sampleName, labelNames.toList.asJava,
        labelValues.toList.asJava, (leftOver + inBucketCount).doubleValue))
  }

  private def readConfiguration(config: Config): PrometheusJavaReporter.Configuration = {
    val prometheusConfig = config.getConfig("prometheus.kamon")

    PrometheusJavaReporter.Configuration(
      defaultBuckets = prometheusConfig.getDoubleList("buckets.default-buckets").asScala,
      timeBuckets = prometheusConfig.getDoubleList("buckets.time-buckets").asScala,
      informationBuckets = prometheusConfig.getDoubleList("buckets.information-buckets").asScala
    )
  }
}

object PrometheusJavaReporter {
  case class Configuration(defaultBuckets: Seq[java.lang.Double], timeBuckets: Seq[java.lang.Double],
                           informationBuckets: Seq[java.lang.Double])
}

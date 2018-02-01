package com.example.prometheus.kamon

import java.time.Duration

import com.typesafe.config.Config

import io.prometheus.client.Collector
import io.prometheus.client.Collector.Type
import kamon.MetricReporter
import kamon.metric.MeasurementUnit.Dimension.{Information, Time}
import kamon.metric.MeasurementUnit.{information, none, time}
import kamon.metric.{MeasurementUnit, MetricValue, PeriodSnapshot, PeriodSnapshotAccumulator}

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
  }

  private def convertCounters(counters: Seq[MetricValue]): Unit = {
    counters.groupBy(_.name).foreach(convertValueMetric(Type.COUNTER, alwaysIncreasing = true))
  }

  private def convertGauges(counters: Seq[MetricValue]): Unit = {
    counters.groupBy(_.name).foreach(convertValueMetric(Type.COUNTER, alwaysIncreasing = true))
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
}

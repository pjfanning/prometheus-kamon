package com.example.prometheus.kamon

import java.io.StringWriter

import scala.util.control.NonFatal
import scala.concurrent.duration.DurationInt

import org.slf4j.LoggerFactory

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import kamon.Kamon

/**
  * Demo showing how to take Kamon metrics and use PrometheusJavaReporter to have the metric data copied
  * to the Prometheus Java Client CollectorRegistry.defaultRegistry
  *
  * kamon.metric.tick-interval defaults to 60 seconds (Kamon will report its values based on this interval)
  */
object PrometheusKamonTest extends App {
  val logger = LoggerFactory.getLogger(PrometheusKamonTest.getClass)
  Kamon.addReporter(new PrometheusJavaReporter)
  sys.addShutdownHook(Kamon.stopAllReporters())

  val counter = Kamon.counter("kamon-test-counter")
  counter.increment(10)
  val gauge = Kamon.gauge("kamon-test-gauge")
  gauge.set(100)
  val hist = Kamon.histogram("kamon-test-histogram")
  hist.record(1000)
  hist.record(1000 * 1000)

  val executor = new ScheduledExecutor(1)
  executor.scheduleAtFixedRate(logPrometheusMetrics)(10.seconds)

  Thread.sleep(5.minutes.toMillis)

  def logPrometheusMetrics: Unit = {
    val sw = new StringWriter()
    try {
      TextFormat.write004(sw, CollectorRegistry.defaultRegistry.metricFamilySamples())
      logger.info(s"Prometheus Metrics:\n$sw")
    } catch {
      case NonFatal(t) => logger.warn("logPrometheusMetrics failed", t)
    }
  }
}

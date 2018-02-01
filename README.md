# prometheus-kamon
Test Kamon Reporter that reports kamon metrics into Prometheus CollectorRegistry.

The io.kamon:kamon-prometheus library has a Reporter that exposes the Kamon metrics in Prometheus text format on a HTTP server.

This code borrows heavily from kamon-prometheus but adds the metrics to Prometheus Java Client CollectorRegistry.
The idea is that you might have existing Prometheus metrics and want to collect Kamon metrics as if they were Prometheus metrics
(and be able to report the two types of metric together).

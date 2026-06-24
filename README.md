# jfr-monitoring

> **Work in progress.** APIs and configuration properties may change.

A Spring Boot auto-configuration library that captures [Java Flight Recorder (JFR)](https://openjdk.org/jeps/328) events and exports them in [pprof](https://github.com/google/pprof) format for analysis in profiling and observability systems such as [Grafana Alloy](https://grafana.com/docs/alloy/).

## How it works

1. A JFR recording is started on application startup and sampled on a configurable interval.
2. Recorded events (both built-in JVM events and custom application events) are converted to the pprof protobuf format.
3. The pprof payload is handed to one or more `JfrPprofHandler` beans for export (e.g. written to disk, pushed to a remote endpoint).

## Getting started

### 1. Add the dependency

The library is not yet published to Maven Central. Build and install it locally:

```bash
./gradlew build
```

### 2. Enable in your application

```yaml
jfr-monitoring:
  enabled: true
  output-endpoint: /tmp/profiles   # directory where .pb.gz files are written
  recording-interval-seconds: 10   # how often a new pprof snapshot is flushed
```

The library activates only when `jfr-monitoring.enabled=true`, so it is safe to leave the dependency on the classpath in all environments.

### 3. Emit custom JFR events

Extend `jdk.jfr.Event` and annotate your fields:

```java
@Name("com.example.OrderProcessed")
@Label("Order Processed")
@Category({"Application", "Orders"})
public class OrderProcessedEvent extends Event {

    @Label("Order ID")
    String orderId;

    @Label("Amount")
    double amount;
}
```

Commit the event anywhere in your code:

```java
var event = new OrderProcessedEvent("order-42", 99.95);
event.commit();
```

Custom field values are attached as pprof labels on each sample and visible in the profiler UI.

## Configuration reference

| Property | Default | Description |
|---|---|---|
| `jfr-monitoring.enabled` | `false` | Enable/disable the library |
| `jfr-monitoring.output-endpoint` | `.` | Directory to write pprof files |
| `jfr-monitoring.recording-interval-seconds` | `10` | Flush interval in seconds |

## Analysing profiles

With `go tool pprof` installed:

```bash
go tool pprof -http=:8080 jfr-<timestamp>.pb.gz
```

Opens a browser UI with flame graphs, call graphs, and per-sample labels (including your custom event fields).

## Extending the export pipeline

Implement `JfrPprofHandler` and register it as a Spring bean to send pprof payloads anywhere:

```java
@Bean
public JfrPprofHandler grafanaAlloyHandler() {
    return pprof -> { /* push to Grafana Alloy or any remote endpoint */ };
}
```

Multiple handlers are supported; all receive every snapshot.

## Requirements

- Java 21+
- Spring Boot 3.x / 4.x

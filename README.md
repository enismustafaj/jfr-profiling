# jfr-profiling

> **Work in progress, not production ready.** APIs and configuration properties may change. See [Roadmap](#roadmap) for known gaps.

A Spring Boot auto-configuration library that captures per-HTTP-request [Java Flight Recorder (JFR)](https://openjdk.org/jeps/328) CPU stack samples and exports them in [pprof](https://github.com/google/pprof) format for analysis in profiling and observability systems such as [Grafana Alloy](https://grafana.com/docs/alloy/).

## How it works

1. A `HandlerInterceptor` starts a short-lived JFR recording for each incoming HTTP request. On Linux with JDK 25+, it uses [`jdk.CPUTimeSample`](https://openjdk.org/jeps/509) — a per-thread CPU-time signal sampler that isn't biased toward safepoint-heavy code. Elsewhere it falls back to `jdk.ExecutionSample`, which *is* safepoint-biased (tight, allocation-free code can go under-sampled).
2. When the request completes, the recording is handed off to a `ProfileExporter`, which runs on a virtual thread (bounded concurrency — excess exports are dropped rather than queued) to dump the recording, filter it down to the request's thread, convert it to a pprof profile, and export it.
3. The pprof payload is routed to every registered `JfrPprofHandler` bean (e.g. written to disk).

Each HTTP request produces its own pprof profile, so profiling overhead is scoped to the request rather than running continuously.

## Getting started

### 1. Add the dependency

The library is available on [Maven Central](https://central.sonatype.com/artifact/io.github.enismustafaj/jfr-profiling) as `io.github.enismustafaj:jfr-profiling`.

Gradle:

```groovy
implementation 'io.github.enismustafaj:jfr-profiling:0.0.2'
```

Maven:

```xml
<dependency>
    <groupId>io.github.enismustafaj</groupId>
    <artifactId>jfr-profiling</artifactId>
    <version>0.0.2</version>
</dependency>
```

### 2. Enable in your application

```yaml
jfr-profiling:
  enabled: true
  output-endpoint: /tmp/profiles   # directory where .pb.gz files are written
```

The library activates only when `jfr-profiling.enabled=true`, so it is safe to leave the dependency on the classpath in all environments.

## Configuration reference

| Property | Default | Description |
|---|---|---|
| `jfr-profiling.enabled` | `false` | Enable/disable the library |
| `jfr-profiling.output-endpoint` | `.` | Directory to write pprof files |

## Analysing profiles

With `go tool pprof` installed:

```bash
go tool pprof -http=:8080 jfr-<timestamp>.pb.gz
```

Opens a browser UI with flame graphs and call graphs for that request's CPU samples.

## Extending the export pipeline

Implement `JfrPprofHandler` and register it as a Spring bean to send pprof payloads anywhere:

```java
@Bean
public JfrPprofHandler grafanaAlloyHandler() {
    return pprof -> { /* push to Grafana Alloy or any remote endpoint */ };
}
```

Multiple handlers are supported; all receive every profile.

## Requirements

- Java 25+ (needed for `jdk.CPUTimeSample`; on older JDKs the library still works via the `jdk.ExecutionSample` fallback)
- Linux, to get non-safepoint-biased sampling (`jdk.CPUTimeSample` is Linux-only per JEP 509)
- Spring Boot 3.x / 4.x

## Roadmap

Not yet production ready. Known gaps, roughly in priority order:

- **No file retention.** `PprofFileHandler` writes one `.pb.gz` per HTTP request, forever — no age/count-based cleanup, so a busy service will fill its disk.
- **No path exclusions.** The interceptor profiles every request, including health checks and static assets, with no `excludePathPatterns` support.
- **Drops are invisible.** Exports beyond the concurrency limit are silently dropped (tracked internally but never exposed as a metric or log line).
- **Async servlet requests untested.** `Callable`/`DeferredResult`/SSE-style controllers may run on a different thread than the one `afterCompletion` sees, which could break the per-request thread filter.
- **No concurrency/load testing.** Only sequential single-request behavior has been verified; overhead and correctness under real concurrent traffic are unknown.
- **Sampling rate is hardcoded.** The `10ms` throttle isn't configurable, so there's no way to trade off overhead vs. resolution without a code change.

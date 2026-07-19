package com.emu.jfr_profiling;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jdk.jfr.Recording;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

import org.springframework.web.servlet.HandlerInterceptor;

import com.emu.jfr_profiling.config.JfrProfilingInterceptorProperties;

@Slf4j
@RequiredArgsConstructor
class JfrProfilerInterceptor implements HandlerInterceptor {

    private final ProfileExporter profileExporter;
    private final JfrProfilingInterceptorProperties jfrProfilingInterceptor;


    // ponytail: jdk.CPUTimeSample (JEP 509, JDK 25+) samples via a per-thread CPU-time signal
    // instead of safepoints, so tight/allocation-free code isn't silently under-sampled. It's
    // Linux-only today; elsewhere we fall back to jdk.ExecutionSample, which IS safepoint-biased.
    // Upgrade path: none needed once running on Linux, which is where this is meant to matter (prod).
    static final boolean CPU_TIME_SAMPLING_SUPPORTED =
            System.getProperty("os.name", "").toLowerCase().contains("linux");
    static final String SAMPLE_EVENT = CPU_TIME_SAMPLING_SUPPORTED ? "jdk.CPUTimeSample" : "jdk.ExecutionSample";

    private static final String RECORDING_ATTR = "jfr.recording";
    private static final String THREAD_ID_ATTR = "jfr.threadId";

    @Override
    public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler
    ) {

        if (jfrProfilingInterceptor.excludedEndpoints().contains(request.getRequestURI())) {
            return true;
        }

        // This library must never turn a JFR problem into a failed request for the host
        // application, so any failure setting up the recording is swallowed here: the request
        // just proceeds unprofiled instead of erroring out.
        Recording recording = null;
        try {
            recording = new Recording();
            if (CPU_TIME_SAMPLING_SUPPORTED) {
                recording.enable(SAMPLE_EVENT).with("throttle", "10ms");
            } else {
                recording.enable(SAMPLE_EVENT).withPeriod(Duration.ofMillis(10));
            }
            recording.start();

            request.setAttribute(RECORDING_ATTR, recording);
            request.setAttribute(THREAD_ID_ATTR, Thread.currentThread().threadId());
        } catch (RuntimeException e) {
            log.warn("Failed to start JFR recording for {}; continuing without profiling", request.getRequestURI(), e);
            if (recording != null) {
                recording.close();
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception ex
    ) {

        // Null whenever preHandle excluded this request, or failed to start a recording above -
        // either way there's nothing to stop or export.
        Recording recording = (Recording) request.getAttribute(RECORDING_ATTR);
        if (recording == null) {
            return;
        }
        long threadId = (long) request.getAttribute(THREAD_ID_ATTR);

        try {
            recording.stop();
        } catch (RuntimeException e) {
            log.warn("Failed to stop JFR recording for {}; dropping this profile", request.getRequestURI(), e);
            recording.close();
            return;
        }

        // ponytail: dump()/parse is real disk I/O, so it happens off this thread in
        // ProfileExporter (virtual-thread executor), not here on the request thread.
        profileExporter.exportAsync(recording, threadId);
    }
}

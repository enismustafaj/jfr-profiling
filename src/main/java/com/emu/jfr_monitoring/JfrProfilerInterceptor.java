package com.emu.jfr_monitoring;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jdk.jfr.Recording;

import java.time.Duration;

import org.springframework.web.servlet.HandlerInterceptor;

class JfrProfilerInterceptor implements HandlerInterceptor {

    private final ProfileExporter profileExporter;

    public JfrProfilerInterceptor(ProfileExporter profileExporter) {
        this.profileExporter = profileExporter;
    }

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

        Recording recording = new Recording();
        if (CPU_TIME_SAMPLING_SUPPORTED) {
            recording.enable(SAMPLE_EVENT).with("throttle", "10ms");
        } else {
            recording.enable(SAMPLE_EVENT).withPeriod(Duration.ofMillis(10));
        }
        recording.start();

        request.setAttribute(RECORDING_ATTR, recording);
        request.setAttribute(THREAD_ID_ATTR, Thread.currentThread().threadId());
        return true;
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception ex
    ) {

        Recording recording = (Recording) request.getAttribute(RECORDING_ATTR);
        long threadId = (long) request.getAttribute(THREAD_ID_ATTR);

        recording.stop();
        // ponytail: dump()/parse is real disk I/O, so it happens off this thread in
        // ProfileExporter (virtual-thread executor), not here on the request thread.
        profileExporter.exportAsync(recording, threadId);
    }
}

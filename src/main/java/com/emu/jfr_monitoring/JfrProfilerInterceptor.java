package com.emu.jfr_monitoring;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingStream;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.web.servlet.HandlerInterceptor;

class JfrProfilerInterceptor implements HandlerInterceptor {

    private final ProfileExporter profileExporter;

    public JfrProfilerInterceptor(ProfileExporter profileExporter) {
        this.profileExporter = profileExporter;
    }

    private static final String EXECUTION_SAMPLE_EVENT = "jdk.ExecutionSample";
    private static final String STREAM_ATTR = "jfr.stream";
    private static final String START_ATTR = "jfr.start";


    @Override
    public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler
    ) {

        RecordingStream stream = new RecordingStream();
        stream.enable(EXECUTION_SAMPLE_EVENT).withPeriod(Duration.ofNanos(10));

        long threadId = Thread.currentThread().threadId();
        List<RecordedEvent> captured = Collections.synchronizedList(new ArrayList<>());

        stream.onEvent("jdk.ExecutionSample", ev -> {
            if (isSameThread(ev, threadId)) captured.add(ev);
        });

        stream.startAsync();

        request.setAttribute(STREAM_ATTR, stream);
        request.setAttribute(START_ATTR, System.nanoTime());
        request.setAttribute("jfr.captured", captured);
        return true;
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception ex
    ) {

        RecordingStream stream = (RecordingStream) request.getAttribute(STREAM_ATTR);
        long start = (long) request.getAttribute(START_ATTR);
        @SuppressWarnings("unchecked")
        List<RecordedEvent> captured = (List<RecordedEvent>) request.getAttribute("jfr.captured");

        long durationMillis = (System.nanoTime() - start) / 1_000_000;

        stream.close();

        String endpoint = request.getRequestURI();

    }

    private boolean isSameThread(RecordedEvent ev, long threadId) {
            RecordedThread t = ev.getThread();
            return t != null && t.getJavaThreadId() == threadId;
        }
}

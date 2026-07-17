package com.emu.jfr_monitoring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.emu.jfr_monitoring.pprof.JfrToPprofConverter;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class ProfileExporter {

        private final ExecutorService executor;
        private final JfrToPprofConverter jfrToPprofConverter;
        private final JfrProfilingRouter jfrProfilingRouter;
        private final Semaphore concurrencyLimiter;
        private final AtomicLong droppedCount = new AtomicLong();

        public ProfileExporter(JfrToPprofConverter jfrToPprofConverter, JfrProfilingRouter jfrProfilingRouter) {
            this.executor = Executors.newVirtualThreadPerTaskExecutor();
            this.jfrProfilingRouter = jfrProfilingRouter;
            this.jfrToPprofConverter = jfrToPprofConverter;
            this.concurrencyLimiter = new Semaphore(64);
        }

        public void exportAsync(Recording recording, long threadId) {
            if (!concurrencyLimiter.tryAcquire()) {
                droppedCount.incrementAndGet();
                recording.close();
                return;
            }

            executor.submit(() -> {
                try {
                    List<RecordedEvent> captured = readSamplesForThread(recording, threadId);
                    byte[] pprofBytes = jfrToPprofConverter.processEvents(captured);
                    jfrProfilingRouter.route(pprofBytes);
                } catch (Exception e) {
                    log.warn("Failed to export profile for trace!", e);
                } finally {
                    recording.close();
                    concurrencyLimiter.release();
                }
            });
        }

        private List<RecordedEvent> readSamplesForThread(Recording recording, long threadId) throws IOException {
            Path dump = Files.createTempFile("jfr-request-", ".jfr");
            try {
                recording.dump(dump);
                List<RecordedEvent> captured = new ArrayList<>();
                try (RecordingFile file = new RecordingFile(dump)) {
                    while (file.hasMoreEvents()) {
                        RecordedEvent event = file.readEvent();
                        if (isBiasedSample(event)) continue;
                        if (isSameThread(event, threadId)) {
                            captured.add(event);
                        }
                    }
                }
                return captured;
            } finally {
                Files.deleteIfExists(dump);
            }
        }

        // jdk.CPUTimeSample can fall back to a safepoint-derived stack for individual samples
        // (e.g. during class loading); drop those so every captured sample is genuinely CPU-time based.
        private boolean isBiasedSample(RecordedEvent event) {
            return event.hasField("biased") && event.getBoolean("biased");
        }

        // jdk.CPUTimeSample names its thread field "eventThread" (the JFR convention, so
        // RecordedEvent.getThread() works); jdk.ExecutionSample instead uses "sampledThread",
        // which getThread() does not know about, so it must be looked up explicitly.
        private boolean isSameThread(RecordedEvent ev, long threadId) {
            RecordedThread t = ev.hasField("eventThread") ? ev.getThread() : ev.getThread("sampledThread");
            return t != null && t.getJavaThreadId() == threadId;
        }
}

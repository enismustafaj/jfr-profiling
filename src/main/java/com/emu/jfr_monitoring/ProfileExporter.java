package com.emu.jfr_monitoring;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.emu.jfr_monitoring.pprof.JfrToPprofConverter;

import jdk.jfr.consumer.RecordedEvent;
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

        public void exportAsync(List<RecordedEvent> captured) {
            if (!concurrencyLimiter.tryAcquire()) {
                droppedCount.incrementAndGet();
                return;
            }

            executor.submit(() -> {
                try {
                    byte[] pprofBytes = jfrToPprofConverter.processEvents(captured);
                    jfrProfilingRouter.route(pprofBytes);
                } catch (Exception e) {
                    log.warn("Failed to export profile for trace!", e);
                } finally {
                    concurrencyLimiter.release();
                }
            });
        }

        public void shutdown() {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
}

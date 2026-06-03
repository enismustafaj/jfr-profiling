package com.emu.jfr_monitoring;

import com.emu.jfr_monitoring.config.JfrMonitoringConfiguration;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class JfrRecordingService implements InitializingBean, DisposableBean {

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JfrMonitoringConfiguration config;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "jfr-dump");
        t.setDaemon(true);
        return t;
    });

    private Recording recording;

    public JfrRecordingService(JfrMonitoringConfiguration config) {
        this.config = config;
    }

    @Override
    public void afterPropertiesSet() throws IOException, ParseException {
        recording = new Recording(Configuration.getConfiguration("default"));
        recording.setMaxAge(Duration.ofSeconds(config.recordingIntervalSeconds() * 2));
        recording.start();
        log.info("JFR recording started");

        long interval = Math.max(config.recordingIntervalSeconds(), 1);
        scheduler.scheduleAtFixedRate(this::dumpSafe, interval, interval, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() {
        scheduler.shutdown();
        dumpSafe();
        recording.stop();
        recording.close();
        log.info("JFR recording stopped");
    }

    private void dumpSafe() {
        try {
            Path outputPath = resolveOutputPath();
            recording.dump(outputPath);
            log.info("JFR recording dumped to {}", outputPath);
        } catch (IOException e) {
            log.error("Failed to dump JFR recording", e);
        }
    }

    private Path resolveOutputPath() {
        String endpoint = config.outputEndpoint();
        String timestamp = TIMESTAMP_FMT.format(ZonedDateTime.now(ZoneOffset.UTC));
        String filename = "jfr-" + timestamp + ".jfr";

        if (endpoint == null || endpoint.isBlank()) {
            return Path.of(System.getProperty("java.io.tmpdir"), filename);
        }
        return Path.of(endpoint, filename);
    }
}

package com.emu.jfr_monitoring;

import com.emu.jfr_monitoring.config.JfrMonitoringConfiguration;
import com.emu.jfr_monitoring.pprof.JfrToPprofConverter;
import jdk.jfr.Configuration;
import jdk.jfr.consumer.RecordingStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.io.IOException;
import java.text.ParseException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class JfrRecordingRecorder implements InitializingBean, DisposableBean {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "jfr-dump");
        t.setDaemon(true);
        return t;
    });

    private final JfrMonitoringConfiguration config;
    private final JfrMonitoringRouter router;
    private final JfrToPprofConverter converter = new JfrToPprofConverter();

    private RecordingStream stream;

    public JfrRecordingRecorder(JfrMonitoringConfiguration config, JfrMonitoringRouter router) {
        this.config = config;
        this.router = router;
    }

    @Override
    public void afterPropertiesSet() throws IOException, ParseException {
        stream = new RecordingStream(Configuration.getConfiguration("default"));
        stream.onEvent("com.emu.jfr_monitoring.events.SampleEvent", converter::addEvent);
        stream.startAsync();
        log.info("JFR recording started");

        long interval = Math.max(config.recordingIntervalSeconds(), 1);
        scheduler.scheduleAtFixedRate(this::dumpSafe, interval, interval, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() {
        scheduler.shutdown();
        dumpSafe();
        stream.close();
        log.info("JFR recording stopped");
    }

    private void dumpSafe() {
        try {
            router.route(converter.buildAndReset());
        } catch (IOException e) {
            log.error("Failed to build pprof profile", e);
        }
    }
}

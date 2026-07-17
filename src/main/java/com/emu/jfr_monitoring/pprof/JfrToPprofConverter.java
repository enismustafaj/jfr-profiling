package com.emu.jfr_monitoring.pprof;

import jdk.jfr.consumer.RecordedEvent;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;

@Slf4j
public class JfrToPprofConverter {

    private final ProfileBuilder current = new ProfileBuilder();

    public byte[] processEvents(List<RecordedEvent> events) {
        for (RecordedEvent event : events) {
            current.addEvent(event);
        }

        try {
            return current.build();
        } catch (IOException e) {
            log.error("Failed to build profile", e);
            throw new RuntimeException("Failed to build profile", e);
        }
    }
}

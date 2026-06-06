package com.emu.jfr_monitoring.pprof;

import jdk.jfr.consumer.RecordedEvent;

import java.io.IOException;

public class JfrToPprofConverter {

    private ProfileBuilder current = new ProfileBuilder();

    public synchronized void addEvent(RecordedEvent event) {
        current.addEvent(event);
    }

    public byte[] buildAndReset() throws IOException {
        ProfileBuilder snapshot;
        synchronized (this) {
            snapshot = current;
            current = new ProfileBuilder();
        }
        return snapshot.build();
    }
}

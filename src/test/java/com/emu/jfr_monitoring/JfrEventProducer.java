package com.emu.jfr_monitoring;

import com.emu.jfr_monitoring.events.SampleEvent;

public class JfrEventProducer {

    public void produceEvent() {

        for (int i = 0; i < 100; i++) {
            // Simulate some processing
            try {
                // Create a new SampleEvent and commit it
                SampleEvent event = new SampleEvent();
                event.commit();
                event.end();
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

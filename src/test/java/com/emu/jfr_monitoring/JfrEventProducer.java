package com.emu.jfr_monitoring;

import com.emu.jfr_monitoring.events.SampleEvent;

public class JfrEventProducer {

    public void produceEvent() {

        for (int i = 0; i < 100; i++) {
            // Simulate some processing
            try {
                // Create a new SampleEvent and commit it
                SampleEvent event = new SampleEvent(
                        "order-" + i, // orderId
                        Math.random() * 1000, // amount
                        (int) (Math.random() * 10) // itemCount
                );

                event.commit();
                event.end();
                Thread.sleep(100); // Simulate some delay between events
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

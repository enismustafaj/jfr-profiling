package com.emu.jfr_monitoring.events;

import jdk.jfr.*;
import lombok.AllArgsConstructor;

@Name("com.emu.jfr_monitoring.events.SampleEvent")
@Label("Sample Event")
@Description("Emitted when an order finishes processing")
@Category({ "Application", "Orders" })
@AllArgsConstructor
public class SampleEvent extends Event {

    @Label("Order ID")
    String orderId;

    @Label("Amount")
    @DataAmount
    double amount;

    @Label("Item Count")
    int itemCount;
}

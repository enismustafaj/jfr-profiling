package com.emu.jfr_monitoring.handlers;

import java.net.http.HttpClient;

import com.emu.jfr_monitoring.JfrPprofHandler;

public class JfrTelemetryHandler implements JfrPprofHandler {

    private final HttpClient httpClient;

    public JfrTelemetryHandler(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void handle(byte[] pprof) {
        // TODO: Implement the logic to send the pprof data to a telemetry endpoint
        // using the httpClient
    }
}

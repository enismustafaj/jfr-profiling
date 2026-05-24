package com.emu.jfr_monitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jfr-monitoring")
public record JfrMonitoringConfiguration(
        boolean enabled,
        String outputEndpoint,
        long recordingIntervalSeconds) {

}

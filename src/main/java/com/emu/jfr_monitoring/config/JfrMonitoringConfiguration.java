package com.emu.jfr_monitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "jfr-monitoring")
public record JfrMonitoringConfiguration(
                @DefaultValue("false") boolean enabled,
                @DefaultValue(".") String outputEndpoint
) {
}

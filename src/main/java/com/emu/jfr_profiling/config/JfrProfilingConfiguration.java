package com.emu.jfr_profiling.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "jfr-profiling")
public record JfrProfilingConfiguration(
                @DefaultValue("false") boolean enabled,
                @DefaultValue(".") String outputEndpoint
) {
}

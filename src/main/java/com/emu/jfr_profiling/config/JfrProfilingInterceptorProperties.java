package com.emu.jfr_profiling.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jfr-profiling.interceptor")
public record JfrProfilingInterceptorProperties(
    List<String> excludedEndpoints
) {
    public JfrProfilingInterceptorProperties {
        excludedEndpoints = excludedEndpoints == null ? List.of() : excludedEndpoints;
    }
}

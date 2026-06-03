package com.emu.jfr_monitoring;

import com.emu.jfr_monitoring.config.JfrMonitoringConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(JfrMonitoringConfiguration.class)
@ConditionalOnProperty(prefix = "jfr-monitoring", name = "enabled", havingValue = "true")
public class JfrMonitoringAutoconfigutration {

    @Bean
    public JfrRecordingService jfrRecordingService(JfrMonitoringConfiguration config) {
        return new JfrRecordingService(config);
    }

}

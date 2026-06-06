package com.emu.jfr_monitoring;

import com.emu.jfr_monitoring.config.JfrMonitoringConfiguration;
import com.emu.jfr_monitoring.handlers.PprofFileHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(JfrMonitoringConfiguration.class)
@ConditionalOnProperty(prefix = "jfr-monitoring", name = "enabled", havingValue = "true")
public class JfrMonitoringAutoconfigutration {

    @Bean
    public JfrPprofHandler pprofFileHandler(JfrMonitoringConfiguration config) throws IOException {
        return new PprofFileHandler(config.outputEndpoint());
    }

    @Bean
    public JfrMonitoringRouter jfrMonitoringRouter(List<JfrPprofHandler> handlers) {
        return new JfrMonitoringRouter(handlers);
    }

    @Bean
    public JfrRecordingRecorder jfrRecordingService(JfrMonitoringConfiguration config, JfrMonitoringRouter router) {
        return new JfrRecordingRecorder(config, router);
    }

}

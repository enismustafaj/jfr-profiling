package com.emu.jfr_monitoring.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.emu.jfr_monitoring.JfrEventProducer;

@TestConfiguration
public class JfrMonitoringTestConfiguration {

    @Bean
    JfrEventProducer jfrEventProducer() {
        return new JfrEventProducer();
    }

}

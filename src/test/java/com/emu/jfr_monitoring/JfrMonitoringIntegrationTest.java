package com.emu.jfr_monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.emu.jfr_monitoring.config.JfrMonitoringTestConfiguration;

class JfrMonitoringIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JfrMonitoringAutoConfiguration.class));

    @Test
    void testJfrMonitoringBeans() {
        contextRunner.withPropertyValues("jfr-monitoring.enabled=true")
                .withUserConfiguration(JfrMonitoringTestConfiguration.class)
                .run(context -> {
                    // Check if the beans are present in the context
                    assert context.containsBean("pprofFileHandler");
                    assert context.containsBean("jfrMonitoringRouter");
                    assert context.containsBean("jfrRecordingService");
                });
    }

}

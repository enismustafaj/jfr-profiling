package com.emu.jfr_monitoring;

import com.emu.jfr_monitoring.config.JfrMonitoringConfiguration;
import com.emu.jfr_monitoring.handlers.PprofFileHandler;
import com.emu.jfr_monitoring.pprof.JfrToPprofConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(JfrMonitoringConfiguration.class)
@ConditionalOnProperty(prefix = "jfr-monitoring", name = "enabled", havingValue = "true")
public class JfrMonitoringAutoConfiguration {

    @Bean
    public JfrPprofHandler pprofFileHandler(JfrMonitoringConfiguration config) throws IOException {
        return new PprofFileHandler(config.outputEndpoint());
    }

    @Bean
    public JfrProfilingRouter jfrMonitoringRouter(List<JfrPprofHandler> handlers) {
        return new JfrProfilingRouter(handlers);
    }

    @Bean
    public JfrToPprofConverter jfrToPprofConverter() {
        return new JfrToPprofConverter();
    }

    @Bean
    public ProfileExporter profileExporter(JfrToPprofConverter jfrToPprofConverter, JfrProfilingRouter jfrProfilingRouter) {
        return new ProfileExporter(jfrToPprofConverter, jfrProfilingRouter);
    }

    @Bean
    public JfrProfilerInterceptor jfrProfilerInterceptor(ProfileExporter profileExporter) {
        return new JfrProfilerInterceptor(profileExporter);
    }

    @Bean
    public WebMvcConfigurer jfrMonitoringWebMvcConfigurer(JfrProfilerInterceptor jfrProfilerInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(jfrProfilerInterceptor);
            }
        };
    }
}

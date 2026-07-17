package com.emu.jfr_profiling;

import com.emu.jfr_profiling.config.JfrProfilingConfiguration;
import com.emu.jfr_profiling.handlers.PprofFileHandler;
import com.emu.jfr_profiling.pprof.JfrToPprofConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(JfrProfilingConfiguration.class)
@ConditionalOnProperty(prefix = "jfr-profiling", name = "enabled", havingValue = "true")
public class JfrProfilingAutoConfiguration {

    @Bean
    public JfrPprofHandler pprofFileHandler(JfrProfilingConfiguration config) throws IOException {
        return new PprofFileHandler(config.outputEndpoint());
    }

    @Bean
    public JfrProfilingRouter jfrProfilingRouter(List<JfrPprofHandler> handlers) {
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
    public WebMvcConfigurer jfrProfilingWebMvcConfigurer(JfrProfilerInterceptor jfrProfilerInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(jfrProfilerInterceptor);
            }
        };
    }
}

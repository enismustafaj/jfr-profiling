package com.emu.jfr_profiling;

import com.google.perftools.profiles.ProfileProto.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = JfrProfilingHttpIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class JfrProfilingHttpIntegrationTest {

    @TempDir
    static Path outputDir;

    @DynamicPropertySource
    static void jfrProfilingProperties(DynamicPropertyRegistry registry) {
        registry.add("jfr-profiling.enabled", () -> "true");
        registry.add("jfr-profiling.output-endpoint", () -> outputDir.toString());
        registry.add("jfr-profiling.interceptor.excluded-endpoints", () -> "/health");
    }

    @Autowired
    WebApplicationContext webApplicationContext;

    @Test
    void requestThroughInterceptorProducesValidPprofFile() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockMvc.perform(get("/burn")).andExpect(status().isOk());

        Path pprofFile = awaitPprofFile();
        Profile profile = readProfile(pprofFile);

        assertThat(profile.getSampleCount()).isGreaterThan(0);
        assertThat(profile.getFunctionCount()).isGreaterThan(0);
        assertThat(profile.getLocationCount()).isGreaterThan(0);
        assertThat(profile.getStringTable(0)).isEmpty();
        assertThat(profile.getSampleTypeList())
                .anyMatch(vt -> profile.getStringTable((int) vt.getType()).equals("samples"));
    }

    @Test
    void excludedEndpointDoesNotProduceProfile() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        long filesBefore = countPprofFiles();
        mockMvc.perform(get("/health")).andExpect(status().isOk());

        Duration window = Duration.ofSeconds(5);
        long deadline = System.nanoTime() + window.toNanos();
        while (System.nanoTime() < deadline) {
            assertThat(countPprofFiles())
                    .as("excluded endpoint should never produce a pprof file")
                    .isEqualTo(filesBefore);
            Thread.sleep(50);
        }
    }

    private static long countPprofFiles() throws IOException {
        try (Stream<Path> files = Files.list(outputDir)) {
            return files.filter(p -> p.toString().endsWith(".pb.gz")).count();
        }
    }

    private static Profile readProfile(Path pprofFile) throws IOException {
        byte[] gzipped = Files.readAllBytes(pprofFile);
        try (GZIPInputStream gzipIn = new GZIPInputStream(new ByteArrayInputStream(gzipped))) {
            return Profile.parseFrom(gzipIn);
        }
    }

    private static Path awaitPprofFile() throws InterruptedException {
        Duration timeout = Duration.ofSeconds(5);
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try (Stream<Path> files = Files.list(outputDir)) {
                Optional<Path> match = files.filter(p -> p.toString().endsWith(".pb.gz")).findFirst();
                if (match.isPresent()) {
                    return match.get();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("no pprof file appeared in " + outputDir + " within " + timeout);
    }

    @SpringBootApplication
    static class TestApp {

        @RestController
        static class BurnController {

            @GetMapping("/burn")
            String burn() {
                // ponytail: allocation + method calls per iteration, not tight arithmetic —
                // HotSpot can strip safepoint polls from pure counted loops, which silently
                // starves JFR's (safepoint-based) execution sampler of any samples.
                List<String> junk = new ArrayList<>();
                long deadline = System.nanoTime() + Duration.ofMillis(500).toNanos();
                while (System.nanoTime() < deadline) {
                    junk.add(UUID.randomUUID().toString());
                    if (junk.size() > 1000) junk.clear();
                }
                return "done:" + junk.size();
            }

            @GetMapping("/health")
            String health() {
                return "ok";
            }
        }
    }
}

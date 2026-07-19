package com.emu.jfr_profiling.handlers;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
class PprofFileWriter {

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path outputDir;

    public PprofFileWriter(String outputEndpoint) throws IOException {
        this.outputDir = Path.of(outputEndpoint);
        Files.createDirectories(outputDir);
    }

    public void write(byte[] pprof) throws IOException {
        String timestamp = TIMESTAMP_FMT.format(ZonedDateTime.now(ZoneOffset.UTC));

        String unique = Long.toHexString(System.nanoTime());
        Path outputPath = outputDir.resolve("jfr-" + timestamp + "-" + unique + ".pb.gz");
        Files.write(outputPath, pprof);
        log.info("pprof profile written to {}", outputPath);
    }
}

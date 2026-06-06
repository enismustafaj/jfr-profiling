package com.emu.jfr_monitoring.handlers;

import com.emu.jfr_monitoring.JfrPprofHandler;
import com.emu.jfr_monitoring.pprof.PprofFileWriter;

import java.io.IOException;
import java.io.UncheckedIOException;

public class PprofFileHandler implements JfrPprofHandler {

    private final PprofFileWriter writer;

    public PprofFileHandler(String outputEndpoint) throws IOException {
        this.writer = new PprofFileWriter(outputEndpoint);
    }

    @Override
    public void handle(byte[] pprof) {
        try {
            writer.write(pprof);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

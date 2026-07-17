package com.emu.jfr_monitoring;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class JfrProfilingRouter {

    private final List<JfrPprofHandler> handlers;

    public JfrProfilingRouter(List<JfrPprofHandler> handlers) {
        this.handlers = handlers;
    }

    public void route(byte[] pprof) {
        for (JfrPprofHandler handler : handlers) {
            try {
                handler.handle(pprof);
            } catch (Exception e) {
                log.error("Handler {} failed to process pprof profile", handler.getClass().getSimpleName(), e);
            }
        }
    }
}

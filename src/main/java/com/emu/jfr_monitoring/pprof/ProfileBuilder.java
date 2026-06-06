package com.emu.jfr_monitoring.pprof;

import com.google.perftools.profiles.ProfileProto.*;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

class ProfileBuilder {

    private final StringTable strings = new StringTable();
    private final Map<String, Long> locationCache = new HashMap<>();
    private final Map<String, Long> functionCache = new HashMap<>();
    private final List<Sample> samples = new ArrayList<>();
    private final List<Location> locations = new ArrayList<>();
    private final List<Function> functions = new ArrayList<>();
    private long startNanos = 0;
    private long endNanos = 0;

    void addEvent(RecordedEvent event) {
        long eventNanos = toNanos(event);
        if (startNanos == 0) startNanos = eventNanos;
        endNanos = Math.max(endNanos, eventNanos);

        RecordedStackTrace stackTrace = event.getStackTrace();
        if (stackTrace == null) return;

        List<Long> locationIds = new ArrayList<>();
        for (RecordedFrame frame : stackTrace.getFrames()) {
            if (!frame.isJavaFrame()) continue;

            String className = frame.getMethod().getType().getName();
            String methodName = frame.getMethod().getName();
            String fullName = className + "." + methodName;
            int lineNo = frame.getLineNumber();
            String locKey = fullName + ":" + lineNo;

            if (!locationCache.containsKey(locKey)) {
                if (!functionCache.containsKey(fullName)) {
                    long fid = functions.size() + 1;
                    functions.add(Function.newBuilder()
                            .setId(fid)
                            .setName(strings.intern(fullName))
                            .setSystemName(strings.intern(fullName))
                            .setFilename(strings.intern(className.replace('.', '/') + ".java"))
                            .build());
                    functionCache.put(fullName, fid);
                }
                long funcId = functionCache.get(fullName);
                long lid = locations.size() + 1;
                Line.Builder line = Line.newBuilder().setFunctionId(funcId);
                if (lineNo > 0) line.setLine(lineNo);
                locations.add(Location.newBuilder().setId(lid).addLine(line).build());
                locationCache.put(locKey, lid);
            }
            locationIds.add(locationCache.get(locKey));
        }

        if (!locationIds.isEmpty()) {
            samples.add(Sample.newBuilder().addAllLocationId(locationIds).addValue(1).build());
        }
    }

    byte[] build() throws IOException {
        long samplesIdx = strings.intern("samples");
        long countIdx = strings.intern("count");
        long cpuIdx = strings.intern("cpu");
        long nanosIdx = strings.intern("nanoseconds");

        Profile profile = Profile.newBuilder()
                .addSampleType(ValueType.newBuilder().setType(samplesIdx).setUnit(countIdx).build())
                .addAllSample(samples)
                .addAllLocation(locations)
                .addAllFunction(functions)
                .addAllStringTable(strings.table())
                .setTimeNanos(startNanos)
                .setDurationNanos(endNanos - startNanos)
                .setPeriodType(ValueType.newBuilder().setType(cpuIdx).setUnit(nanosIdx).build())
                .setPeriod(10_000_000) // 10ms default JFR sampling period
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            profile.writeTo(gzip);
        }
        return baos.toByteArray();
    }

    private static long toNanos(RecordedEvent event) {
        var t = event.getStartTime();
        return t.getEpochSecond() * 1_000_000_000L + t.getNano();
    }

    private static class StringTable {
        private final Map<String, Long> index = new HashMap<>();
        private final List<String> table = new ArrayList<>();

        StringTable() {
            intern(""); // pprof requires index 0 to be the empty string
        }

        long intern(String s) {
            return index.computeIfAbsent(s, str -> {
                long idx = table.size();
                table.add(str);
                return idx;
            });
        }

        List<String> table() {
            return List.copyOf(table);
        }
    }
}

package com.wizzardo.metrics;

import java.lang.management.MemoryUsage;

public class MemoryStats implements JvmMonitoring.Recordable {

    private MemoryUsage usage;
    private JvmMonitoring jvmMonitoring;
    private Recorder.Tags tags;

    public MemoryStats(MemoryUsage usage, JvmMonitoring jvmMonitoring, Recorder.Tags tags) {
        this.usage = usage;
        this.jvmMonitoring = jvmMonitoring;
        this.tags = tags;
    }

    @Override
    public void record(Recorder recorder) {
        long committed = usage.getCommitted();
        long init = usage.getInit();
        long max = usage.getMax();
        long used = usage.getUsed();

        recorder.gauge(jvmMonitoring.metricJvmMemCommitted, committed, tags);
        recorder.gauge(jvmMonitoring.metricJvmMemInit, init, tags);
        recorder.gauge(jvmMonitoring.metricJvmMemUsed, used, tags);
        recorder.gauge(jvmMonitoring.metricJvmMemMax, max, tags);
    }

    @Override
    public boolean isValid() {
        return true;
    }
}

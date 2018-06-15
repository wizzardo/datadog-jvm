package com.wizzardo.metrics;

import com.wizzardo.tools.interfaces.Supplier;

import java.lang.management.MemoryUsage;
import java.util.concurrent.Callable;

public class MemoryStats implements JvmMonitoring.Recordable {

    private Supplier<MemoryUsage> provider;
    private JvmMonitoring jvmMonitoring;
    private Recorder.Tags tags;

    public MemoryStats(Supplier<MemoryUsage> provider, JvmMonitoring jvmMonitoring, Recorder.Tags tags) {
        this.provider = provider;
        this.jvmMonitoring = jvmMonitoring;
        this.tags = tags;
    }

    @Override
    public void record(Recorder recorder) {
        MemoryUsage usage = provider.supply();
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

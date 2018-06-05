package com.wizzardo.metrics;

import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;

/**
 * Created by wizzardo on 08/10/16.
 */
public class MemoryPoolStats implements JvmMonitoring.Recordable {

    private MemoryPoolMXBean memoryPool;
    private Recorder.Tags tags;
    private JvmMonitoring jvmMonitoring;

    public MemoryPoolStats(MemoryPoolMXBean memoryPool, JvmMonitoring jvmMonitoring) {
        this.memoryPool = memoryPool;
        this.jvmMonitoring = jvmMonitoring;
        tags = jvmMonitoring.getTags(memoryPool);
    }

    @Override
    public void record(Recorder recorder) {
        if (!isValid())
            return;

        MemoryUsage usage = memoryPool.getUsage();
        recorder.gauge(jvmMonitoring.metricJvmMemoryPoolCommitted, usage.getCommitted(), tags);
        recorder.gauge(jvmMonitoring.metricJvmMemoryPoolInit, usage.getInit(), tags);
        recorder.gauge(jvmMonitoring.metricJvmMemoryPoolMax, usage.getMax(), tags);
        recorder.gauge(jvmMonitoring.metricJvmMemoryPoolUsed, usage.getUsed(), tags);
    }

    @Override
    public boolean isValid() {
        return memoryPool.isValid();
    }
}

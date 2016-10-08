package com.wizzardo.metrics;

import java.lang.management.MemoryPoolMXBean;

/**
 * Created by wizzardo on 08/10/16.
 */
public class MemoryStats implements JvmMonitoring.Recordable {

    private MemoryPoolMXBean memoryPool;
    private Recorder.Tags tags;
    private JvmMonitoring jvmMonitoring;

    public MemoryStats(MemoryPoolMXBean memoryPool, JvmMonitoring jvmMonitoring) {
        this.memoryPool = memoryPool;
        this.jvmMonitoring = jvmMonitoring;
        tags = jvmMonitoring.getTags(memoryPool);
    }

    @Override
    public void record(Recorder recorder) {
        if (!isValid())
            return;

        recorder.gauge(jvmMonitoring.metricJvmMemoryPoolCommitted, memoryPool.getUsage().getCommitted(), tags);
        recorder.gauge(jvmMonitoring.metricJvmMemoryPoolInit, memoryPool.getUsage().getInit(), tags);
        recorder.gauge(jvmMonitoring.metricJvmMemoryPoolMax, memoryPool.getUsage().getMax(), tags);
        recorder.gauge(jvmMonitoring.metricJvmMemoryPoolUsed, memoryPool.getUsage().getUsed(), tags);
    }

    @Override
    public boolean isValid() {
        return memoryPool.isValid();
    }
}

package com.wizzardo.metrics;

import java.lang.management.GarbageCollectorMXBean;

/**
 * Created by wizzardo on 08/10/16.
 */
public class GcStats implements JvmMonitoring.Recordable {

    private GarbageCollectorMXBean collector;
    private volatile long collectionCount;
    private volatile long collectionTime;
    private Recorder.Tags tags;
    private JvmMonitoring jvmMonitoring;

    public GcStats(GarbageCollectorMXBean collector, JvmMonitoring jvmMonitoring) {
        this.collector = collector;
        this.jvmMonitoring = jvmMonitoring;
        tags = jvmMonitoring.getTags(collector);
    }

    public synchronized long getCollectionCountDiff() {
        long l = collector.getCollectionCount();
        long result = l - collectionCount;
        collectionCount = l;
        return result;
    }

    public synchronized long getCollectionTimeDiff() {
        long l = collector.getCollectionTime();
        long result = l - collectionTime;
        collectionTime = l;
        return result;
    }

    @Override
    public void record(Recorder recorder) {
        recorder.gauge(jvmMonitoring.metricJvmGcCountTotal, collector.getCollectionCount(), tags);
        recorder.gauge(jvmMonitoring.metricJvmGcTimeTotal, collector.getCollectionTime(), tags);
        recorder.gauge(jvmMonitoring.metricJvmGcCount, getCollectionCountDiff(), tags);
        recorder.rec(jvmMonitoring.metricJvmGcTime, getCollectionTimeDiff(), tags);
    }

    @Override
    public boolean isValid() {
        return true;
    }
}

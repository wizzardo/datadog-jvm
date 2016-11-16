package com.wizzardo.metrics;

import com.wizzardo.tools.cache.CacheStatistics;
import com.wizzardo.tools.cache.MemoryLimitedCache;

/**
 * Created by wizzardo on 08/10/16.
 */
public class CacheStats implements JvmMonitoring.Recordable {

    private CacheStatistics statistics;
    private MemoryLimitedCache.CacheStatisticsWithHeapUsage statisticsWithHeapUsage;
    private Recorder.Tags tags;
    private JvmMonitoring jvmMonitoring;
    private StatisticsHolder previous;
    private Recorder.Tags tagsGetCount;
    private Recorder.Tags tagsGetLatency;
    private Recorder.Tags tagsPutCount;
    private Recorder.Tags tagsPutLatency;
    private Recorder.Tags tagsRemoveCount;
    private Recorder.Tags tagsRemoveLatency;
    private Recorder.Tags tagsComputeCount;
    private Recorder.Tags tagsComputeLatency;
    private Recorder.Tags tagsSize;
    private Recorder.Tags tagsHeap;

    public CacheStats(CacheStatistics cacheStatistics, JvmMonitoring jvmMonitoring) {
        this.statistics = cacheStatistics;
        this.jvmMonitoring = jvmMonitoring;
        tags = jvmMonitoring.getTags(cacheStatistics);
        if (statistics instanceof MemoryLimitedCache.CacheStatisticsWithHeapUsage)
            statisticsWithHeapUsage = (MemoryLimitedCache.CacheStatisticsWithHeapUsage) statistics;

        previous = new StatisticsHolder();

        tagsComputeCount = jvmMonitoring.getTags(cacheStatistics).add("operation", "compute").add("metric", "count");
        tagsComputeLatency = jvmMonitoring.getTags(cacheStatistics).add("operation", "compute").add("metric", "latency");
        tagsGetCount = jvmMonitoring.getTags(cacheStatistics).add("operation", "get").add("metric", "count");
        tagsGetLatency = jvmMonitoring.getTags(cacheStatistics).add("operation", "get").add("metric", "latency");
        tagsPutCount = jvmMonitoring.getTags(cacheStatistics).add("operation", "put").add("metric", "count");
        tagsPutLatency = jvmMonitoring.getTags(cacheStatistics).add("operation", "put").add("metric", "latency");
        tagsRemoveCount = jvmMonitoring.getTags(cacheStatistics).add("operation", "remove").add("metric", "count");
        tagsRemoveLatency = jvmMonitoring.getTags(cacheStatistics).add("operation", "remove").add("metric", "latency");

        tagsSize = jvmMonitoring.getTags(cacheStatistics).add("metric", "size");
        tagsHeap = jvmMonitoring.getTags(cacheStatistics).add("metric", "heap");
    }

    static class StatisticsHolder {
        long computeCount;
        long computeLatency;
        long getCount;
        long getLatency;
        long putCount;
        long putLatency;
        long removeCount;
        long removeLatency;
    }

    @Override
    public void record(Recorder recorder) {
        if (!isValid())
            return;

        recorder.gauge("cache.gauge", statistics.getSize(), tagsSize);
        if (statisticsWithHeapUsage != null)
            recorder.gauge("cache.gauge", statisticsWithHeapUsage.getHeapUsage(), tagsHeap);

        long computeCount = statistics.getComputeCount();
        long computeLatency = statistics.getComputeLatency();
        recorder.gauge("cache.gauge", computeCount, tagsComputeCount);
        recorder.gauge("cache.gauge", computeLatency, tagsComputeLatency);

        long getCount = statistics.getGetCount();
        long getLatency = statistics.getGetLatency();
        recorder.gauge("cache.gauge", getCount, tagsGetCount);
        recorder.gauge("cache.gauge", getLatency, tagsGetLatency);

        long putCount = statistics.getPutCount();
        long putLatency = statistics.getPutLatency();
        recorder.gauge("cache.gauge", putCount, tagsPutCount);
        recorder.gauge("cache.gauge", putLatency, tagsPutLatency);

        long removeCount = statistics.getRemoveCount();
        long removeLatency = statistics.getRemoveLatency();
        recorder.gauge("cache.gauge", removeCount, tagsRemoveCount);
        recorder.gauge("cache.gauge", removeLatency, tagsRemoveLatency);


        recorder.rec("cache", computeCount - previous.computeCount, tagsComputeCount);
        recorder.rec("cache", computeLatency - previous.computeLatency, tagsComputeLatency);

        recorder.rec("cache", getCount - previous.getCount, tagsGetCount);
        recorder.rec("cache", getLatency - previous.getLatency, tagsGetLatency);

        recorder.rec("cache", putCount - previous.putCount, tagsPutCount);
        recorder.rec("cache", putLatency - previous.putLatency, tagsPutLatency);

        recorder.rec("cache", removeCount - previous.removeCount, tagsRemoveCount);
        recorder.rec("cache", removeLatency - previous.removeLatency, tagsRemoveLatency);


        previous.computeCount = computeCount;
        previous.computeLatency = computeLatency;

        previous.getCount = getCount;
        previous.getLatency = getLatency;

        previous.putCount = putCount;
        previous.putLatency = putLatency;

        previous.removeCount = removeCount;
        previous.removeLatency = removeLatency;
    }

    @Override
    public boolean isValid() {
        return statistics.isValid();
    }
}

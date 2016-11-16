package com.wizzardo.metrics;

import com.wizzardo.tools.cache.CacheStatistics;
import com.wizzardo.tools.cache.MemoryLimitedCache;

/**
 * Created by wizzardo on 08/10/16.
 */
public class CacheStats implements JvmMonitoring.Recordable {

    protected String metricCacheGauge = "cache.gauge";
    protected String metricCache = "cache";

    private CacheStatistics statistics;
    private MemoryLimitedCache.CacheStatisticsWithHeapUsage statisticsWithHeapUsage;
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

        recorder.gauge(metricCacheGauge, statistics.getSize(), tagsSize);
        if (statisticsWithHeapUsage != null)
            recorder.gauge(metricCacheGauge, statisticsWithHeapUsage.getHeapUsage(), tagsHeap);

        long computeCount = statistics.getComputeCount();
        long computeLatency = statistics.getComputeLatency();
        recorder.gauge(metricCacheGauge, computeCount, tagsComputeCount);
        recorder.gauge(metricCacheGauge, computeLatency, tagsComputeLatency);

        long getCount = statistics.getGetCount();
        long getLatency = statistics.getGetLatency();
        recorder.gauge(metricCacheGauge, getCount, tagsGetCount);
        recorder.gauge(metricCacheGauge, getLatency, tagsGetLatency);

        long putCount = statistics.getPutCount();
        long putLatency = statistics.getPutLatency();
        recorder.gauge(metricCacheGauge, putCount, tagsPutCount);
        recorder.gauge(metricCacheGauge, putLatency, tagsPutLatency);

        long removeCount = statistics.getRemoveCount();
        long removeLatency = statistics.getRemoveLatency();
        recorder.gauge(metricCacheGauge, removeCount, tagsRemoveCount);
        recorder.gauge(metricCacheGauge, removeLatency, tagsRemoveLatency);


        recorder.histogram(metricCache, computeCount - previous.computeCount, tagsComputeCount);
        recorder.histogram(metricCache, computeLatency - previous.computeLatency, tagsComputeLatency);

        recorder.histogram(metricCache, getCount - previous.getCount, tagsGetCount);
        recorder.histogram(metricCache, getLatency - previous.getLatency, tagsGetLatency);

        recorder.histogram(metricCache, putCount - previous.putCount, tagsPutCount);
        recorder.histogram(metricCache, putLatency - previous.putLatency, tagsPutLatency);

        recorder.histogram(metricCache, removeCount - previous.removeCount, tagsRemoveCount);
        recorder.histogram(metricCache, removeLatency - previous.removeLatency, tagsRemoveLatency);


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

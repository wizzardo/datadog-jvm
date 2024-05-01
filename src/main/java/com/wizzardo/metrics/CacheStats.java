package com.wizzardo.metrics;

import com.wizzardo.tools.cache.CacheStatistics;
//import com.wizzardo.tools.cache.MemoryLimitedCache;

/**
 * Created by wizzardo on 08/10/16.
 */
public class CacheStats implements JvmMonitoring.Recordable {

    private CacheStatistics statistics;
//    private MemoryLimitedCache.CacheStatisticsWithHeapUsage statisticsWithHeapUsage;
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
//        if (statistics instanceof MemoryLimitedCache.CacheStatisticsWithHeapUsage)
//            statisticsWithHeapUsage = (MemoryLimitedCache.CacheStatisticsWithHeapUsage) statistics;

        previous = new StatisticsHolder();

        tagsComputeCount = jvmMonitoring.getTags(cacheStatistics).add("operation", "compute");
        tagsComputeLatency = jvmMonitoring.getTags(cacheStatistics).add("operation", "compute");
        tagsGetCount = jvmMonitoring.getTags(cacheStatistics).add("operation", "get");
        tagsGetLatency = jvmMonitoring.getTags(cacheStatistics).add("operation", "get");
        tagsPutCount = jvmMonitoring.getTags(cacheStatistics).add("operation", "put");
        tagsPutLatency = jvmMonitoring.getTags(cacheStatistics).add("operation", "put");
        tagsRemoveCount = jvmMonitoring.getTags(cacheStatistics).add("operation", "remove");
        tagsRemoveLatency = jvmMonitoring.getTags(cacheStatistics).add("operation", "remove");

        tagsSize = jvmMonitoring.getTags(cacheStatistics);
        tagsHeap = jvmMonitoring.getTags(cacheStatistics);
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

        recorder.gauge(jvmMonitoring.getMetricCacheSize(), statistics.getSize(), tagsSize);
//        if (statisticsWithHeapUsage != null)
//            recorder.gauge(jvmMonitoring.getMetricCacheHeap(), statisticsWithHeapUsage.getHeapUsage(), tagsHeap);

        long computeCount = statistics.getComputeCount();
        long computeLatency = statistics.getComputeLatency();
        recorder.gauge(jvmMonitoring.getMetricCacheCountTotal(), computeCount, tagsComputeCount);
        recorder.gauge(jvmMonitoring.getMetricCacheLatencyTotal(), computeLatency, tagsComputeLatency);

        long getCount = statistics.getGetCount();
        long getLatency = statistics.getGetLatency();
        recorder.gauge(jvmMonitoring.getMetricCacheCountTotal(), getCount, tagsGetCount);
        recorder.gauge(jvmMonitoring.getMetricCacheLatencyTotal(), getLatency, tagsGetLatency);

        long putCount = statistics.getPutCount();
        long putLatency = statistics.getPutLatency();
        recorder.gauge(jvmMonitoring.getMetricCacheCountTotal(), putCount, tagsPutCount);
        recorder.gauge(jvmMonitoring.getMetricCacheLatencyTotal(), putLatency, tagsPutLatency);

        long removeCount = statistics.getRemoveCount();
        long removeLatency = statistics.getRemoveLatency();
        recorder.gauge(jvmMonitoring.getMetricCacheCountTotal(), removeCount, tagsRemoveCount);
        recorder.gauge(jvmMonitoring.getMetricCacheLatencyTotal(), removeLatency, tagsRemoveLatency);


        recorder.histogram(jvmMonitoring.getMetricCacheCount(), computeCount - previous.computeCount, tagsComputeCount);
        recorder.histogram(jvmMonitoring.getMetricCacheLatency(), computeLatency - previous.computeLatency, tagsComputeLatency);

        recorder.histogram(jvmMonitoring.getMetricCacheCount(), getCount - previous.getCount, tagsGetCount);
        recorder.histogram(jvmMonitoring.getMetricCacheLatency(), getLatency - previous.getLatency, tagsGetLatency);

        recorder.histogram(jvmMonitoring.getMetricCacheCount(), putCount - previous.putCount, tagsPutCount);
        recorder.histogram(jvmMonitoring.getMetricCacheLatency(), putLatency - previous.putLatency, tagsPutLatency);

        recorder.histogram(jvmMonitoring.getMetricCacheCount(), removeCount - previous.removeCount, tagsRemoveCount);
        recorder.histogram(jvmMonitoring.getMetricCacheLatency(), removeLatency - previous.removeLatency, tagsRemoveLatency);


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

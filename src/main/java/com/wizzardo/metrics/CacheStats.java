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

    public CacheStats(CacheStatistics cacheStatistics, JvmMonitoring jvmMonitoring) {
        this.statistics = cacheStatistics;
        this.jvmMonitoring = jvmMonitoring;
        tags = jvmMonitoring.getTags(cacheStatistics);
        if (statistics instanceof MemoryLimitedCache.CacheStatisticsWithHeapUsage)
            statisticsWithHeapUsage = (MemoryLimitedCache.CacheStatisticsWithHeapUsage) statistics;

        previous = new StatisticsHolder();
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

        recorder.gauge("cache.size", statistics.getSize(), tags);
        if (statisticsWithHeapUsage != null)
            recorder.gauge("cache.heap", statisticsWithHeapUsage.getHeapUsage(), tags);

        long computeCount = statistics.getComputeCount();
        long computeLatency = statistics.getComputeLatency();
        recorder.gauge("cache.compute.count", computeCount, tags);
        recorder.gauge("cache.compute.latency", computeLatency, tags);

        long getCount = statistics.getGetCount();
        long getLatency = statistics.getGetLatency();
        recorder.gauge("cache.get.count", getCount, tags);
        recorder.gauge("cache.get.latency", getLatency, tags);

        long putCount = statistics.getPutCount();
        long putLatency = statistics.getPutLatency();
        recorder.gauge("cache.put.count", putCount, tags);
        recorder.gauge("cache.put.latency", putLatency, tags);

        long removeCount = statistics.getRemoveCount();
        long removeLatency = statistics.getRemoveLatency();
        recorder.gauge("cache.remove.count", removeCount, tags);
        recorder.gauge("cache.remove.latency", removeLatency, tags);


        recorder.rec("cache.compute.count", computeCount - previous.computeCount, tags);
        recorder.rec("cache.compute.latency", computeLatency - previous.computeLatency, tags);

        recorder.rec("cache.get.count", getCount - previous.getCount, tags);
        recorder.rec("cache.get.latency", getLatency - previous.getLatency, tags);

        recorder.rec("cache.put.count", putCount - previous.putCount, tags);
        recorder.rec("cache.put.latency", putLatency - previous.putLatency, tags);

        recorder.rec("cache.remove.count", removeCount - previous.removeCount, tags);
        recorder.rec("cache.remove.latency", removeLatency - previous.removeLatency, tags);


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

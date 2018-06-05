package com.wizzardo.metrics;

import com.wizzardo.metrics.system.*;
import com.wizzardo.tools.cache.Cache;
import com.wizzardo.tools.cache.CacheCleaner;
import com.wizzardo.tools.cache.CacheStatistics;
import com.wizzardo.tools.interfaces.Filter;
import com.wizzardo.tools.misc.Pair;

import java.lang.management.*;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by wizzardo on 06/09/16.
 */
public class JvmMonitoring {

    protected Recorder recorder;
    protected Cache<String, Recordable> cache;
    protected Profiler profiler;
    protected volatile boolean profilerEnabled = false;
    protected Queue<Pair<Filter<String>, String>> customThreadGroupNames = new ConcurrentLinkedQueue<>();
    protected int interval = 10;
    protected String metricJvmMemoryFree = "jvm.memory.free";
    protected String metricJvmMemoryTotal = "jvm.memory.total";
    protected String metricJvmMemoryUsed = "jvm.memory.used";
    protected String metricJvmMemoryMax = "jvm.memory.max";
    protected String metricJvmMemCommitted = "jvm.mem.committed";
    protected String metricJvmMemInit = "jvm.mem.init";
    protected String metricJvmMemUsed = "jvm.mem.used";
    protected String metricJvmMemMax = "jvm.mem.max";
    protected String metricJvmGcCountTotal = "jvm.gc.countTotal";
    protected String metricJvmGcTimeTotal = "jvm.gc.timeTotal";
    protected String metricJvmGcCount = "jvm.gc.count";
    protected String metricJvmGcTime = "jvm.gc.time";
    protected String metricJvmClassesLoaded = "jvm.classes.loaded";
    protected String metricJvmTotal = "jvm.classes.total";
    protected String metricJvmClassesUnloaded = "jvm.classes.unloaded";
    protected String metricJvmCompilationTime = "jvm.compilation.time";
    protected String metricJvmThreadAlive = "jvm.thread.alive";
    protected String metricJvmProfilerStackTraceEntry = "jvm.profiler.ste";
    protected String metricJvmThreadAllocation = "jvm.thread.allocation";
    protected String metricJvmThreadCpu = "jvm.thread.cpu";
    protected String metricJvmThreadCpuUser = "jvm.thread.cpu.user";
    protected String metricJvmThreadCpuNanos = "jvm.thread.cpu.nanos";
    protected String metricJvmThreadCpuUserNanos = "jvm.thread.cpu.user.nanos";
    protected String metricJvmMemoryPoolCommitted = "jvm.mp.committed";
    protected String metricJvmMemoryPoolInit = "jvm.mp.init";
    protected String metricJvmMemoryPoolMax = "jvm.mp.max";
    protected String metricJvmMemoryPoolUsed = "jvm.mp.used";
    protected String metricJvmBuffersCount = "jvm.buffers.count";
    protected String metricJvmBuffersMemoryUsed = "jvm.buffers.memory_used";
    protected String metricJvmBuffersCapacity = "jvm.buffers.capacity";

    protected String metricCacheSize = "cache.size";
    protected String metricCacheHeap = "cache.heap";
    protected String metricCacheLatency = "cache.latency";
    protected String metricCacheLatencyTotal = "cache.latency.total";
    protected String metricCacheCount = "cache.count";
    protected String metricCacheCountTotal = "cache.count.total";
    protected boolean withJvmGcMetrics = true;
    protected boolean withJvmBasicMemoryMetrics = true;
    protected boolean withJvmBuffersMetrics = true;
    protected boolean withJvmMemoryPoolMetrics = true;
    protected boolean withJvmMemoryMetrics = true;
    protected boolean withJvmClassLoadingMetrics = true;
    protected boolean withJvmCompilationMetrics = true;
    protected boolean withJvmThreadMetrics = true;
    protected boolean withCacheMetrics = true;
    protected boolean withSystemMetrics = false;

    public JvmMonitoring(Recorder recorder) {
        this.recorder = recorder;
    }

    public JvmMonitoring() {
    }

    public void setRecorder(Recorder recorder) {
        if (this.recorder != null)
            throw new IllegalStateException("Recorder was already set");

        this.recorder = recorder;
    }

    public interface Recordable {
        void record(Recorder recorder);

        boolean isValid();
    }

    public boolean isStarted() {
        return true;
    }

    public JvmMonitoring addCustomThreadGroupNameResolver(Filter<String> filter, String groupName) {
        customThreadGroupNames.add(new Pair<Filter<String>, String>(filter, groupName));
        return this;
    }

    public JvmMonitoring setProfilerEnabled(Boolean profilerEnabled) {
        this.profilerEnabled = profilerEnabled;
        if (!profilerEnabled && profiler != null) {
            profiler.stopProfiling();
        }
        return this;
    }

    public void add(String name, Recordable recordable) {
        boolean put = cache.putIfAbsent(name, recordable);
        if (!put)
            throw new IllegalStateException("Recordable with name '" + name + "' is already registered");
    }

    public void init() {
        cache = new Cache<String, Recordable>("monitoring", interval) {
            @Override
            public void onRemoveItem(String name, Recordable recordable) {
                recordable.record(recorder);
                if (recordable.isValid())
                    put(name, recordable);
            }
        };

        if (withJvmGcMetrics)
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                cache.put(gc.getName(), new GcStats(gc, this));
            }

        if (withJvmBasicMemoryMetrics)
            cache.put("jvm.memory", new Recordable() {
                @Override
                public void record(Recorder recorder) {
                    Runtime rt = Runtime.getRuntime();
                    long freeMemory = rt.freeMemory();
                    long totalMemory = rt.totalMemory();
                    recorder.gauge(metricJvmMemoryFree, freeMemory);
                    recorder.gauge(metricJvmMemoryTotal, totalMemory);
                    recorder.gauge(metricJvmMemoryUsed, totalMemory - freeMemory);
                    recorder.gauge(metricJvmMemoryMax, rt.maxMemory());
                }

                @Override
                public boolean isValid() {
                    return true;
                }
            });

        if (withJvmBuffersMetrics) {
            List<BufferPoolMXBean> bufferPools = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
            for (final BufferPoolMXBean bufferPool : bufferPools) {
                cache.put("jvm.buffer." + bufferPool.getName(), new Recordable() {

                    Recorder.Tags tags = getTags(bufferPool);

                    @Override
                    public void record(Recorder recorder) {
                        recorder.gauge(metricJvmBuffersCount, bufferPool.getCount(), tags);
                        recorder.gauge(metricJvmBuffersMemoryUsed, bufferPool.getMemoryUsed(), tags);
                        recorder.gauge(metricJvmBuffersCapacity, bufferPool.getTotalCapacity(), tags);
                    }

                    @Override
                    public boolean isValid() {
                        return true;
                    }
                });
            }
        }

        if (withJvmMemoryPoolMetrics)
            for (MemoryPoolMXBean memoryMXBean : ManagementFactory.getMemoryPoolMXBeans()) {
                cache.put(memoryMXBean.getName(), new MemoryPoolStats(memoryMXBean, this));
            }

        if (withJvmMemoryMetrics) {
            MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
            cache.put("jvm.mem.heap", new MemoryStats(memoryMXBean.getHeapMemoryUsage(), this, Recorder.Tags.of("type", "heap")));
            cache.put("jvm.mem.nonheap", new MemoryStats(memoryMXBean.getNonHeapMemoryUsage(), this, Recorder.Tags.of("type", "nonheap")));
        }


        if (withJvmClassLoadingMetrics)
            cache.put("classLoading", new Recordable() {
                final ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();

                @Override
                public void record(Recorder recorder) {
                    recorder.gauge(metricJvmClassesLoaded, classLoadingMXBean.getLoadedClassCount());
                    recorder.gauge(metricJvmTotal, classLoadingMXBean.getTotalLoadedClassCount());
                    recorder.gauge(metricJvmClassesUnloaded, classLoadingMXBean.getUnloadedClassCount());
                }

                @Override
                public boolean isValid() {
                    return true;
                }
            });

        if (withJvmCompilationMetrics)
            cache.put("compilation", new Recordable() {

                final CompilationMXBean compilationMXBean = ManagementFactory.getCompilationMXBean();

                @Override
                public void record(Recorder recorder) {
                    recorder.gauge(metricJvmCompilationTime, compilationMXBean.getTotalCompilationTime());
                }

                @Override
                public boolean isValid() {
                    return true;
                }
            });

        if (withJvmThreadMetrics) {
            com.sun.management.ThreadMXBean threadMXBean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            if (threadMXBean.isThreadAllocatedMemorySupported() && threadMXBean.isThreadAllocatedMemoryEnabled()
                    && threadMXBean.isThreadCpuTimeSupported() && threadMXBean.isThreadCpuTimeEnabled()) {

                if (profilerEnabled)
                    profiler = createProfiler();

                cache.put("threading", new ThreadsStats(threadMXBean, this));
            }
        }

        if (withCacheMetrics) {
            final AtomicInteger counter = new AtomicInteger(0);
            CacheCleaner.addListener(new CacheCleaner.OnCacheAddedListener() {
                @Override
                public void onAdd(Cache c) {
                    cache.put("cache" + counter.incrementAndGet() + "." + c.getName(), createCacheStats(c));
                }
            });
            for (Cache c : CacheCleaner.iterable()) {
                cache.put("cache" + counter.incrementAndGet() + "." + c.getName(), createCacheStats(c));
            }
        }

        if (withSystemMetrics) {
            cache.put("CpuStat", new CpuStatReader().createRecordable());
            cache.put("DiskStat", new DiskStatsReader().createRecordable());
            cache.put("LoadStat", new LoadStatsReader().createRecordable());
            cache.put("MemoryStat", new MemoryStatsReader().createRecordable());
            cache.put("NetworkStat", new NetworkStatsReader().createRecordable());
        }
    }

    public boolean isWithJvmGcMetrics() {
        return withJvmGcMetrics;
    }

    public void setWithJvmGcMetrics(boolean withJvmGcMetrics) {
        this.withJvmGcMetrics = withJvmGcMetrics;
    }

    public boolean isWithJvmBasicMemoryMetrics() {
        return withJvmBasicMemoryMetrics;
    }

    public void setWithJvmBasicMemoryMetrics(boolean withJvmBasicMemoryMetrics) {
        this.withJvmBasicMemoryMetrics = withJvmBasicMemoryMetrics;
    }

    public boolean isWithJvmBuffersMetrics() {
        return withJvmBuffersMetrics;
    }

    public void setWithJvmBuffersMetrics(boolean withJvmBuffersMetrics) {
        this.withJvmBuffersMetrics = withJvmBuffersMetrics;
    }

    public boolean isWithJvmMemoryPoolMetrics() {
        return withJvmMemoryPoolMetrics;
    }

    public void setWithJvmMemoryPoolMetrics(boolean withJvmMemoryPoolMetrics) {
        this.withJvmMemoryPoolMetrics = withJvmMemoryPoolMetrics;
    }

    public boolean isWithJvmClassLoadingMetrics() {
        return withJvmClassLoadingMetrics;
    }

    public void setWithJvmClassLoadingMetrics(boolean withJvmClassLoadingMetrics) {
        this.withJvmClassLoadingMetrics = withJvmClassLoadingMetrics;
    }

    public boolean isWithJvmCompilationMetrics() {
        return withJvmCompilationMetrics;
    }

    public void setWithJvmCompilationMetrics(boolean withJvmCompilationMetrics) {
        this.withJvmCompilationMetrics = withJvmCompilationMetrics;
    }

    public boolean isWithJvmThreadMetrics() {
        return withJvmThreadMetrics;
    }

    public void setWithJvmThreadMetrics(boolean withJvmThreadMetrics) {
        this.withJvmThreadMetrics = withJvmThreadMetrics;
    }

    public boolean isWithCacheMetrics() {
        return withCacheMetrics;
    }

    public void setWithCacheMetrics(boolean withCacheMetrics) {
        this.withCacheMetrics = withCacheMetrics;
    }

    public boolean isWithSystemMetrics() {
        return withSystemMetrics;
    }

    public void setWithSystemMetrics(boolean withSystemMetrics) {
        this.withSystemMetrics = withSystemMetrics;
    }

    protected CacheStats createCacheStats(Cache cache) {
        return new CacheStats(cache.getStatistics(), this);
    }

    protected Profiler createProfiler() {
        Profiler profiler = new Profiler(this);
        profiler.addFilter(new Filter<StackTraceElement>() {
            @Override
            public boolean allow(StackTraceElement stackTraceElement) {
                return stackTraceElement.getClassName().startsWith("com.wizzardo.");
            }
        });
        profiler.start();
        return profiler;
    }

    public String resolveThreadGroupName(String threadName, String actualThreadGroupName) {
        for (Pair<Filter<String>, String> filterStringPair : customThreadGroupNames) {
            if (filterStringPair.key.allow(threadName))
                return filterStringPair.value;
        }
        return actualThreadGroupName;
    }

    public Profiler getProfiler() {
        if (profilerEnabled && profiler == null)
            profiler = createProfiler();

        return profiler;
    }

    public static ThreadGroup threadGroup(long threadId) {
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        while (group.getParent() != null)
            group = group.getParent();

        Thread[] arr = new Thread[group.activeCount()];
        int l = group.enumerate(arr);
        for (int i = 0; i < l; i++) {
            if (arr[i].getId() == threadId)
                return arr[i].getThreadGroup();
        }

        return group;
    }

    protected Recorder.Tags getTags(CacheStatistics statistics) {
        return Recorder.Tags.of("cache", statistics.getCacheName());
    }

    protected Recorder.Tags getTags(GarbageCollectorMXBean collector) {
        return Recorder.Tags.of("gc", collector.getName());
    }

    protected Recorder.Tags getTags(MemoryPoolMXBean memoryPool) {
        return Recorder.Tags.of("memoryPool", memoryPool.getName());
    }

    protected Recorder.Tags getTags(BufferPoolMXBean memoryPool) {
        return Recorder.Tags.of("buffer", memoryPool.getName());
    }

    protected Recorder.Tags getTags(ThreadsStats.TInfo info) {
        return Recorder.Tags.of("thread", info.name, "group", info.group, "id", String.valueOf(info.id));
    }

    public boolean isProfilerEnabled() {
        return profilerEnabled;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public String getMetricJvmMemoryFree() {
        return metricJvmMemoryFree;
    }

    public void setMetricJvmMemoryFree(String metricJvmMemoryFree) {
        this.metricJvmMemoryFree = metricJvmMemoryFree;
    }

    public String getMetricJvmMemoryTotal() {
        return metricJvmMemoryTotal;
    }

    public void setMetricJvmMemoryTotal(String metricJvmMemoryTotal) {
        this.metricJvmMemoryTotal = metricJvmMemoryTotal;
    }

    public String getMetricJvmMemoryUsed() {
        return metricJvmMemoryUsed;
    }

    public void setMetricJvmMemoryUsed(String metricJvmMemoryUsed) {
        this.metricJvmMemoryUsed = metricJvmMemoryUsed;
    }

    public String getMetricJvmMemoryMax() {
        return metricJvmMemoryMax;
    }

    public void setMetricJvmMemoryMax(String metricJvmMemoryMax) {
        this.metricJvmMemoryMax = metricJvmMemoryMax;
    }

    public String getMetricJvmGcCountTotal() {
        return metricJvmGcCountTotal;
    }

    public void setMetricJvmGcCountTotal(String metricJvmGcCountTotal) {
        this.metricJvmGcCountTotal = metricJvmGcCountTotal;
    }

    public String getMetricJvmGcTimeTotal() {
        return metricJvmGcTimeTotal;
    }

    public void setMetricJvmGcTimeTotal(String metricJvmGcTimeTotal) {
        this.metricJvmGcTimeTotal = metricJvmGcTimeTotal;
    }

    public String getMetricJvmGcCount() {
        return metricJvmGcCount;
    }

    public void setMetricJvmGcCount(String metricJvmGcCount) {
        this.metricJvmGcCount = metricJvmGcCount;
    }

    public String getMetricJvmGcTime() {
        return metricJvmGcTime;
    }

    public void setMetricJvmGcTime(String metricJvmGcTime) {
        this.metricJvmGcTime = metricJvmGcTime;
    }

    public String getMetricJvmClassesLoaded() {
        return metricJvmClassesLoaded;
    }

    public void setMetricJvmClassesLoaded(String metricJvmClassesLoaded) {
        this.metricJvmClassesLoaded = metricJvmClassesLoaded;
    }

    public String getMetricJvmTotal() {
        return metricJvmTotal;
    }

    public void setMetricJvmTotal(String metricJvmTotal) {
        this.metricJvmTotal = metricJvmTotal;
    }

    public String getMetricJvmClassesUnloaded() {
        return metricJvmClassesUnloaded;
    }

    public void setMetricJvmClassesUnloaded(String metricJvmClassesUnloaded) {
        this.metricJvmClassesUnloaded = metricJvmClassesUnloaded;
    }

    public String getMetricJvmCompilationTime() {
        return metricJvmCompilationTime;
    }

    public void setMetricJvmCompilationTime(String metricJvmCompilationTime) {
        this.metricJvmCompilationTime = metricJvmCompilationTime;
    }

    public String getMetricJvmThreadAlive() {
        return metricJvmThreadAlive;
    }

    public void setMetricJvmThreadAlive(String metricJvmThreadAlive) {
        this.metricJvmThreadAlive = metricJvmThreadAlive;
    }

    public String getMetricJvmProfilerStackTraceEntry() {
        return metricJvmProfilerStackTraceEntry;
    }

    public void setMetricJvmProfilerStackTraceEntry(String metricJvmProfilerStackTraceEntry) {
        this.metricJvmProfilerStackTraceEntry = metricJvmProfilerStackTraceEntry;
    }

    public String getMetricJvmThreadAllocation() {
        return metricJvmThreadAllocation;
    }

    public void setMetricJvmThreadAllocation(String metricJvmThreadAllocation) {
        this.metricJvmThreadAllocation = metricJvmThreadAllocation;
    }

    public String getMetricJvmThreadCpu() {
        return metricJvmThreadCpu;
    }

    public void setMetricJvmThreadCpu(String metricJvmThreadCpu) {
        this.metricJvmThreadCpu = metricJvmThreadCpu;
    }

    public String getMetricJvmThreadCpuUser() {
        return metricJvmThreadCpuUser;
    }

    public void setMetricJvmThreadCpuUser(String metricJvmThreadCpuUser) {
        this.metricJvmThreadCpuUser = metricJvmThreadCpuUser;
    }

    public String getMetricJvmThreadCpuNanos() {
        return metricJvmThreadCpuNanos;
    }

    public void setMetricJvmThreadCpuNanos(String metricJvmThreadCpuNanos) {
        this.metricJvmThreadCpuNanos = metricJvmThreadCpuNanos;
    }

    public String getMetricJvmThreadCpuUserNanos() {
        return metricJvmThreadCpuUserNanos;
    }

    public void setMetricJvmThreadCpuUserNanos(String metricJvmThreadCpuUserNanos) {
        this.metricJvmThreadCpuUserNanos = metricJvmThreadCpuUserNanos;
    }

    public String getMetricJvmMemoryPoolCommitted() {
        return metricJvmMemoryPoolCommitted;
    }

    public void setMetricJvmMemoryPoolCommitted(String metricJvmMemoryPoolCommitted) {
        this.metricJvmMemoryPoolCommitted = metricJvmMemoryPoolCommitted;
    }

    public String getMetricJvmMemoryPoolInit() {
        return metricJvmMemoryPoolInit;
    }

    public void setMetricJvmMemoryPoolInit(String metricJvmMemoryPoolInit) {
        this.metricJvmMemoryPoolInit = metricJvmMemoryPoolInit;
    }

    public String getMetricJvmMemoryPoolMax() {
        return metricJvmMemoryPoolMax;
    }

    public void setMetricJvmMemoryPoolMax(String metricJvmMemoryPoolMax) {
        this.metricJvmMemoryPoolMax = metricJvmMemoryPoolMax;
    }

    public String getMetricJvmMemoryPoolUsed() {
        return metricJvmMemoryPoolUsed;
    }

    public void setMetricJvmMemoryPoolUsed(String metricJvmMemoryPoolUsed) {
        this.metricJvmMemoryPoolUsed = metricJvmMemoryPoolUsed;
    }

    public String getMetricJvmBuffersCount() {
        return metricJvmBuffersCount;
    }

    public void setMetricJvmBuffersCount(String metricJvmBuffersCount) {
        this.metricJvmBuffersCount = metricJvmBuffersCount;
    }

    public String getMetricJvmBuffersMemoryUsed() {
        return metricJvmBuffersMemoryUsed;
    }

    public void setMetricJvmBuffersMemoryUsed(String metricJvmBuffersMemoryUsed) {
        this.metricJvmBuffersMemoryUsed = metricJvmBuffersMemoryUsed;
    }

    public String getMetricJvmBuffersCapacity() {
        return metricJvmBuffersCapacity;
    }

    public void setMetricJvmBuffersCapacity(String metricJvmBuffersCapacity) {
        this.metricJvmBuffersCapacity = metricJvmBuffersCapacity;
    }

    public String getMetricCacheSize() {
        return metricCacheSize;
    }

    public void setMetricCacheSize(String metricCacheSize) {
        this.metricCacheSize = metricCacheSize;
    }

    public String getMetricCacheHeap() {
        return metricCacheHeap;
    }

    public void setMetricCacheHeap(String metricCacheHeap) {
        this.metricCacheHeap = metricCacheHeap;
    }

    public String getMetricCacheLatency() {
        return metricCacheLatency;
    }

    public void setMetricCacheLatency(String metricCacheLatency) {
        this.metricCacheLatency = metricCacheLatency;
    }

    public String getMetricCacheLatencyTotal() {
        return metricCacheLatencyTotal;
    }

    public void setMetricCacheLatencyTotal(String metricCacheLatencyTotal) {
        this.metricCacheLatencyTotal = metricCacheLatencyTotal;
    }

    public String getMetricCacheCount() {
        return metricCacheCount;
    }

    public void setMetricCacheCount(String metricCacheCount) {
        this.metricCacheCount = metricCacheCount;
    }

    public String getMetricCacheCountTotal() {
        return metricCacheCountTotal;
    }

    public void setMetricCacheCountTotal(String metricCacheCountTotal) {
        this.metricCacheCountTotal = metricCacheCountTotal;
    }
}

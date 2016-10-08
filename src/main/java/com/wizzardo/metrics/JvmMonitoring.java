package com.wizzardo.metrics;

import com.wizzardo.tools.cache.Cache;
import com.wizzardo.tools.collections.Pair;
import com.wizzardo.tools.collections.flow.Filter;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by wizzardo on 06/09/16.
 */
public class JvmMonitoring {

    protected Recorder recorder;
    protected Cache<String, Recordable> cache;
    protected Profiler profiler;
    protected volatile boolean profilerEnabled = true;
    protected Queue<Pair<Filter<String>, String>> customThreadGroupNames = new ConcurrentLinkedQueue<>();
    protected int interval = 10;
    protected String metricJvmMemoryFree = "jvm.memory.free";
    protected String metricJvmMemoryTotal = "jvm.memory.total";
    protected String metricJvmMemoryUsed = "jvm.memory.used";
    protected String metricJvmMemoryMax = "jvm.memory.max";
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

    public void init() {
        cache = new Cache<String, Recordable>(interval) {
            @Override
            public void onRemoveItem(String name, Recordable recordable) {
                recordable.record(recorder);
                if (recordable.isValid())
                    put(name, recordable);
            }
        };

        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            cache.put(gc.getName(), new GcStats(gc, this));
        }

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

        for (MemoryPoolMXBean memoryMXBean : ManagementFactory.getMemoryPoolMXBeans()) {
            cache.put(memoryMXBean.getName(), new MemoryStats(memoryMXBean, this));
        }

        final ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
        cache.put("classLoading", new Recordable() {
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

        final CompilationMXBean compilationMXBean = ManagementFactory.getCompilationMXBean();
        cache.put("compilation", new Recordable() {
            @Override
            public void record(Recorder recorder) {
                recorder.gauge(metricJvmCompilationTime, compilationMXBean.getTotalCompilationTime());
            }

            @Override
            public boolean isValid() {
                return true;
            }
        });

        com.sun.management.ThreadMXBean threadMXBean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (threadMXBean.isThreadAllocatedMemorySupported() && threadMXBean.isThreadAllocatedMemoryEnabled()
                && threadMXBean.isThreadCpuTimeSupported() && threadMXBean.isThreadCpuTimeEnabled()) {
            if (profilerEnabled) {
                profiler = createProfiler();
                profiler.addFilter(new Filter<StackTraceElement>() {
                    @Override
                    public boolean allow(StackTraceElement stackTraceElement) {
                        return stackTraceElement.getClassName().startsWith("com.wizzardo.");
                    }
                });
                profiler.start();
            }

            cache.put("threading", new ThreadsStats(threadMXBean, profiler, this));
        }
    }

    protected Profiler createProfiler() {
        return new Profiler(recorder, this);
    }

    public String resolveThreadGroupName(String threadName, String actualThreadGroupName) {
        for (Pair<Filter<String>, String> filterStringPair : customThreadGroupNames) {
            if (filterStringPair.key.allow(threadName))
                return filterStringPair.value;
        }
        return actualThreadGroupName;
    }

    public Profiler getProfiler() {
        return profiler;
    }

    public static class Counter {
        int value = 0;

        public void increment() {
            value++;
        }

        public int get() {
            return value;
        }
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

    static public class Profiler extends Thread {

        Recorder recorder;
        ThreadMXBean threadMXBean;
        final Set<Long> profilingThreads = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
        Set<Filter<StackTraceElement>> filters = Collections.newSetFromMap(new ConcurrentHashMap<Filter<StackTraceElement>, Boolean>());
        volatile int pause = 5;
        volatile int cycles = 1;
        JvmMonitoring jvmMonitoring;

        public Profiler(Recorder recorder, JvmMonitoring jvmMonitoring) {
            super("Profiler");
            this.jvmMonitoring = jvmMonitoring;
            this.recorder = recorder;
            threadMXBean = ManagementFactory.getThreadMXBean();
            setDaemon(true);
        }

        public void setPause(int pause) {
            this.pause = pause;
        }

        public void setCycles(int cycles) {
            this.cycles = cycles;
        }

        @Override
        public void run() {
            long[] ids = new long[0];
            Map<StackTraceEntry, Counter> samples = new HashMap<>();
            long time;
            long nextPrint = getNextPrintTime();
            int pause = this.pause;
            int cycles = this.cycles;
            while (true) {
                while (ids.length == 0) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }
                    ids = getThreadsToProfile();
                    pause = this.pause;
                    cycles = this.cycles;
                    nextPrint = getNextPrintTime();
                }

                try {
                    Thread.sleep(pause);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                for (int j = 0; j < cycles; j++) {
                    for (ThreadInfo threadInfo : threadMXBean.getThreadInfo(ids, Integer.MAX_VALUE)) {
                        if (threadInfo == null)
                            continue;

                        StackTraceElement[] stackTrace = threadInfo.getStackTrace();
                        int length = stackTrace.length;
                        for (int i = 0; i < length; i++) {
                            StackTraceElement element = stackTrace[i];
                            if (!filter(element))
                                continue;

                            StackTraceEntry stackTraceEntry = new StackTraceEntry(threadInfo.getThreadName(), threadGroup(threadInfo.getThreadId()).getName(), element.getClassName(), element.getMethodName(), length - i);
                            Counter counter = samples.get(stackTraceEntry);
                            if (counter == null) {
                                samples.put(stackTraceEntry, counter = new Counter());
                            }
                            counter.increment();
                        }
                    }
                }

                time = System.nanoTime();
                if (time >= nextPrint) {
                    for (Map.Entry<StackTraceEntry, Counter> mapEntry : filter(samples.entrySet())) {
                        recorder.gauge(jvmMonitoring.metricJvmProfilerStackTraceEntry, mapEntry.getValue().get(), jvmMonitoring.getTags(mapEntry.getKey()));
                    }
                    samples.clear();
                    ids = getThreadsToProfile();
                    pause = this.pause;
                    cycles = this.cycles;
                    nextPrint = getNextPrintTime();
                }
            }
        }

        protected Iterable<Map.Entry<StackTraceEntry, Counter>> filter(Set<Map.Entry<StackTraceEntry, Counter>> samples) {
            return samples;
        }

        private long[] getThreadsToProfile() {
            if (!profilingThreads.isEmpty()) {
                ArrayList<Long> longs = new ArrayList<>(profilingThreads.size());
                for (Long id : profilingThreads) {
                    longs.add(id);
                }
                long[] ids = new long[longs.size()];
                for (int i = 0; i < longs.size(); i++) {
                    ids[i] = longs.get(i);
                }
                return ids;
            } else
                return new long[0];
        }

        private boolean filter(StackTraceElement element) {
            for (Filter<StackTraceElement> filter : filters) {
                if (filter.allow(element))
                    return true;
            }
            return false;
        }

        private long getNextPrintTime() {
            return System.nanoTime() + 10_000_000_000L;
        }

        public void startProfiling(long id) {
            profilingThreads.add(id);
        }

        public void stopProfiling(long id) {
            profilingThreads.remove(id);
        }

        public void stopProfiling() {
            profilingThreads.clear();
        }

        public void addFilter(Filter<StackTraceElement> filter) {
            filters.add(filter);
        }

        public void clearFilters() {
            filters.clear();
        }

        public static class StackTraceEntry implements Comparable<StackTraceEntry> {
            public final String thread;
            public final String group;
            public final String declaringClass;
            public final String methodName;
            public final int depth;

            StackTraceEntry(String thread, String group, String declaringClass, String methodName, int depth) {
                this.thread = thread;
                this.group = group;
                this.declaringClass = declaringClass;
                this.methodName = methodName;
                this.depth = depth;
            }

            @Override
            public String toString() {
                return depth + ": " + declaringClass + "." + methodName;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                StackTraceEntry that = (StackTraceEntry) o;

                if (depth != that.depth) return false;
                if (!thread.equals(that.thread)) return false;
                if (!declaringClass.equals(that.declaringClass)) return false;
                return methodName.equals(that.methodName);

            }

            @Override
            public int hashCode() {
                int result = thread.hashCode();
                result = 31 * result + declaringClass.hashCode();
                result = 31 * result + methodName.hashCode();
                result = 31 * result + depth;
                return result;
            }

            @Override
            public int compareTo(StackTraceEntry o) {
                return Integer.compare(depth, o.depth);
            }
        }
    }

    public static class ThreadsStats implements Recordable {
        com.sun.management.ThreadMXBean threadMXBean;
        Profiler profiler;
        Map<Long, TInfo> threads = new HashMap<>(32, 1);
        int tickCounter = 0;
        JvmMonitoring jvmMonitoring;

        @Override
        public boolean isValid() {
            return true;
        }

        public static class TInfo {
            String name;
            String group;
            long id;
            long bytesAllocated;
            long cpuTime;
            long userTime;
            int tick;
            long lastRecord;
            boolean profiling;
            boolean profilingDisabled = false;
            Recorder.Tags tags;
        }

        public ThreadsStats(com.sun.management.ThreadMXBean threadMXBean, Profiler profiler, JvmMonitoring jvmMonitoring) {
            this.threadMXBean = threadMXBean;
            this.profiler = profiler;
            this.jvmMonitoring = jvmMonitoring;
        }

        @Override
        public void record(Recorder recorder) {
            tickCounter++;

            long[] ids = threadMXBean.getAllThreadIds();
            long[] allocatedBytes = threadMXBean.getThreadAllocatedBytes(ids);
            long[] threadUserTime = threadMXBean.getThreadUserTime(ids);
            long[] threadCpuTime = threadMXBean.getThreadCpuTime(ids);
            long now = System.nanoTime();

            recorder.gauge(jvmMonitoring.metricJvmThreadAlive, ids.length);

            for (int i = 0; i < ids.length; i++) {
                long id = ids[i];
                long bytesAllocated = allocatedBytes[i];
                long cpuTime = threadCpuTime[i];
                long userTime = threadUserTime[i];
                if (cpuTime < 0) {
                    threads.remove(id);
                    continue;
                }

                TInfo tInfo = threads.get(id);
                if (tInfo == null) {
                    tInfo = new TInfo();
                    tInfo.id = id;
                    threads.put(id, tInfo);
                    tInfo.name = threadMXBean.getThreadInfo(tInfo.id).getThreadName();
                    tInfo.group = threadGroup(id).getName();
                    if ("main".equalsIgnoreCase(tInfo.group) || "system".equalsIgnoreCase(tInfo.group))
                        tInfo.group = jvmMonitoring.resolveThreadGroupName(tInfo.name, tInfo.group);

                    tInfo.tags = jvmMonitoring.getTags(tInfo);
                    if (tInfo.name.equals("DestroyJavaVM") || tInfo.name.equals("Profiler"))
                        tInfo.profilingDisabled = true;
                } else {
                    recorder.histogram(jvmMonitoring.metricJvmThreadAllocation, bytesAllocated - tInfo.bytesAllocated, tInfo.tags);
                    recorder.histogram(jvmMonitoring.metricJvmThreadCpu, (cpuTime - tInfo.cpuTime) * 100d / (now - tInfo.lastRecord), tInfo.tags);
                    recorder.histogram(jvmMonitoring.metricJvmThreadCpuUser, (userTime - tInfo.userTime) * 100d / (now - tInfo.lastRecord), tInfo.tags);
                    recorder.histogram(jvmMonitoring.metricJvmThreadCpuNanos, (cpuTime - tInfo.cpuTime), tInfo.tags);
                    recorder.histogram(jvmMonitoring.metricJvmThreadCpuUserNanos, (userTime - tInfo.userTime), tInfo.tags);
                }

                if (jvmMonitoring.profilerEnabled && !tInfo.profilingDisabled) {
                    if (tInfo.lastRecord != 0 && (cpuTime - tInfo.cpuTime) * 100d / (now - tInfo.lastRecord) >= 5) {
                        if (!tInfo.profiling) {
                            profiler.startProfiling(tInfo.id);
                            tInfo.profiling = true;
                        }
                    } else if (tInfo.profiling) {
                        profiler.stopProfiling(tInfo.id);
                        tInfo.profiling = false;
                    }
                }

                tInfo.bytesAllocated = bytesAllocated;
                tInfo.cpuTime = cpuTime;
                tInfo.userTime = userTime;
                tInfo.tick = tickCounter;
                tInfo.lastRecord = now;
            }

            if (tickCounter >= 30) {
                Iterator<Map.Entry<Long, TInfo>> iterator = threads.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Long, TInfo> next = iterator.next();
                    if (next.getValue().tick != tickCounter)
                        iterator.remove();
                }
                tickCounter = 0;
            }
        }
    }

    static class GcStats implements Recordable {

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

    static class MemoryStats implements Recordable {

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

    protected Recorder.Tags getTags(GarbageCollectorMXBean collector) {
        return Recorder.Tags.of("gc", collector.getName());
    }

    protected Recorder.Tags getTags(MemoryPoolMXBean memoryPool) {
        return Recorder.Tags.of("memoryPool", memoryPool.getName());
    }

    protected Recorder.Tags getTags(Profiler.StackTraceEntry ste) {
        return Recorder.Tags.of(
                "thread", ste.thread,
                "group", ste.group,
                "entry", ste.depth + "-" + ste.declaringClass + "." + ste.methodName
        );
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
}

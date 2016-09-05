package com.wizzardo.metrics;

import com.wizzardo.tools.cache.Cache;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by wizzardo on 06/09/16.
 */
public class JvmMonitoring {

    private Recorder recorder;

    private Cache<String, Recordable> cache;

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

    interface Recordable {
        void record(Recorder recorder);

        boolean isValid();
    }

    public boolean isStarted() {
        return true;
    }

    public void init() {
        cache = new Cache<String, Recordable>(10) {
            @Override
            public void onRemoveItem(String name, Recordable recordable) {
                recordable.record(recorder);
                if (recordable.isValid())
                    put(name, recordable);
            }
        };

        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            cache.put(gc.getName(), new GcStats(gc));
        }

        cache.put("jvm.memory", new Recordable() {
            @Override
            public void record(Recorder recorder) {
                Runtime rt = Runtime.getRuntime();
                long freeMemory = rt.freeMemory();
                long totalMemory = rt.totalMemory();
                recorder.gauge("jvm.memory.free", freeMemory);
                recorder.gauge("jvm.memory.total", totalMemory);
                recorder.gauge("jvm.memory.used", totalMemory - freeMemory);
                recorder.gauge("jvm.memory.max", rt.maxMemory());
            }

            @Override
            public boolean isValid() {
                return true;
            }
        });

        for (MemoryPoolMXBean memoryMXBean : ManagementFactory.getMemoryPoolMXBeans()) {
            cache.put(memoryMXBean.getName(), new MemoryStats(memoryMXBean));
        }

        final ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
        cache.put("classLoading", new Recordable() {
            @Override
            public void record(Recorder recorder) {
                recorder.gauge("jvm.classes.loaded", classLoadingMXBean.getLoadedClassCount());
                recorder.gauge("jvm.classes.total", classLoadingMXBean.getTotalLoadedClassCount());
                recorder.gauge("jvm.classes.unloaded", classLoadingMXBean.getUnloadedClassCount());
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
                recorder.gauge("jvm.compilation.time", compilationMXBean.getTotalCompilationTime());
            }

            @Override
            public boolean isValid() {
                return true;
            }
        });

        com.sun.management.ThreadMXBean threadMXBean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        System.out.println("isThreadAllocatedMemorySupported: " + threadMXBean.isThreadAllocatedMemorySupported());
        if (threadMXBean.isThreadAllocatedMemorySupported())
            System.out.println("isThreadAllocatedMemoryEnabled: " + threadMXBean.isThreadAllocatedMemoryEnabled());

        System.out.println("isThreadCpuTimeSupported: " + threadMXBean.isThreadCpuTimeSupported());
        if (threadMXBean.isThreadCpuTimeSupported())
            System.out.println("isThreadCpuTimeEnabled: " + threadMXBean.isThreadCpuTimeEnabled());

        if (threadMXBean.isThreadAllocatedMemorySupported() && threadMXBean.isThreadAllocatedMemoryEnabled() && threadMXBean.isThreadCpuTimeSupported() && threadMXBean.isThreadCpuTimeEnabled()) {
            Profiler profiler = new Profiler(recorder);
            profiler.addFilter(new Filter<StackTraceElement>() {
                @Override
                public boolean allow(StackTraceElement stackTraceElement) {
                    return stackTraceElement.getClassName().startsWith("com.bonial.");
                }
            });
            profiler.addFilter(new Filter<StackTraceElement>() {
                @Override
                public boolean allow(StackTraceElement stackTraceElement) {
                    return stackTraceElement.getClassName().startsWith("com.wizzardo.");
                }
            });
            profiler.addFilter(new Filter<StackTraceElement>() {
                @Override
                public boolean allow(StackTraceElement stackTraceElement) {
                    return stackTraceElement.getClassName().startsWith("de.kaufda.");
                }
            });
            profiler.start();
            cache.put("threading", new ThreadsStats(threadMXBean, profiler));
        }
    }

    static class Counter {
        int value = 0;

        public void increment() {
            value++;
        }

        public int get() {
            return value;
        }
    }

    public interface Filter<T> {
        boolean allow(T t);
    }

    static class Profiler extends Thread {

        Recorder recorder;
        ThreadMXBean threadMXBean;
        final Set<Long> profilingThreads = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
        List<Filter<StackTraceElement>> filters = new ArrayList<>();

        public Profiler(Recorder recorder) {
            super("Profiler");
            threadMXBean = ManagementFactory.getThreadMXBean();
            this.recorder = recorder;
            setDaemon(true);
        }

        @Override
        public void run() {
            long[] ids = new long[0];
            Map<StackTraceEntry, Counter> samples = new HashMap<>();
            long time;
            long nextPrint = getNextPrintTime();
            while (true) {
                while (ids.length == 0) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }
                    ids = getThreadsToProfile();
                    nextPrint = getNextPrintTime();
                }

                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                for (int j = 0; j < 1; j++) {
                    for (ThreadInfo threadInfo : threadMXBean.getThreadInfo(ids, Integer.MAX_VALUE)) {
                        if (threadInfo == null)
                            continue;

                        StackTraceElement[] stackTrace = threadInfo.getStackTrace();
                        int length = stackTrace.length;
                        for (int i = 0; i < length; i++) {
                            StackTraceElement element = stackTrace[i];
                            if (!filter(element))
                                continue;

                            StackTraceEntry stackTraceEntry = new StackTraceEntry(threadInfo.getThreadName(), element.getClassName(), element.getMethodName(), length - i);
//                            System.out.println(stackTraceEntry);
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
//                    List<Map.Entry<StackTraceEntry, Counter>> entries = new ArrayList<>(samples.size());
//                    samples.forEach((entry, samplesCounter) -> System.out.println(entry.toString() + ": " + samplesCounter.get() + " samples"));
                    for (Map.Entry<StackTraceEntry, Counter> mapEntry : samples.entrySet()) {
                        recorder.gauge("jvm.profiler.ste1", mapEntry.getValue().get(),
                                Recorder.Tags.of(
                                        "thread", mapEntry.getKey().thread,
//                                    "class", entry.declaringClass,
//                                    "method", entry.methodName,
                                        "entry", mapEntry.getKey().depth + "-" + mapEntry.getKey().declaringClass + "." + mapEntry.getKey().methodName
//                                    "depth", String.valueOf(entry.depth)
                                ));
                    }
//                    samples.forEach((entry, samplesCounter) -> System.out.println(entry.toString() + ": " + samplesCounter.get() + " samples"));
//                    samples.entrySet().forEach(entries::add);
//                    Collections.sort(entries, (o1, o2) -> o1.getKey().compareTo(o2.getKey()));
//                    entries.forEach(entry -> System.out.println(entry.getKey().toString() + ": " + entry.getValue().get() + " samples"));
                    samples.clear();
                    ids = getThreadsToProfile();
                    nextPrint = getNextPrintTime();
                }
            }
        }

        private long[] getThreadsToProfile() {
            if (!profilingThreads.isEmpty()) {
                ArrayList<Long> longs = new ArrayList<>(profilingThreads.size());
                for (Long id : profilingThreads) {
                    longs.add(id);
                }
                System.out.println("going to profile these threads: " + longs);
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

        private static long getNextPrintTime() {
            return System.nanoTime() + 10_000_000_000L;
        }

        public void startProfiling(long id) {
            profilingThreads.add(id);
        }

        public void stopProfiling(long id) {
            profilingThreads.remove(id);
        }

        public void addFilter(Filter<StackTraceElement> filter) {
            filters.add(filter);
        }

        public static class StackTraceEntry implements Comparable<StackTraceEntry> {
            public final String thread;
            public final String declaringClass;
            public final String methodName;
            public final int depth;

            StackTraceEntry(String thread, String declaringClass, String methodName, int depth) {
                this.thread = thread;
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

    static class ThreadsStats implements Recordable {
        com.sun.management.ThreadMXBean threadMXBean;
        Profiler profiler;
        Map<Long, TInfo> threads = new HashMap<>(32, 1);
        int tickCounter = 0;

        @Override
        public boolean isValid() {
            return true;
        }

        static class TInfo {
            String name;
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

        public ThreadsStats(com.sun.management.ThreadMXBean threadMXBean, Profiler profiler) {
            this.threadMXBean = threadMXBean;
            this.profiler = profiler;
        }

        @Override
        public void record(Recorder recorder) {
            tickCounter++;

            long[] ids = threadMXBean.getAllThreadIds();
            long[] allocatedBytes = threadMXBean.getThreadAllocatedBytes(ids);
            long[] threadUserTime = threadMXBean.getThreadUserTime(ids);
            long[] threadCpuTime = threadMXBean.getThreadCpuTime(ids);

            recorder.gauge("jvm.thread.alive", ids.length);

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
                    tInfo.tags = Recorder.Tags.of("thread", tInfo.name, "id", String.valueOf(id));
                    if (tInfo.name.equals("DestroyJavaVM") || tInfo.name.equals("Profiler"))
                        tInfo.profilingDisabled = true;
                } else {
                    recorder.recValue("jvm.thread.allocation", bytesAllocated - tInfo.bytesAllocated, tInfo.tags);
                    recorder.recValue("jvm.thread.cpu", (cpuTime - tInfo.cpuTime) / 1000 / 1000, tInfo.tags);
                    recorder.recValue("jvm.thread.cpu.user", (userTime - tInfo.userTime) / 1000 / 1000, tInfo.tags);
                }

                long time = System.nanoTime();
                if (!tInfo.profilingDisabled) {
                    if (tInfo.lastRecord != 0 && (cpuTime - tInfo.cpuTime) * 100d / (time - tInfo.lastRecord) >= 5) {
                        System.out.println("start profiling thread " + tInfo.name + ": " + (cpuTime - tInfo.cpuTime) * 100d / (time - tInfo.lastRecord));
                        if (!tInfo.profiling) {
                            profiler.startProfiling(tInfo.id);
                            tInfo.profiling = true;
                        }
                    } else if (tInfo.profiling) {
                        profiler.stopProfiling(tInfo.id);
                        System.out.println("stop profiling thread " + tInfo.name);
                        tInfo.profiling = false;
                    }
                }

                tInfo.bytesAllocated = bytesAllocated;
                tInfo.cpuTime = cpuTime;
                tInfo.userTime = userTime;
                tInfo.tick = tickCounter;
                tInfo.lastRecord = time;
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

        public GcStats(GarbageCollectorMXBean collector) {
            this.collector = collector;
            tags = Recorder.Tags.of("gc", collector.getName());
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
            recorder.gauge("jvm.gc.countTotal", collector.getCollectionCount(), tags);
            recorder.gauge("jvm.gc.timeTotal", collector.getCollectionTime(), tags);
            recorder.gauge("jvm.gc.count", getCollectionCountDiff(), tags);
            recorder.rec("jvm.gc.time", getCollectionTimeDiff(), tags);
        }

        @Override
        public boolean isValid() {
            return true;
        }
    }

    static class MemoryStats implements Recordable {

        private MemoryPoolMXBean memoryPool;
        private Recorder.Tags tags;

        public MemoryStats(MemoryPoolMXBean memoryPool) {
            this.memoryPool = memoryPool;
            tags = Recorder.Tags.of("memoryPool", memoryPool.getName());
        }

        @Override
        public void record(Recorder recorder) {
            if (!isValid())
                return;

            recorder.gauge("jvm.mp.committed", memoryPool.getUsage().getCommitted(), tags);
            recorder.gauge("jvm.mp.init", memoryPool.getUsage().getInit(), tags);
            recorder.gauge("jvm.mp.max", memoryPool.getUsage().getMax(), tags);
            recorder.gauge("jvm.mp.used", memoryPool.getUsage().getUsed(), tags);
        }

        @Override
        public boolean isValid() {
            return memoryPool.isValid();
        }
    }
}

package com.wizzardo.metrics;

import com.wizzardo.tools.collections.flow.Filter;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by wizzardo on 08/10/16.
 */
public class Profiler extends Thread {

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

    public static class Counter {
        int value = 0;

        public void increment() {
            value++;
        }

        public int get() {
            return value;
        }
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

                        String groupName = jvmMonitoring.resolveThreadGroupName(threadInfo.getThreadName(), JvmMonitoring.threadGroup(threadInfo.getThreadId()).getName());
                        StackTraceEntry stackTraceEntry = new StackTraceEntry(threadInfo.getThreadName(), groupName, element.getClassName(), element.getMethodName(), length - i);
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

    protected boolean filter(StackTraceElement element) {
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

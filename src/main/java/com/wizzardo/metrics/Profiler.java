package com.wizzardo.metrics;

import com.wizzardo.tools.interfaces.BiConsumer;
import com.wizzardo.tools.interfaces.Filter;
import com.wizzardo.tools.misc.Pair;

import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by wizzardo on 08/10/16.
 */
public class Profiler extends Thread {

    ThreadMXBean threadMXBean;
    final Set<Long> profilingThreads = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    Set<Filter<StackTraceElement>> filters = Collections.newSetFromMap(new ConcurrentHashMap<Filter<StackTraceElement>, Boolean>());
    volatile int pause = 5;
    volatile int cycles = 1;
    volatile long durationNanos = 10_000_000_000l;
    JvmMonitoring jvmMonitoring;
    BiConsumer<SimpleThreadInfo, StackTraceEntry> resultHandler;

    public Profiler(JvmMonitoring jvmMonitoring) {
        super("Profiler");
        this.jvmMonitoring = jvmMonitoring;
        threadMXBean = ManagementFactory.getThreadMXBean();
        setDaemon(true);
        addFilter(new Filter<StackTraceElement>() {
            @Override
            public boolean allow(StackTraceElement stackTraceElement) {
                return true;
            }
        });
    }

    public static class SimpleThreadInfo {
        public final long id;
        public final String name;
        public final String group;

        public SimpleThreadInfo(ThreadInfo threadInfo, JvmMonitoring jvmMonitoring) {
            id = threadInfo.getThreadId();
            name = threadInfo.getThreadName();
            group = jvmMonitoring.resolveThreadGroupName(threadInfo.getThreadName(), JvmMonitoring.threadGroup(threadInfo.getThreadId()).getName());
        }
    }

    public void setResultHandler(BiConsumer<SimpleThreadInfo, StackTraceEntry> resultHandler) {
        this.resultHandler = resultHandler;
    }

    public void setDuration(long durationNanos) {
        this.durationNanos = durationNanos;
    }

    public long getDuration() {
        return durationNanos;
    }

    public void setPause(int pause) {
        this.pause = pause;
    }

    public int getPause() {
        return pause;
    }

    public void setCycles(int cycles) {
        this.cycles = cycles;
    }

    public int getCycles() {
        return cycles;
    }

    @Override
    public void run() {
        long[] ids = new long[0];
        Map<Long, Pair<SimpleThreadInfo, StackTraceEntry>> samples = new HashMap<>();
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
                    Pair<SimpleThreadInfo, StackTraceEntry> pair = samples.get(threadInfo.getThreadId());
                    if (pair == null)
                        samples.put(threadInfo.getThreadId(), pair = new Pair<>(new SimpleThreadInfo(threadInfo, jvmMonitoring), new StackTraceEntry()));

                    StackTraceEntry entry = pair.value;

                    int length = stackTrace.length;
                    for (int i = length - 1; i >= 0; i--) {
                        StackTraceElement element = stackTrace[i];
                        if (!filter(element))
                            continue;

                        entry = entry.getChild(element.getClassName(), element.getMethodName());
                    }
                }
            }

            time = System.nanoTime();
            if (time >= nextPrint) {
                for (Pair<SimpleThreadInfo, StackTraceEntry> pair : samples.values()) {
                    SimpleThreadInfo threadInfo = pair.key;
                    StackTraceEntry value = pair.value;

                    handleResult(threadInfo, value);
                }
                samples.clear();
                ids = getThreadsToProfile();
                pause = this.pause;
                cycles = this.cycles;
                nextPrint = getNextPrintTime();
            }
        }
    }

    protected void handleResult(SimpleThreadInfo threadInfo, StackTraceEntry profile) {
        if (resultHandler != null)
            resultHandler.consume(threadInfo, profile);
    }

    /**
     * Prints {@link StackTraceEntry} to {@link PrintStream} in <a href="https://github.com/brendangregg/FlameGraph">FlameGraph</a> format
     **/
    public void printProfile(StackTraceEntry root, PrintStream out) {
        record(root, new StringBuilder(), out);
    }

    private void record(StackTraceEntry entry, StringBuilder sb, PrintStream out) {
        int lengthBefore = sb.length();
        if (entry.children != null) {
            for (Map.Entry<String, Map<String, StackTraceEntry>> stringMapEntry : entry.children.entrySet()) {
                String declaringClass = stringMapEntry.getKey();
                if (sb.length() > 0)
                    sb.append(';');

                sb.append(declaringClass).append('.');
                int lengthBeforeMethod = sb.length();
                Map<String, StackTraceEntry> methods = stringMapEntry.getValue();
                for (Map.Entry<String, StackTraceEntry> entryEntry : methods.entrySet()) {
                    String method = entryEntry.getKey();
                    StackTraceEntry value = entryEntry.getValue();
                    sb.append(method);
                    int lengthBeforeValue = sb.length();
                    record(sb.append(' ').append(value.value).toString(), out);

                    sb.setLength(lengthBeforeValue);
                    record(value, sb, out);
                    sb.setLength(lengthBeforeMethod);
                }

                sb.setLength(lengthBefore);
            }
        }
    }

    private void record(String s, PrintStream out) {
        out.println(s);
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

    protected long getNextPrintTime() {
        return System.nanoTime() + durationNanos;
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

    public static class StackTraceEntry {
        public final String declaringClass;
        public final String methodName;
        public int value;
        public Map<String, Map<String, StackTraceEntry>> children;

        StackTraceEntry(String declaringClass, String methodName) {
            this.declaringClass = declaringClass;
            this.methodName = methodName;
        }

        StackTraceEntry() {
            this(null, null);
        }

        public StackTraceEntry getChild(String declaringClass, String methodName) {
            if (children == null)
                children = new HashMap<>(16, 1f);

            Map<String, StackTraceEntry> map = children.get(declaringClass);
            if (map == null)
                children.put(declaringClass, map = new HashMap<>());

            StackTraceEntry entry = map.get(methodName);
            if (entry == null)
                map.put(methodName, entry = new StackTraceEntry(declaringClass, methodName));

            entry.value++;
            return entry;
        }

        @Override
        public String toString() {
            return declaringClass + "." + methodName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            StackTraceEntry that = (StackTraceEntry) o;

            if (!declaringClass.equals(that.declaringClass)) return false;
            return methodName.equals(that.methodName);
        }

        @Override
        public int hashCode() {
            int result = declaringClass.hashCode();
            result = 31 * result + methodName.hashCode();
            return result;
        }
    }
}

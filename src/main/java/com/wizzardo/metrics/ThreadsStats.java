package com.wizzardo.metrics;

import java.lang.management.ThreadInfo;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by wizzardo on 08/10/16.
 */
public class ThreadsStats implements JvmMonitoring.Recordable {
    com.sun.management.ThreadMXBean threadMXBean;
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

    public ThreadsStats(com.sun.management.ThreadMXBean threadMXBean, JvmMonitoring jvmMonitoring) {
        this.threadMXBean = threadMXBean;
        this.jvmMonitoring = jvmMonitoring;
    }

    @Override
    public void record(Recorder recorder) {
        tickCounter++;
        boolean profilerEnabled = jvmMonitoring.profilerEnabled;

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
                ThreadInfo threadInfo = threadMXBean.getThreadInfo(tInfo.id);
                if (threadInfo == null)
                    continue;

                threads.put(id, tInfo);
                tInfo.name = threadInfo.getThreadName();
                tInfo.group = jvmMonitoring.resolveThreadGroupName(tInfo.name, JvmMonitoring.threadGroup(id).getName());

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

            if (profilerEnabled && !tInfo.profilingDisabled) {
                if (tInfo.lastRecord != 0 && (cpuTime - tInfo.cpuTime) * 100d / (now - tInfo.lastRecord) >= 5) {
                    if (!tInfo.profiling) {
                        jvmMonitoring.getProfiler().startProfiling(tInfo.id);
                        tInfo.profiling = true;
                    }
                } else if (tInfo.profiling) {
                    jvmMonitoring.getProfiler().stopProfiling(tInfo.id);
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

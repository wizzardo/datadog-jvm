package com.wizzardo.metrics;

import java.lang.management.ManagementFactory;

public class CpuAndAllocationStats {
    protected static final ThreadLocal<CpuAndAllocationStats> THREAD_LOCAL = new ThreadLocal<CpuAndAllocationStats>() {
        @Override
        protected CpuAndAllocationStats initialValue() {
            return new CpuAndAllocationStats();
        }
    };

    public static CpuAndAllocationStats get() {
        return THREAD_LOCAL.get();
    }

    public final boolean cpuTimeEnabled;
    public final boolean allocationEnabled;
    protected final long[] threadId;
    protected long cpuTime;
    protected long cpuUserTime;
    protected long allocation;
    protected com.sun.management.ThreadMXBean threadMXBean;


    public CpuAndAllocationStats() {
        threadId = new long[]{Thread.currentThread().getId()};
        threadMXBean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        cpuTimeEnabled = threadMXBean.isThreadCpuTimeSupported() && threadMXBean.isThreadCpuTimeEnabled();
        allocationEnabled = threadMXBean.isThreadAllocatedMemorySupported() && threadMXBean.isThreadAllocatedMemoryEnabled();
    }

    public void reset() {
        if (cpuTimeEnabled) {
            cpuTime = getTotalCpuTime();
            cpuUserTime = getTotalCpuUserTime();
        }
        if (allocationEnabled) {
            allocation = getTotalAllocation();
        }
    }

    public long getCountedCpuTime() {
        return getTotalCpuTime() - cpuTime;
    }

    public long getTotalCpuTime() {
        if (!cpuTimeEnabled)
            return 0;

        return threadMXBean.getCurrentThreadCpuTime();
    }

    public long getCountedCpuUserTime() {
        return getTotalCpuUserTime() - cpuUserTime;
    }

    public long getTotalCpuUserTime() {
        if (!cpuTimeEnabled)
            return 0;

        return threadMXBean.getCurrentThreadUserTime();
    }

    public long getCountedAllocation() {
        return getTotalAllocation() - allocation;
    }

    public long getTotalAllocation() {
        if (!allocationEnabled)
            return 0;

        return threadMXBean.getThreadAllocatedBytes(threadId)[0];
    }
}

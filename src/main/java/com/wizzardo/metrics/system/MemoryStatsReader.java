package com.wizzardo.metrics.system;

import com.wizzardo.metrics.JvmMonitoring;
import com.wizzardo.metrics.Recorder;

import java.util.HashMap;
import java.util.Map;

import static com.wizzardo.metrics.system.Utils.*;

/**
 * Created by wizzardo on 12/05/17.
 */
public class MemoryStatsReader {
    protected byte[] buffer;
    protected LineMapping mapping;

    public static class MemoryStats {

        // parsing /proc/meminfo
//        to            from
//        total		    MemTotal
//        free		    MemFree
//        shared		Shmem
//        cached		Cached
//        available	    MemAvailable
//        buffered	    Buffers
//        used		    total-free
//
//        swap.total	SwapTotal
//        swap.free	    SwapFree
//        swap.cached	SwapCached
//        swap.used	    total-free

        public long total;
        public long free;
        public long used;
        public long cached;
        public long buffered;
        public long available;
        public long shared;

        public long swapTotal;
        public long swapUsed;
        public long swapFree;
        public long swapCached;

        @Override
        public String toString() {
            return "MemoryStats{" +
                    "total=" + total +
                    ", free=" + free +
                    ", used=" + used +
                    ", cached=" + cached +
                    ", buffered=" + buffered +
                    ", available=" + available +
                    ", shared=" + shared +
                    ", swapTotal=" + swapTotal +
                    ", swapUsed=" + swapUsed +
                    ", swapFree=" + swapFree +
                    ", swapCached=" + swapCached +
                    '}';
        }
    }

    protected static class LineMapping {
        protected LineConsumer<MemoryStats>[] lines;

        protected LineMapping(byte[] data, int limit, Map<String, LineConsumer<MemoryStats>> consumerMap) {
            String s = new String(data, 0, limit);
            String[] split = s.split("\n");
            lines = new LineConsumer[split.length];
            for (int i = 0; i < split.length; i++) {
                String line = split[i];
                String name = line.split(":")[0];
                lines[i] = consumerMap.get(name);
            }
        }
    }

    static abstract class LineConsumer<T> {

        void consume(byte[] buffer, int from, int to, T t) {
            int colon = indexOf((byte) ':', buffer, from, to);
            int start = indexOfNot((byte) ' ', buffer, colon + 1, to);

            consume(t, readLong(buffer, start, to));
        }

        abstract void consume(T t, long value);
    }

    public MemoryStatsReader() {
        buffer = new byte[10240];
        Map<String, LineConsumer<MemoryStats>> map = new HashMap<>();
        map.put("MemTotal", new LineConsumer<MemoryStats>() {

            @Override
            void consume(MemoryStats memoryStats, long value) {
                memoryStats.total = value;
            }
        });
        map.put("MemFree", new LineConsumer<MemoryStats>() {

            @Override
            void consume(MemoryStats memoryStats, long value) {
                memoryStats.free = value;
            }
        });
        map.put("Shmem", new LineConsumer<MemoryStats>() {

            @Override
            void consume(MemoryStats memoryStats, long value) {
                memoryStats.shared = value;
            }
        });
        map.put("Cached", new LineConsumer<MemoryStats>() {

            @Override
            void consume(MemoryStats memoryStats, long value) {
                memoryStats.cached = value;
            }
        });
        map.put("MemAvailable", new LineConsumer<MemoryStats>() {

            @Override
            void consume(MemoryStats memoryStats, long value) {
                memoryStats.available = value;
            }
        });
        map.put("Buffers", new LineConsumer<MemoryStats>() {

            @Override
            void consume(MemoryStats memoryStats, long value) {
                memoryStats.buffered = value;
            }
        });
        map.put("SwapTotal", new LineConsumer<MemoryStats>() {

            @Override
            void consume(MemoryStats memoryStats, long value) {
                memoryStats.swapTotal = value;
            }
        });
        map.put("SwapFree", new LineConsumer<MemoryStats>() {

            @Override
            void consume(MemoryStats memoryStats, long value) {
                memoryStats.swapFree = value;
            }
        });
        map.put("SwapCached", new LineConsumer<MemoryStats>() {

            @Override
            void consume(MemoryStats memoryStats, long value) {
                memoryStats.swapCached = value;
            }
        });
        mapping = new LineMapping(buffer, Utils.read("/proc/meminfo", buffer), map);
    }

    public MemoryStats read() {
        byte[] buffer = this.buffer;
        int limit = Utils.read("/proc/meminfo", buffer);
        LineMapping mapping = this.mapping;

        MemoryStats stats = new MemoryStats();
        int position = 0;
        int line = 0;
        while (position < limit) {
            int next = indexOf((byte) '\n', buffer, position, limit);
            if (next == -1)
                next = limit;

            LineConsumer<MemoryStats> consumer = mapping.lines[line];
            if (consumer != null) {
                consumer.consume(buffer, position, next, stats);
            }

            line++;
            position = next + 1;
        }

        stats.swapUsed = stats.swapTotal - stats.swapFree;
        stats.used = stats.total - stats.free;
        return stats;
    }

    public JvmMonitoring.Recordable createRecordable() {
        return new JvmMonitoring.Recordable() {

            @Override
            public void record(Recorder recorder) {
                MemoryStats stats = read();
                recorder.gauge("system.mem.total", stats.total);
                recorder.gauge("system.mem.free", stats.free);
                recorder.gauge("system.mem.used", stats.used);
                recorder.gauge("system.mem.cached", stats.cached);
                recorder.gauge("system.mem.buffered", stats.buffered);
                recorder.gauge("system.mem.usable", stats.available);
                recorder.gauge("system.mem.shared", stats.shared);

                recorder.gauge("system.swap.total", stats.swapTotal);
                recorder.gauge("system.swap.cached", stats.swapCached);
                recorder.gauge("system.swap.free", stats.swapFree);
                recorder.gauge("system.swap.used", stats.swapUsed);
            }

            @Override
            public boolean isValid() {
                return true;
            }
        };
    }
}

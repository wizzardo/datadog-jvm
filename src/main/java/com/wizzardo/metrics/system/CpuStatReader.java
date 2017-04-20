package com.wizzardo.metrics.system;

import com.wizzardo.metrics.JvmMonitoring;
import com.wizzardo.metrics.Recorder;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Created by wizzardo on 20/04/17.
 */
public class CpuStatReader {

    protected byte[] buffer = new byte[10240];
    protected int[] intHolder = new int[1];
    protected long SC_CLK_TCK_MS = 10;

    public static class CpuStats {
        public String name;
        public long user;
        public long nice;
        public long system;
        public long idle;
        public long iowait;
        public long irq;
        public long softirq;

        @Override
        public String toString() {
            return "CpuStats{" +
                    "name='" + name + '\'' +
                    ", user=" + user +
                    ", nice=" + nice +
                    ", system=" + system +
                    ", idle=" + idle +
                    ", iowait=" + iowait +
                    ", irq=" + irq +
                    ", softirq=" + softirq +
                    '}';
        }
    }

    public JvmMonitoring.Recordable createRecordable() {
        return new JvmMonitoring.Recordable() {
            Recorder.Tags[] tags;
            CpuStats[] prev;
            CpuStats[] next;
            long time;

            {
                prev = new CpuStats[Runtime.getRuntime().availableProcessors() + 1];
                next = new CpuStats[prev.length];
                tags = new Recorder.Tags[prev.length];
                for (int i = 1; i < tags.length; i++) {
                    tags[i] = Recorder.Tags.of("cpu", "cpu" + (i - 1));
                }

                time = System.nanoTime();
                read(prev);
            }

            @Override
            public void record(Recorder recorder) {
                read(next);
                long time = System.nanoTime();
                long timeMs = (time - this.time) / 1000 / 1000;

                diff(prev, next);

                CpuStatReader.this.record(prev[0], recorder, timeMs);
                for (int i = 1; i < prev.length; i++) {
                    recordWithCore(prev[i], recorder, timeMs, tags[i]);
                }

                CpuStats[] temp = prev;
                prev = next;
                next = temp;
            }

            @Override
            public boolean isValid() {
                return true;
            }

        };
    }

    protected void record(CpuStats cpuStats, Recorder recorder, long timeMs) {
        recorder.gauge("system.cpu.user", cpuStats.user * 100d * SC_CLK_TCK_MS / timeMs);
        recorder.gauge("system.cpu.nice", cpuStats.nice * 100d * SC_CLK_TCK_MS / timeMs);
        recorder.gauge("system.cpu.system", cpuStats.system * 100d * SC_CLK_TCK_MS / timeMs);
        recorder.gauge("system.cpu.idle", cpuStats.idle * 100d * SC_CLK_TCK_MS / timeMs);
        recorder.gauge("system.cpu.iowait", cpuStats.iowait * 100d * SC_CLK_TCK_MS / timeMs);
        recorder.gauge("system.cpu.interrupt", (cpuStats.irq + cpuStats.softirq) * 100d * SC_CLK_TCK_MS / timeMs);
    }

    protected void recordWithCore(CpuStats cpuStats, Recorder recorder, long timeMs, Recorder.Tags tags) {
        recorder.gauge("system.cpu.core.user", cpuStats.user * 100d * SC_CLK_TCK_MS / timeMs, tags);
        recorder.gauge("system.cpu.core.nice", cpuStats.nice * 100d * SC_CLK_TCK_MS / timeMs, tags);
        recorder.gauge("system.cpu.core.system", cpuStats.system * 100d * SC_CLK_TCK_MS / timeMs, tags);
        recorder.gauge("system.cpu.core.idle", cpuStats.idle * 100d * SC_CLK_TCK_MS / timeMs, tags);
        recorder.gauge("system.cpu.core.iowait", cpuStats.iowait * 100d * SC_CLK_TCK_MS / timeMs, tags);
        recorder.gauge("system.cpu.core.interrupt", (cpuStats.irq + cpuStats.softirq) * 100d * SC_CLK_TCK_MS / timeMs, tags);
    }

    protected void diff(CpuStats[] statsA, CpuStats[] statsB) {
        int length = statsA.length;
        for (int i = 0; i < length; i++) {
            CpuStats a = statsA[i];
            CpuStats b = statsB[i];

            a.user = b.user - a.user;
            a.nice = b.nice - a.nice;
            a.system = b.system - a.system;
            a.idle = b.idle - a.idle;
            a.iowait = b.iowait - a.iowait;
            a.irq = b.irq - a.irq;
            a.softirq = b.softirq - a.softirq;
        }
    }

    public int read(CpuStats[] stats) {
        byte[] buffer = this.buffer;
        int[] holder = this.intHolder;

        int limit = read("/proc/stat", buffer);
        if (limit == -1)
            return 0;

        int line = 0;
        int nextLine;
        int position = 0;
        do {
            nextLine = indexOf((byte) '\n', buffer, position, limit);
            if (nextLine == -1)
                nextLine = limit;

            if (buffer[position] != 'c') // line starts with cpu
                break;

            CpuStats cpuStats = stats[line];
            if (cpuStats == null) {
                stats[line] = (cpuStats = new CpuStats());
                cpuStats.name = new String(buffer, position, indexOf((byte) ' ', buffer, position, nextLine) - position);
                position += cpuStats.name.length();
            } else {
                position = indexOf((byte) ' ', buffer, position, nextLine);
            }
            position = indexOfNot((byte) ' ', buffer, position, nextLine);
            checkPosition(buffer, position, nextLine);

            position = readInt(holder, buffer, position, nextLine);
            checkPosition(buffer, position, nextLine);
            cpuStats.user = holder[0];

            position = readInt(holder, buffer, position, nextLine);
            checkPosition(buffer, position, nextLine);
            cpuStats.nice = holder[0];

            position = readInt(holder, buffer, position, nextLine);
            checkPosition(buffer, position, nextLine);
            cpuStats.system = holder[0];

            position = readInt(holder, buffer, position, nextLine);
            checkPosition(buffer, position, nextLine);
            cpuStats.idle = holder[0];

            position = readInt(holder, buffer, position, nextLine);
            checkPosition(buffer, position, nextLine);
            cpuStats.iowait = holder[0];

            position = readInt(holder, buffer, position, nextLine);
            checkPosition(buffer, position, nextLine);
            cpuStats.irq = holder[0];

            position = readInt(holder, buffer, position, nextLine);
            checkPosition(buffer, position, nextLine);
            cpuStats.softirq = holder[0];

            position = nextLine + 1;
            line++;
        } while (position < limit);

        return line;
    }

    protected void checkPosition(byte[] buffer, int position, int limit) {
        if (position == -1 || position > limit)
            throw new IllegalStateException("Cannot parse: " + new String(buffer, 0, limit));
    }

    public int indexOf(byte b, byte[] bytes, int offset, int limit) {
        for (int i = offset; i < limit; i++) {
            if (bytes[i] == b)
                return i;
        }
        return -1;
    }

    public int indexOfNot(byte b, byte[] bytes, int offset, int limit) {
        for (int i = offset; i < limit; i++) {
            if (bytes[i] != b)
                return i;
        }
        return -1;
    }

    public int readInt(int[] holder, byte[] bytes, int offset, int limit) {
        int value = 0;
        for (int i = offset; i < limit; i++) {
            byte b = bytes[i];
            if (b >= '0' && b <= '9') {
                value = value * 10 + (b - '0');
            } else {
                holder[0] = value;
                return i + 1;
            }
        }

        holder[0] = value;
        return limit + 1;
    }

    public static int read(String path, byte[] bytes) {
        try (FileInputStream in = new FileInputStream(path)) {
            return in.read(bytes);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        CpuStats[] stats = new CpuStats[5];
//        int read = new StatReader().read(stats);
//        System.out.println(read);
//        for (CpuStats stat : stats) {
//            System.out.println(stat);
//        }
        for (int i = 0; i < 100; i++) {
            long time = System.nanoTime();
            for (int j = 0; j < 1000; j++) {
                new CpuStatReader().read(stats);
            }
            time = System.nanoTime() - time;
            System.out.println("time: " + (time / 1000f / 1000f) + "ms");
            System.out.println(stats[0]);
            System.out.println();
            Thread.sleep(1000);
        }
    }
}

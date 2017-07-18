package com.wizzardo.metrics.system;

import com.wizzardo.metrics.JvmMonitoring;
import com.wizzardo.metrics.Recorder;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static com.wizzardo.metrics.system.Utils.*;

/**
 * Created by wizzardo on 20/04/17.
 */
public class CpuStatReader {
    protected int numberOfCores = Runtime.getRuntime().availableProcessors();
    protected byte[] buffer = new byte[10240];
    protected int[] intHolder = new int[1];
    protected long SC_CLK_TCK_MS = 10;

    public CpuStatReader() {
        int userHz = getUserHz();
        if (userHz > 0) {
            SC_CLK_TCK_MS = 1000 / userHz;
        }
    }

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

    public int getUserHz() {
        try {
            File file = new File("/tmp/CLK_TCK.sh");
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write("echo $(getconf CLK_TCK)".getBytes(StandardCharsets.UTF_8));
            }
            String exec = Utils.exec("bash " + file.getAbsolutePath());
            return Integer.parseInt(exec);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1;
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
                try {
                    read(next);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
                long time = System.nanoTime();
                long timeMs = (time - this.time) / 1000 / 1000;
                this.time = time;

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
        recorder.gauge("system.cpu.user", cpuStats.user * 100d * SC_CLK_TCK_MS / timeMs / numberOfCores);
        recorder.gauge("system.cpu.nice", cpuStats.nice * 100d * SC_CLK_TCK_MS / timeMs / numberOfCores);
        recorder.gauge("system.cpu.system", cpuStats.system * 100d * SC_CLK_TCK_MS / timeMs / numberOfCores);
        recorder.gauge("system.cpu.idle", cpuStats.idle * 100d * SC_CLK_TCK_MS / timeMs / numberOfCores);
        recorder.gauge("system.cpu.iowait", cpuStats.iowait * 100d * SC_CLK_TCK_MS / timeMs / numberOfCores);
        recorder.gauge("system.cpu.interrupt", (cpuStats.irq + cpuStats.softirq) * 100d * SC_CLK_TCK_MS / timeMs / numberOfCores);
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

        int limit = Utils.read("/proc/stat", buffer);
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
}

package com.wizzardo.metrics.system;

import com.wizzardo.metrics.JvmMonitoring;
import com.wizzardo.metrics.Recorder;

/**
 * Created by wizzardo on 13/05/17.
 */
public class LoadStatsReader {
    protected byte[] buffer = new byte[256];

    public static class LoadStats {
        public float load1;
        public float load5;
        public float load15;

        @Override
        public String toString() {
            return "LoadStats{" +
                    "load1=" + load1 +
                    ", load5=" + load5 +
                    ", load15=" + load15 +
                    '}';
        }
    }

    public LoadStats read() {
        byte[] buffer = this.buffer;
        int limit = Utils.read("/proc/loadavg", buffer);

        int[] intHolder = new int[1];
        float[] floatHolder = new float[1];

        LoadStats stats = new LoadStats();

        int position = 0;
        position = readFloat(floatHolder, intHolder, buffer, position, limit);
        stats.load1 = floatHolder[0];
        position = readFloat(floatHolder, intHolder, buffer, position, limit);
        stats.load5 = floatHolder[0];
        position = readFloat(floatHolder, intHolder, buffer, position, limit);
        stats.load15 = floatHolder[0];

        return stats;
    }

    public static int readFloat(float[] floatHolder, int[] intHolder, byte[] bytes, int offset, int limit) {
        int nextPart = Utils.readInt(intHolder, bytes, offset, limit);
        int leftPart = intHolder[0];
        offset = Utils.readInt(intHolder, bytes, nextPart, limit);
        int rightPart = intHolder[0];
        float size = rightPart < 10 ? 10 : 100;
        floatHolder[0] = leftPart + rightPart / size;

        return offset;
    }

    public JvmMonitoring.Recordable createRecordable() {
        return new JvmMonitoring.Recordable() {

            int numberOfCores = Runtime.getRuntime().availableProcessors();

            @Override
            public void record(Recorder recorder) {
                LoadStats stats = read();
                recorder.gauge("system.load.1", stats.load1);
                recorder.gauge("system.load.5", stats.load5);
                recorder.gauge("system.load.15", stats.load15);

                recorder.gauge("system.load.norm.1", stats.load1 / numberOfCores);
                recorder.gauge("system.load.norm.5", stats.load5 / numberOfCores);
                recorder.gauge("system.load.norm.15", stats.load15 / numberOfCores);
            }

            @Override
            public boolean isValid() {
                return true;
            }
        };
    }
}

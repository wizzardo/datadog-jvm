package com.wizzardo.metrics.system;

import com.wizzardo.metrics.JvmMonitoring;
import com.wizzardo.metrics.Recorder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.wizzardo.metrics.system.Utils.*;

/**
 * Created by Mikhail Bobrutskov on 14.05.17.
 */
public class NetworkStatsReader {

    protected byte[] buffer = new byte[10240];

    public static class NetworkStats {
        public String device;
        public long transmitted;
        public long received;
        protected Recorder.Tags tags;

        public Recorder.Tags getTags() {
            if (tags == null)
                tags = Recorder.Tags.of("device", device);

            return tags;
        }

        public void setTags(Recorder.Tags tags) {
            this.tags = tags;
        }
    }

    public Map<String, NetworkStats> read() {
        byte[] buffer = this.buffer;
        int limit = Utils.read("/proc/net/dev", buffer);

        int position = 0;
        position = indexOf((byte) '\n', buffer, position, limit) + 1;
        position = indexOf((byte) '\n', buffer, position, limit) + 1;

        long[] holder = new long[1];
        int nextLine;
        Map<String, NetworkStats> stats = new HashMap<>();
        do {
            nextLine = indexOf((byte) '\n', buffer, position, limit);
            if (nextLine == -1)
                nextLine = limit;

            position = indexOfNot((byte) ' ', buffer, position, nextLine);
            int nameEnd = indexOf((byte) ':', buffer, position, nextLine);
            String device = new String(buffer, position, nameEnd - position, StandardCharsets.UTF_8);

            position = nameEnd + 1;
            position = indexOfNot((byte) ' ', buffer, position, nextLine);

            position = readLong(holder, buffer, position, nextLine);
            checkPosition(buffer, position, nextLine);
            long received = holder[0];

            position = skipValue(buffer, position, nextLine); //packets
            position = skipValue(buffer, position, nextLine); //errs
            position = skipValue(buffer, position, nextLine); //drop
            position = skipValue(buffer, position, nextLine); //fifo
            position = skipValue(buffer, position, nextLine); //frame
            position = skipValue(buffer, position, nextLine); //compressed
            position = skipValue(buffer, position, nextLine); //multicast

            position = indexOfNot((byte) ' ', buffer, position, nextLine);

            position = readLong(holder, buffer, position, nextLine);
            checkPosition(buffer, position, nextLine);
            long transmitted = holder[0];

            NetworkStats networkStats = new NetworkStats();
            networkStats.device = device;
            networkStats.transmitted = transmitted;
            networkStats.received = received;
            stats.put(device, networkStats);

            position = nextLine + 1;
        } while (position < limit);

        return stats;
    }

    public JvmMonitoring.Recordable createRecordable() {
        return new JvmMonitoring.Recordable() {
            Map<String, NetworkStats> prev;

            @Override
            public void record(Recorder recorder) {
                Map<String, NetworkStats> stats = read();
                if (prev != null) {
                    for (Map.Entry<String, NetworkStats> entry : prev.entrySet()) {
                        NetworkStats current = stats.get(entry.getKey());
                        if (current == null)
                            continue;

                        NetworkStats old = entry.getValue();
                        recorder.gauge("system.net.bytes.transmitted", current.transmitted - old.transmitted, old.getTags());
                        recorder.gauge("system.net.bytes.received", current.received - old.received, old.getTags());
                        current.setTags(old.getTags());
                    }
                }
                prev = stats;
            }

            @Override
            public boolean isValid() {
                return true;
            }
        };
    }
}

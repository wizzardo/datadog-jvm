package com.wizzardo.metrics.system;

import com.wizzardo.metrics.JvmMonitoring;
import com.wizzardo.metrics.Recorder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Mikhail Bobrutskov on 14.05.17.
 */
public class DiskStatsReader {

    public static class FilesystemStats {
        public String name;
        public String mountPoint;
        public long size;
        public long free;
        public long available;
        public File file;
        Recorder.Tags tags;

        @Override
        public String toString() {
            return "Filesystem{" +
                    "name='" + name + '\'' +
                    ", mountPoint='" + mountPoint + '\'' +
                    ", size=" + size +
                    ", free=" + free +
                    ", available=" + available +
                    '}';
        }

        public Recorder.Tags getTags() {
            if (tags == null)
                tags = Recorder.Tags.of("dev", name, "mount", mountPoint);

            return tags;
        }
    }

    List<FilesystemStats> filesystemStats;

    public DiskStatsReader() {
        String s = Utils.exec("df");
        if (s == null) {
            filesystemStats = Collections.emptyList();
            return;
        }

        String[] filesystems = s.split("\n");
        this.filesystemStats = new ArrayList<>(filesystems.length - 1);
        for (int i = 1; i < filesystems.length; i++) {
            FilesystemStats fs = new FilesystemStats();
            String[] data = filesystems[i].split(" +");
            fs.name = data[0];
            fs.mountPoint = data[5];
            fs.file = new File(fs.mountPoint);
            fs.size = fs.file.getTotalSpace();

            this.filesystemStats.add(fs);
        }
    }

    public void update() {
        for (FilesystemStats fs : filesystemStats) {
            fs.available = fs.file.getUsableSpace();
            fs.free = fs.file.getFreeSpace();
        }
    }

    public JvmMonitoring.Recordable createRecordable() {
        return new JvmMonitoring.Recordable() {
            @Override
            public void record(Recorder recorder) {
                update();

                for (FilesystemStats fs : filesystemStats) {
                    recorder.gauge("system.disk.free", fs.free, fs.getTags());
                    recorder.gauge("system.disk.total", fs.size, fs.getTags());
                    recorder.gauge("system.disk.available", fs.available, fs.getTags());
                    recorder.gauge("system.disk.used", fs.size - fs.free, fs.getTags());
                    recorder.gauge("system.disk.in_use", (fs.size - fs.free) * 1d / fs.size, fs.getTags());
                }
            }

            @Override
            public boolean isValid() {
                return !filesystemStats.isEmpty();
            }
        };
    }
}

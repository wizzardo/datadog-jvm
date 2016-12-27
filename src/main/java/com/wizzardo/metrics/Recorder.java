package com.wizzardo.metrics;

import com.wizzardo.tools.interfaces.Consumer;
import com.wizzardo.tools.misc.Unchecked;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Created by wizzardo on 05/09/16.
 */
public class Recorder {
    public static final String ACTION_DURATION = "actionDuration";
    public static final String ACTION_TIME = "action.time";
    public static final String ACTION_ALLOCATION = "action.allocation";
    public static final String METHOD_DURATION = "methodDuration";
    public static final String METHOD_TIME = "method.time";
    public static final String METHOD_ALLOCATION = "method.allocation";

    protected final String[] EMPTY_ARRAY = new String[0];
    private Client client;
    private Consumer<Exception> onError = new Consumer<Exception>() {
        @Override
        public void consume(Exception e) {
            e.printStackTrace();
        }
    };
    protected boolean recordAllocation;
    protected boolean recordCpuTime;

    public Recorder(Client client) {
        this.client = client;
        CpuAndAllocationStats cpuAndAllocationStats = CpuAndAllocationStats.get();
        recordCpuTime = cpuAndAllocationStats.cpuTimeEnabled;
        recordAllocation = cpuAndAllocationStats.allocationEnabled;
    }

    public void rec(Runnable runnable) {
        rec(METHOD_DURATION, runnable);
    }

    public void rec(Runnable runnable, Tags tags) {
        rec(METHOD_DURATION, runnable, tags);
    }

    public void rec(String metric, Runnable runnable) {
        rec(metric, runnable, null);
    }

    public void rec(String metric, Runnable runnable, Tags tags) {
        rec(metric, tags, runnable, null);
    }

    protected <T> T rec(String metric, Tags tags, Runnable runnable, Callable<T> callable) {
        long allocated = 0;
        long cpuTime = 0;
        CpuAndAllocationStats cpuAndAllocationStats = null;
        if (recordAllocation || recordCpuTime) {
            cpuAndAllocationStats = CpuAndAllocationStats.get();
            if (recordAllocation) {
                allocated = cpuAndAllocationStats.getTotalAllocation();
            }
            if (recordCpuTime) {
                cpuTime = cpuAndAllocationStats.getTotalCpuTime();
            }
        }

        long time = System.nanoTime();
        T result = null;
        if (runnable != null)
            runnable.run();

        if (callable != null)
            result = Unchecked.call(callable);

        time = Math.max(System.nanoTime() - time, 0);
        rec(metric, time / 1_000_000, tags);

        if (recordAllocation || recordCpuTime) {
            try {
                if (recordAllocation) {
                    allocated = Math.max(cpuAndAllocationStats.getTotalAllocation() - allocated, 0);
                }
                if (recordCpuTime) {
                    cpuTime = Math.max(cpuAndAllocationStats.getTotalCpuTime() - cpuTime, 0);
                }

                if (allocated > 0) {
                    histogram(METHOD_ALLOCATION, allocated, tags);
                }

                int i = tags.size();
                tags.add("type", "cpu");

                if (cpuTime > 0) {
                    histogram(METHOD_TIME, cpuTime, tags);
                }

                tags.set(i, "type:total");
                histogram(METHOD_TIME, time, tags);

                tags.set(i, "type:wait");
                histogram(METHOD_TIME, time - cpuTime, tags);
            } catch (Exception e) {
                onError(e);
            }
        }
        return result;
    }

    public <T> T rec(Callable<T> callable) {
        return rec(METHOD_DURATION, callable);
    }

    public <T> T rec(Callable<T> callable, Tags tags) {
        return rec(METHOD_DURATION, callable, tags);
    }

    public <T> T rec(String metric, Callable<T> callable) {
        return rec(metric, callable, null);
    }

    public <T> T rec(String metric, Callable<T> callable, Tags tags) {
        return rec(metric, tags, null, callable);
    }

    public void rec(String metric, long duration) {
        rec(metric, duration, null);
    }

    public void rec(String metric, long duration, Tags tags) {
        try {
            client.histogram(metric, duration * 0.001, renderTags(tags));
        } catch (Exception e) {
            onError(e);
        }
    }

    protected void onError(Exception e) {
        onError.consume(e);
    }

    public void onError(Consumer<Exception> onError) {
        this.onError = onError;
    }

    protected String[] renderTags(Tags tags) {
        return tags == null ? EMPTY_ARRAY : tags.build();
    }

    public void count(String metric, long value, Tags tags) {
        try {
            client.count(metric, value, renderTags(tags));
        } catch (Exception e) {
            onError(e);
        }
    }

    public void histogram(String metric, long value, Tags tags) {
        try {
            client.histogram(metric, value, renderTags(tags));
        } catch (Exception e) {
            onError(e);
        }
    }

    public void histogram(String metric, double value, Tags tags) {
        try {
            client.histogram(metric, value, renderTags(tags));
        } catch (Exception e) {
            onError(e);
        }
    }

    public void gauge(String metric, long value) {
        gauge(metric, value, null);
    }

    public void gauge(String metric, long value, Tags tags) {
        try {
            client.gauge(metric, value, renderTags(tags));
        } catch (Exception e) {
            onError(e);
        }
    }

    public static class Tags {
        List<String> tags = new ArrayList<>();
        String[] build;

        public static Tags of(Tags tags) {
            Tags t = new Tags();
            t.tags.addAll(tags.tags);
            return t;
        }

        public static Tags of(String key, Object value) {
            return new Tags().add(key, value);
        }

        public static Tags of(String key, Object value, String... moreTags) {
            Tags tags = new Tags();
            tags.add(key, value);
            if (moreTags != null) {
                if (moreTags.length % 2 != 0)
                    throw new IllegalArgumentException("the number of elements must be even");

                for (int i = 0; i < moreTags.length; i += 2) {
                    tags.add(moreTags[i], moreTags[i + 1]);
                }
            }
            return tags;
        }

        public int size() {
            return tags.size();
        }

        public String get(int i) {
            return tags.get(i);
        }

        public void set(int i, String tagPair) {
            tags.set(i, tagPair);
            if (build != null && build.length == tags.size())
                build[i] = tagPair;
        }

        public Tags add(String key, Object value) {
            if (key != null && value != null)
                tags.add(prepare(key) + ":" + prepare(String.valueOf(value)));

            return this;
        }

        private String prepare(String s) {
            return s.replace(':', '_').replace('#', '_').trim();
        }

        protected String[] build() {
            if (build != null && build.length == tags.size())
                return build;
            return build = tags.toArray(new String[tags.size()]);
        }

        @Override
        public String toString() {
            return Arrays.toString(build());
        }
    }
}
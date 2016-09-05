package com.wizzardo.metrics;

import com.wizzardo.tools.misc.Unchecked;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Created by wizzardo on 05/09/16.
 */
public class Recorder {

    private Client client;

    public Recorder(Client client) {
        this.client = client;
    }

    public void rec(String metric, Runnable runnable) {
        rec(metric, runnable, null);
    }

    public void rec(String metric, Runnable runnable, Tags tags) {
        long time = System.currentTimeMillis();
        runnable.run();
        time = System.currentTimeMillis() - time;
        rec(metric, time, tags);
    }

    public <T> T rec(String metric, Callable<T> callable) {
        return rec(metric, callable, null);
    }

    public <T> T rec(String metric, Callable<T> callable, Tags tags) {
        long time = System.currentTimeMillis();
        T result = Unchecked.call(callable);
        time = System.currentTimeMillis() - time;
        rec(metric, time, tags);
        return result;
    }

    public void rec(String metric, long duration) {
        rec(metric, duration, null);
    }

    public void rec(String metric, long duration, Tags tags) {
        try {
            if (tags == null) {
                client.histogramDouble(metric, duration * 0.001);
            } else {
                client.histogramDouble(metric, duration * 0.001, tags.build());
            }
        } catch (Exception e) {
            onError(e);
        }
    }

    protected void onError(Exception e) {
        e.printStackTrace();
    }

    public void recValue(String metric, long duration, Tags tags) {
        try {
            if (tags == null) {
                client.histogramLong(metric, duration);
            } else {
                client.histogramLong(metric, duration, tags.build());
            }
        } catch (Exception e) {
            onError(e);
        }
    }

    public void gauge(String metric, long value) {
        gauge(metric, value, null);
    }

    public void gauge(String metric, long value, Tags tags) {
        try {
            if (tags == null)
                client.gauge(metric, value);
            else
                client.gauge(metric, value, tags.build());
        } catch (Exception e) {
            onError(e);
        }
    }

    public static class Tags {
        List<String> tags = new ArrayList<>();
        String[] build;

        public static Tags of(String key, String value) {
            return new Tags().add(key, value);
        }

        public static Tags of(String key, String value, String... moreTags) {
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

        public Tags add(String key, String value) {
            if (key != null && value != null)
                tags.add(prepare(key) + ":" + prepare(value));

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
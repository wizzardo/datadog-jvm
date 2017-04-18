package com.wizzardo.metrics;

import com.timgroup.statsd.StatsDClient;

/**
 * Created by wizzardo on 06/09/16.
 */
public class DatadogClient implements Client {

    private StatsDClient client;

    public DatadogClient(StatsDClient client) {
        this.client = client;
    }

    @Override
    public void histogram(String metric, double value, String[] tags) {
        client.recordHistogramValue(metric, value, tags);
    }

    @Override
    public void histogram(String metric, long value, String[] tags) {
        client.histogram(metric, value, tags);
    }

    @Override
    public void gauge(String metric, long value, String[] tags) {
        client.gauge(metric, value, tags);
    }

    @Override
    public void gauge(String metric, double value, String[] tags) {
        client.gauge(metric, value, tags);
    }

    @Override
    public void increment(String metric, String[] tags) {
        client.increment(metric, tags);
    }

    @Override
    public void decrement(String metric, String[] tags) {
        client.decrement(metric, tags);
    }

    @Override
    public void count(String metric, long value, String[] tags) {
        client.count(metric, value, tags);
    }

    @Override
    public void set(String metric, String value, String[] tags) {
        client.recordSetValue(metric, value, tags);
    }
}

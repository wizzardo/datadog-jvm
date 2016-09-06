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
    public void histogram(String metric, double value) {
        client.histogram(metric, value);
    }

    @Override
    public void histogram(String metric, long value, String[] tags) {
        client.histogram(metric, value, tags);
    }

    @Override
    public void histogram(String metric, long value) {
        client.histogram(metric, value);
    }

    @Override
    public void gauge(String metric, long value, String[] tags) {
        client.gauge(metric, value, tags);
    }

    @Override
    public void gauge(String metric, long value) {
        client.gauge(metric, value);
    }
}

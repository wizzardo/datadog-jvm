package com.wizzardo.metrics;

/**
 * Created by wizzardo on 05/09/16.
 */
public interface Client {
    void histogram(String metric, double value, String[] tags);

    void histogram(String metric, double value);

    void histogram(String metric, long value, String[] tags);

    void histogram(String metric, long value);

    void gauge(String metric, long value, String[] tags);

    void gauge(String metric, long value);
}

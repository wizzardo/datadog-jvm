package com.wizzardo.metrics;

/**
 * Created by wizzardo on 05/09/16.
 */
public interface Client {
    void histogram(String metric, double value, String[] tags);

    void histogram(String metric, long value, String[] tags);

    void gauge(String metric, long value, String[] tags);

    void increment(String metric, String[] tags);

    void decrement(String metric, String[] tags);

    void count(String metric, long value, String[] tags);

    void set(String metric, String value, String[] tags);
}

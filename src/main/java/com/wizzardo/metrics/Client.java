package com.wizzardo.metrics;

/**
 * Created by wizzardo on 05/09/16.
 */
public interface Client {
    void histogramDouble(String metric, double value, String[] tags);

    void histogramDouble(String metric, double value);

    void histogramLong(String metric, long value, String[] tags);

    void histogramLong(String metric, long value);

    void gauge(String metric, long value, String[] tags);

    void gauge(String metric, long value);
}

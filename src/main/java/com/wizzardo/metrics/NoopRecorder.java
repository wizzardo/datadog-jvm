package com.wizzardo.metrics;

/**
 * Created by wizzardo on 28/09/16.
 */
public class NoopRecorder extends Recorder {
    public NoopRecorder(Client client) {
        super(client);
    }

    public NoopRecorder() {
        super(null);
    }


    @Override
    public void rec(String metric, long duration, Tags tags) {
    }

    @Override
    public void count(String metric, long value, Tags tags) {
    }

    @Override
    public void histogram(String metric, long value, Tags tags) {
    }

    @Override
    public void histogram(String metric, double value, Tags tags) {
    }

    @Override
    public void gauge(String metric, long value, Tags tags) {
    }
}

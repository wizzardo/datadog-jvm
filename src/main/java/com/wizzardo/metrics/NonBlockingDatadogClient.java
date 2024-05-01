package com.wizzardo.metrics;

import com.wizzardo.tools.interfaces.Consumer;
import com.wizzardo.tools.interfaces.Supplier;
import com.wizzardo.tools.misc.ExceptionDrivenStringBuilder;
import com.wizzardo.tools.misc.UTF8;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.*;

public class NonBlockingDatadogClient implements Client {

    private static int PACKET_SIZE_BYTES = 1500;

    private static Consumer<Exception> NO_OP_HANDLER = new Consumer<Exception>() {
        @Override
        public void consume(Exception e) {
            e.printStackTrace();
        }
    };

    private static ThreadLocal<NumberFormat> NUMBER_FORMATTERS = new ThreadLocal<NumberFormat>() {
        @Override
        protected NumberFormat initialValue() {

            // Always create the formatter for the US locale in order to avoid this bug:
            // https://github.com/indeedeng/java-dogstatsd-client/issues/3
            NumberFormat numberFormatter = NumberFormat.getInstance(Locale.US);
            numberFormatter.setGroupingUsed(false);
            numberFormatter.setMaximumFractionDigits(6);

            // we need to specify a value for Double.NaN that is recognized by dogStatsD
            if (numberFormatter instanceof DecimalFormat) { // better safe than a runtime error
                DecimalFormat decimalFormat = (DecimalFormat) numberFormatter;
                DecimalFormatSymbols symbols = decimalFormat.getDecimalFormatSymbols();
                symbols.setNaN("NaN");
                decimalFormat.setDecimalFormatSymbols(symbols);
            }

            return numberFormatter;
        }
    };

    private String prefix;
    private DatagramChannel clientChannel;
    private Consumer<Exception> handler;
    private String constantTagsRendered;

    private ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        ThreadFactory delegate = Executors.defaultThreadFactory();

        @Override
        public Thread newThread(Runnable r) {
            Thread result = delegate.newThread(r);
            result.setName("StatsD-" + result.getName());
            result.setDaemon(true);
            return result;
        }
    });

    private BlockingQueue<Consumer<ExceptionDrivenStringBuilder>> queue;

    public NonBlockingDatadogClient(String prefix, String hostname, int port) throws UnknownHostException {
        this(prefix, hostname, port, Integer.MAX_VALUE);
    }

    public NonBlockingDatadogClient(String prefix, String hostname, int port, int queueSize) throws UnknownHostException {
        this(prefix, hostname, port, queueSize, null, null);
    }

    public NonBlockingDatadogClient(String prefix, String hostname, int port, String... constantTags) throws UnknownHostException {
        this(prefix, hostname, port, Integer.MAX_VALUE, constantTags, null);
    }

    public NonBlockingDatadogClient(String prefix, String hostname, int port, int queueSize, String... constantTags) throws UnknownHostException {
        this(prefix, hostname, port, queueSize, constantTags, null);
    }

    public NonBlockingDatadogClient(String prefix, String hostname, int port, String[] constantTags, Consumer<Exception> errorHandler) throws UnknownHostException {
        this(prefix, Integer.MAX_VALUE, constantTags, errorHandler, staticAddressResolution(hostname, port));
    }

    public NonBlockingDatadogClient(String prefix, String hostname, int port, int queueSize, String[] constantTags, Consumer<Exception> errorHandler) throws UnknownHostException {
        this(prefix, queueSize, constantTags, errorHandler, staticAddressResolution(hostname, port));
    }

    public NonBlockingDatadogClient(String prefix, int queueSize, String[] constantTags, Consumer<Exception> errorHandler, Callable<InetSocketAddress> addressLookup) {
        if ((prefix != null) && (!prefix.isEmpty())) {
            this.prefix = String.format("%s.", prefix);
        } else {
            this.prefix = "";
        }
        if (errorHandler == null) {
            handler = NO_OP_HANDLER;
        } else {
            handler = errorHandler;
        }

        if ((constantTags != null) && (constantTags.length == 0)) {
            constantTags = null;
        }

        if (constantTags != null) {
            constantTagsRendered = tagString(constantTags, null);
        } else {
            constantTagsRendered = null;
        }

        try {
            clientChannel = DatagramChannel.open();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start StatsD client", e);
        }
        queue = new ArrayBlockingQueue<>(queueSize);
        executor.submit(new QueueConsumer(addressLookup));
    }


    public void stop() {
        try {
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            handler.consume(e);
        } finally {
            if (clientChannel != null) {
                try {
                    clientChannel.close();
                } catch (IOException e) {
                    handler.consume(e);
                }
            }
        }
    }

    static String tagString(String[] tags, String tagPrefix) {
        StringBuilder sb;
        if (tagPrefix != null) {
            if ((tags == null) || (tags.length == 0)) {
                return tagPrefix;
            }
            sb = new StringBuilder(tagPrefix);
            sb.append(",");
        } else {
            if ((tags == null) || (tags.length == 0)) {
                return "";
            }
            sb = new StringBuilder("|#");
        }

        for (int n = tags.length - 1; n >= 0; n--) {
            sb.append(tags[n]);
            if (n > 0) {
                sb.append(",");
            }
        }
        return sb.toString();
    }


    void appendTags(String[] tags, ExceptionDrivenStringBuilder sb) {
        if (constantTagsRendered != null) {
            sb.append(constantTagsRendered);
            if (tags == null || tags.length == 0)
                return;

            sb.append(",");
        } else {
            if (tags == null || tags.length == 0)
                return;

            sb.append("|#");
        }

        for (int n = tags.length - 1; n >= 0; n--) {
            sb.append(tags[n]);
            if (n > 0) {
                sb.append(",");
            }
        }
    }

    @Override
    public void count(final String aspect, final long delta, final String[] tags) {
        send(new Consumer<ExceptionDrivenStringBuilder>() {
            @Override
            public void consume(ExceptionDrivenStringBuilder sb) {
                sb.append(prefix).append(aspect).append(':').append(delta).append("|c");
                appendTags(tags, sb);
            }
        });
    }

    @Override
    public void set(String metric, String value, String[] tags) {
        recordSetValue(metric, value, tags);
    }

    public void incrementCounter(String aspect, String[] tags) {
        count(aspect, 1, tags);
    }

    @Override
    public void increment(String aspect, String[] tags) {
        incrementCounter(aspect, tags);
    }

    public void decrementCounter(String aspect, String[] tags) {
        count(aspect, -1, tags);
    }

    @Override
    public void decrement(String aspect, String[] tags) {
        decrementCounter(aspect, tags);
    }

    public void recordGaugeValue(final String aspect, final double value, final String[] tags) {
        send(new Consumer<ExceptionDrivenStringBuilder>() {
            @Override
            public void consume(ExceptionDrivenStringBuilder sb) {
                sb.append(prefix).append(aspect).append(':').append(NUMBER_FORMATTERS.get().format(value)).append("|g");
                appendTags(tags, sb);
            }
        });
    }

    @Override
    public void gauge(String aspect, double value, String[] tags) {
        recordGaugeValue(aspect, value, tags);
    }


    public void recordGaugeValue(final String aspect, final long value, final String[] tags) {
        send(new Consumer<ExceptionDrivenStringBuilder>() {
            @Override
            public void consume(ExceptionDrivenStringBuilder sb) {
                sb.append(prefix).append(aspect).append(':').append(value).append("|g");
                appendTags(tags, sb);
            }
        });
    }

    @Override
    public void gauge(String aspect, long value, String[] tags) {
        recordGaugeValue(aspect, value, tags);
    }

    public void recordExecutionTime(final String aspect, final long timeInMs, final String[] tags) {
        send(new Consumer<ExceptionDrivenStringBuilder>() {
            @Override
            public void consume(ExceptionDrivenStringBuilder sb) {
                sb.append(prefix).append(aspect).append(':').append(timeInMs).append("|ms");
                appendTags(tags, sb);
            }
        });
    }

    public void time(String aspect, long value, String[] tags) {
        recordExecutionTime(aspect, value, tags);
    }

    public void recordHistogramValue(final String aspect, final double value, final String[] tags) {
        send(new Consumer<ExceptionDrivenStringBuilder>() {
            @Override
            public void consume(ExceptionDrivenStringBuilder sb) {
                sb.append(prefix).append(aspect).append(':').append(NUMBER_FORMATTERS.get().format(value)).append("|h");
                appendTags(tags, sb);
            }
        });
    }

    @Override
    public void histogram(String aspect, double value, String[] tags) {
        recordHistogramValue(aspect, value, tags);
    }

    public void recordHistogramValue(final String aspect, final long value, final String[] tags) {
        send(new Consumer<ExceptionDrivenStringBuilder>() {
            @Override
            public void consume(ExceptionDrivenStringBuilder sb) {
                sb.append(prefix).append(aspect).append(':').append(value).append("|h");
                appendTags(tags, sb);
            }
        });
    }

    @Override
    public void histogram(String aspect, long value, String[] tags) {
        recordHistogramValue(aspect, value, tags);
    }

//    public void recordEvent(final Event event, final String[] tags) {
//        send(new Mapper<ExceptionDrivenStringBuilder, String>() {
//            @Override
//            public String map(ExceptionDrivenStringBuilder sb) {
//                String title = prefix + escapeEventString(event.getTitle());
//                String text = escapeEventString(event.getText());
//
//                sb.append("_e{").append(title.length()).append(',').append(text.length()).append("}:").append(title).append('|').append(text);
//
//                long millisSinceEpoch = event.getMillisSinceEpoch();
//                if (millisSinceEpoch != -1) {
//                    sb.append("|d:").append(millisSinceEpoch / 1000);
//                }
//
//                String hostname = event.getHostname();
//                if (hostname != null) {
//                    sb.append("|h:").append(hostname);
//                }
//
//                String aggregationKey = event.getAggregationKey();
//                if (aggregationKey != null) {
//                    sb.append("|k:").append(aggregationKey);
//                }
//
//                String priority = event.getPriority();
//                if (priority != null) {
//                    sb.append("|p:").append(priority);
//                }
//
//                String alertType = event.getAlertType();
//                if (alertType != null) {
//                    sb.append("|t:").append(alertType);
//                }
//                appendTags(tags, sb);
//                return sb.toString();
//            }
//        });
//    }

    private String escapeEventString(String title) {
        return title.replace("\n", "\\n");
    }

//    public void recordServiceCheckRun(final ServiceCheck sc) {
//        send(new Mapper<ExceptionDrivenStringBuilder, String>() {
//            @Override
//            public String map(ExceptionDrivenStringBuilder sb) {
//                sb.append("_sc|").append(sc.getName()).append('|').append(sc.getStatus());
//                if (sc.getTimestamp() > 0) {
//                    sb.append("|d:").append(sc.getTimestamp());
//                }
//                if (sc.getHostname() != null) {
//                    sb.append("|h:").append(sc.getHostname());
//                }
//
//                appendTags(sc.getTags(), sb);
//                if (sc.getMessage() != null) {
//                    sb.append("|m:").append(sc.getEscapedMessage());
//                }
//                return sb.toString();
//            }
//        });
//    }
//
//    public void serviceCheck(ServiceCheck sc) {
//        recordServiceCheckRun(sc);
//    }

    public void recordSetValue(final String aspect, final String value, final String[] tags) {
        send(new Consumer<ExceptionDrivenStringBuilder>() {
            @Override
            public void consume(ExceptionDrivenStringBuilder sb) {
                sb.append(prefix).append(aspect).append(':').append(value).append("|s");
                appendTags(tags, sb);
            }
        });
    }

    private void send(Consumer<ExceptionDrivenStringBuilder> mapper) {
        queue.offer(mapper);
    }

    public static Charset MESSAGE_CHARSET = StandardCharsets.UTF_8;


    private class QueueConsumer implements Runnable {
        private ByteBuffer sendBuffer = ByteBuffer.allocate(PACKET_SIZE_BYTES);
        private Callable<InetSocketAddress> addressLookup;
        private Supplier<byte[]> bytes = new Supplier<byte[]>() {
            byte[] bytes = new byte[PACKET_SIZE_BYTES];

            @Override
            public byte[] supply() {
                return bytes;
            }
        };
        private UTF8.BytesConsumer sender = new UTF8.BytesConsumer() {
            @Override
            public void consume(byte[] buffer, int offset, int length) {
                try {
                    InetSocketAddress address = addressLookup.call();

                    if (sendBuffer.remaining() < (length + 1)) {
                        blockingSend(address);
                    }
                    if (sendBuffer.position() > 0) {
                        sendBuffer.put((byte) '\n');
                    }
                    sendBuffer.put(buffer, offset, length);
                    if (null == queue.peek()) {
                        blockingSend(address);
                    }
                } catch (Exception e) {
                    handler.consume(e);
                }
            }
        };

        QueueConsumer(Callable<InetSocketAddress> addressLookup) {
            this.addressLookup = addressLookup;
        }

        @Override
        public void run() {
            ExceptionDrivenStringBuilder sb = new ExceptionDrivenStringBuilder();
            while (!executor.isShutdown()) {
                try {
//                    System.out.println("waiting for next metric");
                    Consumer<ExceptionDrivenStringBuilder> consumer = queue.take();
                    sb.clear();
                    consumer.consume(sb);
//                    System.out.println("message: "+message);
                    int length = sb.length();
                    if (length > 0) {
                        sb.toBytes(bytes, sender);
//                        InetSocketAddress address = addressLookup.call();
//                        byte[] data = message.getBytes(MESSAGE_CHARSET);
//                        if (sendBuffer.remaining() < (data.length + 1)) {
//                            blockingSend(address);
//                        }
//                        if (sendBuffer.position() > 0) {
//                            sendBuffer.put((byte) '\n');
//                        }
//                        sendBuffer.put(data);
//                        if (null == queue.peek()) {
//                            blockingSend(address);
//                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    if (e instanceof Exception)
                        handler.consume((Exception) e);
                }
            }
        }

        private void blockingSend(InetSocketAddress address) throws IOException {
            int sizeOfBuffer = sendBuffer.position();
            sendBuffer.flip();
//            System.out.println("blockingSend...");

            int sentBytes = clientChannel.send(sendBuffer, address);
            sendBuffer.limit(sendBuffer.capacity());
            sendBuffer.rewind();

//            System.out.println("sent " + sentBytes + " of " + sizeOfBuffer);
            if (sizeOfBuffer != sentBytes) {
                handler.consume(new IOException(String.format(
                        "Could not send entirely stat %s to host %s:%d. Only sent %d bytes out of %d bytes",
                        sendBuffer,
                        address.getHostName(),
                        address.getPort(),
                        sentBytes,
                        sizeOfBuffer)
                ));
            }
        }
    }

    public static Callable<InetSocketAddress> volatileAddressResolution(final String hostname, final int port) {
        return new Callable<InetSocketAddress>() {
            @Override
            public InetSocketAddress call() throws UnknownHostException {
                return resolve(hostname, port);
            }
        };
    }

    private static InetSocketAddress resolve(String hostname, int port) throws UnknownHostException {
        return new InetSocketAddress(InetAddress.getByName(hostname), port);
    }

    public static Callable<InetSocketAddress> staticAddressResolution(String hostname, int port) throws UnknownHostException {
        final InetSocketAddress address = resolve(hostname, port);
        return new Callable<InetSocketAddress>() {
            @Override
            public InetSocketAddress call() {
                return address;
            }
        };
    }
}

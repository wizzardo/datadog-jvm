package com.wizzardo.metrics.system;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Created by Mikhail Bobrutskov on 14.05.17.
 */
public class Utils {
    public static String exec(String cmd) {
        try {
            Process process;
            process = Runtime.getRuntime().exec(cmd);
            int result = process.waitFor();
            if (result != 0)
                throw new IllegalArgumentException("Process ended with code: " + result);

            byte[] bytes = new byte[1024];
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream in = process.getInputStream();
            int read;
            while ((read = in.read(bytes)) != -1) {
                out.write(bytes, 0, read);
            }
            in.close();
            out.close();
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }


    protected static void checkPosition(byte[] buffer, int position, int limit) {
        if (position == -1 || position > limit)
            throw new IllegalStateException("Cannot parse: " + new String(buffer, 0, limit));
    }

    public static int indexOf(byte b, byte[] bytes, int offset, int limit) {
        for (int i = offset; i < limit; i++) {
            if (bytes[i] == b)
                return i;
        }
        return -1;
    }

    public static int indexOfNot(byte b, byte[] bytes, int offset, int limit) {
        for (int i = offset; i < limit; i++) {
            if (bytes[i] != b)
                return i;
        }
        return -1;
    }

    public static int readInt(int[] holder, byte[] bytes, int offset, int limit) {
        int value = 0;
        for (int i = offset; i < limit; i++) {
            byte b = bytes[i];
            if (b >= '0' && b <= '9') {
                value = value * 10 + (b - '0');
            } else {
                holder[0] = value;
                return i + 1;
            }
        }

        holder[0] = value;
        return limit + 1;
    }

    public static int skipValue(byte[] buffer, int position, int nextLine) {
        position = indexOfNot((byte) ' ', buffer, position, nextLine);
        position = indexOf((byte) ' ', buffer, position, nextLine);
        return position;
    }

    public static int readLong(long[] holder, byte[] bytes, int offset, int limit) {
        long value = 0;
        for (int i = offset; i < limit; i++) {
            byte b = bytes[i];
            if (b >= '0' && b <= '9') {
                value = value * 10 + (b - '0');
            } else {
                holder[0] = value;
                return i + 1;
            }
        }

        holder[0] = value;
        return limit + 1;
    }

    public static long readLong(byte[] bytes, int offset, int limit) {
        long value = 0;
        for (int i = offset; i < limit; i++) {
            byte b = bytes[i];
            if (b >= '0' && b <= '9') {
                value = value * 10 + (b - '0');
            } else {
                return value;
            }
        }

        return value;
    }

    public static int read(String path, byte[] bytes) {
        try (FileInputStream in = new FileInputStream(path)) {
            return in.read(bytes);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }
}

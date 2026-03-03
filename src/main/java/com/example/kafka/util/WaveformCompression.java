package com.example.kafka.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class WaveformCompression {

    private WaveformCompression() {
    }

    public static byte[] compress(List<Double> data) {
        if (data == null) {
            throw new IllegalArgumentException("waveform data required");
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(baos);
            ObjectOutputStream oos = new ObjectOutputStream(gzip);
            double[] arr = data.stream().mapToDouble(Double::doubleValue).toArray();
            oos.writeObject(arr);
            oos.close();
            gzip.close();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("compress waveform failed", e);
        }
    }

    public static List<Double> decompress(byte[] compressed) {
        if (compressed == null) {
            return null;
        }
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
            GZIPInputStream gzip = new GZIPInputStream(bais);
            ObjectInputStream ois = new ObjectInputStream(gzip);
            double[] arr = (double[]) ois.readObject();
            ois.close();
            return Arrays.stream(arr).boxed().toList();
        } catch (Exception e) {
            throw new IllegalStateException("decompress waveform failed", e);
        }
    }
}

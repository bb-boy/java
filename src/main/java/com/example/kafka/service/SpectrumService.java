package com.example.kafka.service;

import com.example.kafka.model.SpectrumRequest;
import com.example.kafka.model.SpectrumResult;
import com.example.kafka.model.WaveformWindowRequest;
import com.example.kafka.model.WaveformWindowResult;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SpectrumService {

    private static final int DEFAULT_MAX_POINTS = 65536;
    private static final int MIN_FFT_SIZE = 2;
    private static final int HALF_DIVISOR = 2;
    private static final int INDEX_OFFSET = 1;
    private static final double WINDOW_HALF = 0.5d;
    private static final double ONE = 1.0d;
    private static final double TWO_PI = Math.PI * 2.0d;
    private static final double AMPLITUDE_SCALE = 2.0d;
    private static final double MIN_SAMPLE_RATE = 0.0d;

    private final WaveformWindowService waveformWindowService;
    private final FastFourierTransformer transformer;

    @Value("${app.spectrum.max-points:" + DEFAULT_MAX_POINTS + "}")
    private int defaultMaxPoints;

    @Autowired
    public SpectrumService(
        WaveformWindowService waveformWindowService,
        FastFourierTransformer transformer
    ) {
        this.waveformWindowService = waveformWindowService;
        this.transformer = transformer;
    }

    public SpectrumResult computeSpectrum(SpectrumRequest request) {
        WaveformWindowResult window = loadWindow(request);
        List<Double> values = window.values();
        int fftSize = resolveFftSize(values.size());
        double[] windowed = applyHann(values, fftSize);
        Complex[] spectrum = transformer.transform(windowed, TransformType.FORWARD);
        return buildResult(window, spectrum, fftSize);
    }

    private WaveformWindowResult loadWindow(SpectrumRequest request) {
        int maxPoints = request.maxPoints() != null ? request.maxPoints() : defaultMaxPoints;
        WaveformWindowRequest windowRequest = new WaveformWindowRequest(
            request.shotNo(),
            request.channelName(),
            request.dataType(),
            request.startTime(),
            request.endTime(),
            maxPoints
        );
        return waveformWindowService.getWindow(windowRequest);
    }

    private int resolveFftSize(int size) {
        int fftSize = Integer.highestOneBit(size);
        if (fftSize < MIN_FFT_SIZE) {
            throw new IllegalArgumentException("fft size too small");
        }
        return fftSize;
    }

    private double[] applyHann(List<Double> values, int fftSize) {
        double[] result = new double[fftSize];
        int denom = Math.max(fftSize - INDEX_OFFSET, INDEX_OFFSET);
        for (int i = 0; i < fftSize; i++) {
            double window = WINDOW_HALF * (ONE - Math.cos(TWO_PI * i / denom));
            result[i] = values.get(i) * window;
        }
        return result;
    }

    private SpectrumResult buildResult(
        WaveformWindowResult window,
        Complex[] spectrum,
        int fftSize
    ) {
        double sampleRate = requireSampleRate(window.sampleRate());
        int half = fftSize / HALF_DIVISOR;
        double freqStep = sampleRate / fftSize;

        List<Double> frequencies = new ArrayList<>(half);
        List<Double> magnitudes = new ArrayList<>(half);
        for (int i = 0; i < half; i++) {
            frequencies.add(i * freqStep);
            magnitudes.add(spectrum[i].abs() * AMPLITUDE_SCALE / fftSize);
        }

        return new SpectrumResult(
            window.shotNo(),
            window.channelName(),
            window.dataType(),
            window.startTime(),
            window.endTime(),
            sampleRate,
            fftSize,
            frequencies,
            magnitudes
        );
    }

    private double requireSampleRate(Double sampleRate) {
        if (sampleRate == null || sampleRate <= MIN_SAMPLE_RATE) {
            throw new IllegalStateException("sampleRate missing");
        }
        return sampleRate;
    }
}

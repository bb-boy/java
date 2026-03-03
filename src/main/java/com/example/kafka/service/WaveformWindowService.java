package com.example.kafka.service;

import com.example.kafka.consumer.DataConsumer;
import com.example.kafka.entity.WaveDataEntity;
import com.example.kafka.model.WaveformStats;
import com.example.kafka.model.WaveformWindowRequest;
import com.example.kafka.model.WaveformWindowResult;
import com.example.kafka.repository.WaveDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class WaveformWindowService {

    private static final int DEFAULT_MAX_POINTS = 50000;
    private static final int MIN_INDEX = 0;
    private static final int INDEX_OFFSET = 1;
    private static final int MIN_STEP = 1;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final double ZERO_DOUBLE = 0.0d;

    private final WaveDataRepository waveDataRepository;

    @Value("${app.waveform.window.max-points:" + DEFAULT_MAX_POINTS + "}")
    private int defaultMaxPoints;

    @Autowired
    public WaveformWindowService(WaveDataRepository waveDataRepository) {
        this.waveDataRepository = waveDataRepository;
    }

    public WaveformWindowResult getWindow(WaveformWindowRequest request) {
        validateRequest(request);
        WaveDataEntity entity = loadEntity(request);
        List<Double> values = decompress(entity);
        IndexRange range = resolveRange(request, entity, values.size());
        int maxPoints = resolveMaxPoints(request.maxPoints(), range.size());
        List<Double> windowValues = downsample(values, range, maxPoints);
        WaveformStats stats = computeStats(values, range);
        return new WaveformWindowResult(
            request.shotNo(),
            request.channelName(),
            request.dataType(),
            request.startTime(),
            request.endTime(),
            entity.getSampleRate(),
            values.size(),
            range.startIndex(),
            range.endIndex(),
            windowValues,
            stats
        );
    }

    private void validateRequest(WaveformWindowRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request required");
        }
        if (request.shotNo() == null || request.channelName() == null) {
            throw new IllegalArgumentException("shotNo and channelName required");
        }
        if (request.dataType() == null) {
            throw new IllegalArgumentException("dataType required");
        }
        if (request.startTime() != null && request.endTime() != null
            && request.endTime().isBefore(request.startTime())) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
    }

    private WaveDataEntity loadEntity(WaveformWindowRequest request) {
        return waveDataRepository
            .findByShotNoAndChannelNameAndDataType(
                request.shotNo(),
                request.channelName(),
                request.dataType()
            )
            .orElseThrow(() -> new IllegalStateException("waveform not found"));
    }

    private List<Double> decompress(WaveDataEntity entity) {
        List<Double> data = DataConsumer.decompressWaveData(entity.getData());
        if (data == null || data.isEmpty()) {
            throw new IllegalStateException("waveform data empty");
        }
        return data;
    }

    private IndexRange resolveRange(WaveformWindowRequest request, WaveDataEntity entity, int size) {
        if (request.startTime() == null || request.endTime() == null) {
            return new IndexRange(MIN_INDEX, size - INDEX_OFFSET);
        }
        if (entity.getStartTime() == null || entity.getSampleRate() == null) {
            throw new IllegalStateException("startTime or sampleRate missing");
        }
        int startIndex = toIndex(entity.getStartTime(), request.startTime(), entity.getSampleRate());
        int endIndex = toIndex(entity.getStartTime(), request.endTime(), entity.getSampleRate());
        return clampRange(startIndex, endIndex, size);
    }

    private int toIndex(LocalDateTime baseTime, LocalDateTime target, double sampleRate) {
        long nanos = Duration.between(baseTime, target).toNanos();
        double seconds = nanos / (double) NANOS_PER_SECOND;
        return (int) Math.floor(seconds * sampleRate);
    }

    private IndexRange clampRange(int startIndex, int endIndex, int size) {
        int maxIndex = size - INDEX_OFFSET;
        int start = Math.max(MIN_INDEX, Math.min(startIndex, maxIndex));
        int end = Math.max(MIN_INDEX, Math.min(endIndex, maxIndex));
        if (end < start) {
            return new IndexRange(start, start);
        }
        return new IndexRange(start, end);
    }

    private int resolveMaxPoints(Integer requested, int count) {
        int maxPoints = requested != null ? requested : defaultMaxPoints;
        if (maxPoints < MIN_STEP) {
            return Math.min(count, defaultMaxPoints);
        }
        return Math.min(count, maxPoints);
    }

    private List<Double> downsample(List<Double> data, IndexRange range, int maxPoints) {
        int count = range.size();
        int step = (int) Math.ceil(count / (double) maxPoints);
        step = Math.max(step, MIN_STEP);

        List<Double> result = new ArrayList<>(Math.min(count, maxPoints));
        int index = range.startIndex();
        while (index <= range.endIndex()) {
            result.add(data.get(index));
            index += step;
        }
        return result;
    }

    private WaveformStats computeStats(List<Double> data, IndexRange range) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sumSquares = ZERO_DOUBLE;
        int count = range.size();
        for (int i = range.startIndex(); i <= range.endIndex(); i++) {
            double value = data.get(i);
            min = Math.min(min, value);
            max = Math.max(max, value);
            sumSquares += value * value;
        }
        double rms = Math.sqrt(sumSquares / count);
        return new WaveformStats(min, max, rms, max - min);
    }

    private record IndexRange(int startIndex, int endIndex) {
        int size() {
            return endIndex - startIndex + INDEX_OFFSET;
        }
    }
}

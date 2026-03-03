package com.example.kafka.service;

import com.example.kafka.entity.WaveformMetadataEntity;
import com.example.kafka.entity.WaveformSampleEntity;
import com.example.kafka.model.WaveformSegmentRequest;
import com.example.kafka.repository.WaveformMetadataRepository;
import com.example.kafka.repository.WaveformSampleRepository;
import com.example.kafka.util.WaveformCompression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WaveformSegmentService {

    private static final Logger logger = LoggerFactory.getLogger(WaveformSegmentService.class);

    private static final int DEFAULT_SEGMENT_POINTS = 10000;
    private static final int MIN_SEGMENT_POINTS = 1;
    private static final int INDEX_OFFSET = 1;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final double MIN_SAMPLE_RATE = 0.0d;
    private static final double ZERO_DOUBLE = 0.0d;
    private static final String DEFAULT_SYSTEM_NAME = "ECRH";
    private static final String DEFAULT_ENCODING = "gzip-f64";
    private static final String DEFAULT_DEVICE_ID = "UNKNOWN";

    private final WaveformMetadataRepository metadataRepository;
    private final WaveformSampleRepository sampleRepository;
    private final ConcurrentHashMap<Long, Object> waveformLocks = new ConcurrentHashMap<>();

    @Value("${app.waveform.segment.max-points:" + DEFAULT_SEGMENT_POINTS + "}")
    private int segmentPoints;

    @Autowired
    public WaveformSegmentService(
        WaveformMetadataRepository metadataRepository,
        WaveformSampleRepository sampleRepository
    ) {
        this.metadataRepository = metadataRepository;
        this.sampleRepository = sampleRepository;
    }

    @Transactional
    public void upsertMetadataAndSegments(WaveformSegmentRequest request) {
        if (request == null) {
            return;
        }
        List<Double> values = request.values();
        if (values == null || values.isEmpty()) {
            return;
        }
        Double sampleRate = request.sampleRate();
        if (sampleRate == null || sampleRate <= MIN_SAMPLE_RATE) {
            logger.warn("波形采样率无效,跳过分段: shotNo={}, channel={}",
                request.shotNo(), request.channelName());
            return;
        }
        LocalDateTime startTime = resolveStartTime(request);
        LocalDateTime endTime = resolveEndTime(request, startTime);
        if (startTime == null || endTime == null) {
            logger.warn("波形时间轴缺失,跳过分段: shotNo={}, channel={}",
                request.shotNo(), request.channelName());
            return;
        }
        WaveformMetadataEntity metadata = upsertMetadata(request, startTime, endTime);
        if (metadata == null) {
            return;
        }
        replaceSegments(metadata.getWaveformId(), request, startTime);
    }

    private LocalDateTime resolveStartTime(WaveformSegmentRequest request) {
        LocalDateTime startTime = request.startTime();
        if (startTime != null) {
            return startTime;
        }
        LocalDateTime endTime = request.endTime();
        if (endTime == null) {
            return null;
        }
        int totalSamples = request.values() != null ? request.values().size() : 0;
        if (totalSamples < MIN_SEGMENT_POINTS) {
            return null;
        }
        long nanos = samplesToNanos(totalSamples - INDEX_OFFSET, request.sampleRate());
        return endTime.minusNanos(nanos);
    }

    private LocalDateTime resolveEndTime(WaveformSegmentRequest request, LocalDateTime startTime) {
        LocalDateTime endTime = request.endTime();
        if (endTime != null) {
            return endTime;
        }
        if (startTime == null) {
            return null;
        }
        int totalSamples = request.values() != null ? request.values().size() : 0;
        if (totalSamples < MIN_SEGMENT_POINTS) {
            return null;
        }
        long nanos = samplesToNanos(totalSamples - INDEX_OFFSET, request.sampleRate());
        return startTime.plusNanos(nanos);
    }

    private long samplesToNanos(long samples, Double sampleRate) {
        double seconds = samples / sampleRate;
        return Math.round(seconds * NANOS_PER_SECOND);
    }

    private WaveformMetadataEntity upsertMetadata(
        WaveformSegmentRequest request,
        LocalDateTime startTime,
        LocalDateTime endTime
    ) {
        String deviceId = resolveDeviceId(request);
        if (deviceId == null) {
            logger.warn("波形设备ID缺失,跳过元数据: shotNo={}, channel={}",
                request.shotNo(), request.channelName());
            return null;
        }
        WaveformMetadataEntity metadata = metadataRepository
            .findByShotNoAndDeviceIdAndChannelName(request.shotNo(), deviceId, request.channelName())
            .orElse(new WaveformMetadataEntity());
        metadata.setShotNo(request.shotNo());
        metadata.setSystemName(DEFAULT_SYSTEM_NAME);
        metadata.setDeviceId(deviceId);
        metadata.setChannelName(request.channelName());
        metadata.setUnit(request.unit());
        metadata.setSampleRateHz(request.sampleRate());
        metadata.setStartTime(startTime);
        metadata.setEndTime(endTime);
        metadata.setTotalSamples((long) request.values().size());
        metadata.setTags(buildTags(request, deviceId));
        return metadataRepository.save(metadata);
    }

    private String resolveDeviceId(WaveformSegmentRequest request) {
        if (request.deviceId() != null && !request.deviceId().isBlank()) {
            return request.deviceId();
        }
        if (request.dataType() != null && !request.dataType().isBlank()) {
            return request.dataType();
        }
        return DEFAULT_DEVICE_ID;
    }

    private String buildTags(WaveformSegmentRequest request, String deviceId) {
        if (request.dataType() == null && request.fileSource() == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        builder.append("\"deviceId\":\"").append(deviceId).append('"');
        if (request.dataType() != null) {
            builder.append(",\"dataType\":\"").append(request.dataType()).append('"');
        }
        if (request.fileSource() != null) {
            builder.append(",\"fileSource\":\"").append(request.fileSource()).append('"');
        }
        builder.append('}');
        return builder.toString();
    }

    private void replaceSegments(
        Long waveformId,
        WaveformSegmentRequest request,
        LocalDateTime startTime
    ) {
        if (waveformId == null) {
            return;
        }
        Object lock = waveformLocks.computeIfAbsent(waveformId, id -> new Object());
        synchronized (lock) {
            sampleRepository.deleteByWaveformId(waveformId);
            sampleRepository.flush();
            SegmentContext context = new SegmentContext(
                waveformId,
                request.shotNo(),
                request.sampleRate(),
                startTime,
                request.values(),
                resolveSegmentPoints(request.values().size())
            );
            List<WaveformSampleEntity> segments = buildSegments(context);
            if (!segments.isEmpty()) {
                sampleRepository.saveAll(segments);
                sampleRepository.flush();
            }
        }
    }

    private int resolveSegmentPoints(int totalSamples) {
        int points = Math.max(segmentPoints, MIN_SEGMENT_POINTS);
        if (points > totalSamples) {
            return totalSamples;
        }
        return points;
    }

    private List<WaveformSampleEntity> buildSegments(SegmentContext context) {
        List<WaveformSampleEntity> segments = new ArrayList<>();
        int totalSamples = context.values().size();
        int segmentIndex = 0;
        int start = 0;
        while (start < totalSamples) {
            int endExclusive = Math.min(start + context.segmentPoints, totalSamples);
            SegmentStats stats = computeStats(context.values(), start, endExclusive);
            WaveformSampleEntity segment = new WaveformSampleEntity();
            segment.setWaveformId(context.waveformId);
            segment.setShotNo(context.shotNo);
            segment.setSegmentIndex(segmentIndex);
            segment.setStartSampleIndex((long) start);
            segment.setSampleCount(endExclusive - start);
            segment.setSegmentStartTime(timeAtIndex(context.startTime, context.sampleRate, start));
            segment.setSegmentEndTime(timeAtIndex(context.startTime, context.sampleRate, endExclusive - INDEX_OFFSET));
            segment.setEncoding(DEFAULT_ENCODING);
            segment.setDataBlob(WaveformCompression.compress(context.values().subList(start, endExclusive)));
            segment.setVMin(stats.min);
            segment.setVMax(stats.max);
            segment.setVRms(stats.rms);
            segments.add(segment);
            start = endExclusive;
            segmentIndex++;
        }
        return segments;
    }

    private SegmentStats computeStats(List<Double> values, int start, int endExclusive) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sumSquares = ZERO_DOUBLE;
        for (int i = start; i < endExclusive; i++) {
            double value = values.get(i);
            min = Math.min(min, value);
            max = Math.max(max, value);
            sumSquares += value * value;
        }
        int count = endExclusive - start;
        double rms = Math.sqrt(sumSquares / count);
        return new SegmentStats(min, max, rms);
    }

    private LocalDateTime timeAtIndex(LocalDateTime startTime, Double sampleRate, int index) {
        long nanos = samplesToNanos(index, sampleRate);
        return startTime.plusNanos(nanos);
    }

    private record SegmentStats(double min, double max, double rms) {
    }

    private record SegmentContext(
        Long waveformId,
        Integer shotNo,
        Double sampleRate,
        LocalDateTime startTime,
        List<Double> values,
        int segmentPoints
    ) {
    }
}

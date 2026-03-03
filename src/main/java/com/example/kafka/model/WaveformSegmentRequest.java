package com.example.kafka.model;

import java.time.LocalDateTime;
import java.util.List;

public record WaveformSegmentRequest(
    Integer shotNo,
    String deviceId,
    String channelName,
    String dataType,
    String fileSource,
    Double sampleRate,
    LocalDateTime startTime,
    LocalDateTime endTime,
    List<Double> values,
    String unit
) {}

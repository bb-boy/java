package com.example.kafka.model;

import java.time.LocalDateTime;
import java.util.List;

public record WaveformWindowResult(
    Integer shotNo,
    String channelName,
    String dataType,
    LocalDateTime startTime,
    LocalDateTime endTime,
    Double sampleRate,
    Integer totalSamples,
    Integer startIndex,
    Integer endIndex,
    List<Double> values,
    WaveformStats stats
) {}

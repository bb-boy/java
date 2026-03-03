package com.example.kafka.model;

import java.time.LocalDateTime;
import java.util.List;

public record SpectrumResult(
    Integer shotNo,
    String channelName,
    String dataType,
    LocalDateTime startTime,
    LocalDateTime endTime,
    Double sampleRate,
    Integer fftSize,
    List<Double> frequencies,
    List<Double> magnitudes
) {}

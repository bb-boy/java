package com.example.kafka.model;

import java.time.LocalDateTime;

public record SpectrumRequest(
    Integer shotNo,
    String channelName,
    String dataType,
    LocalDateTime startTime,
    LocalDateTime endTime,
    Integer maxPoints
) {}

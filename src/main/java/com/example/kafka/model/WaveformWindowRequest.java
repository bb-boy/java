package com.example.kafka.model;

import java.time.LocalDateTime;

public record WaveformWindowRequest(
    Integer shotNo,
    String channelName,
    String dataType,
    LocalDateTime startTime,
    LocalDateTime endTime,
    Integer maxPoints
) {}

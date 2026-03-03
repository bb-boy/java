package com.example.kafka.model;

import java.time.LocalDateTime;

public record SyntheticGenerationResult(
    Integer shotNo,
    String channelName,
    String dataType,
    Integer operationCount,
    Integer protectionCount,
    Double protectionRatio,
    Double thresholdValue,
    LocalDateTime startTime,
    LocalDateTime endTime
) {}

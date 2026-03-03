package com.example.kafka.model;

public record SyntheticGenerationRequest(
    Integer shotNo,
    String channelName,
    String dataType,
    Integer operationCount,
    Integer protectionLimit,
    Double protectionRatio,
    Boolean replaceExisting
) {}

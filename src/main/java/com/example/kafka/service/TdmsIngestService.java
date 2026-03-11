package com.example.kafka.service;

import com.example.kafka.model.DerivedPayload;
import org.springframework.stereotype.Service;

@Service
public class TdmsIngestService {
    private static final int MIN_SHOT_NO = 0;

    private final DerivedOutputReader outputReader;
    private final DerivedKafkaPublisher kafkaPublisher;

    public TdmsIngestService(
        DerivedOutputReader outputReader,
        DerivedKafkaPublisher kafkaPublisher
    ) {
        this.outputReader = outputReader;
        this.kafkaPublisher = kafkaPublisher;
    }

    public void ingestShot(Integer shotNo) {
        validateShotNo(shotNo);
        DerivedPayload payload = outputReader.read(shotNo);
        kafkaPublisher.publish(payload);
    }

    private void validateShotNo(Integer shotNo) {
        if (shotNo == null || shotNo < MIN_SHOT_NO) {
            throw new IllegalArgumentException("Shot number is required");
        }
    }
}

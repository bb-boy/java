package com.example.kafka.consumer;

import com.example.kafka.config.TopicNames;
import com.example.kafka.service.WaveformProjectionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class WaveformConsumer {
    private final KafkaRecordProcessor processor;
    private final WaveformProjectionService projectionService;

    public WaveformConsumer(
        KafkaRecordProcessor processor,
        WaveformProjectionService projectionService
    ) {
        this.processor = processor;
        this.projectionService = projectionService;
    }

    @KafkaListener(topics = TopicNames.WAVEFORM_INGEST_REQUEST, groupId = "${app.kafka.group-id}")
    public void onWaveformRequest(ConsumerRecord<String, String> record, Acknowledgment ack) {
        processor.processRecord(record, ack, projectionService::handleWaveformRequest);
    }

    @KafkaListener(topics = TopicNames.WAVEFORM_CHANNEL_BATCH, groupId = "${app.kafka.group-id}")
    public void onWaveformBatch(ConsumerRecord<String, String> record, Acknowledgment ack) {
        processor.processRecord(record, ack, projectionService::handleWaveformBatch);
    }
}

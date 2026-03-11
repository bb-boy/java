package com.example.kafka.consumer;

import com.example.kafka.config.TopicNames;
import com.example.kafka.service.ArtifactProjectionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class ArtifactConsumer {
    private final KafkaRecordProcessor processor;
    private final ArtifactProjectionService projectionService;

    public ArtifactConsumer(
        KafkaRecordProcessor processor,
        ArtifactProjectionService projectionService
    ) {
        this.processor = processor;
        this.projectionService = projectionService;
    }

    @KafkaListener(topics = TopicNames.TDMS_ARTIFACT, groupId = "${app.kafka.group-id}")
    public void onArtifact(ConsumerRecord<String, String> record, Acknowledgment ack) {
        processor.processRecord(record, ack, projectionService::handleArtifact);
    }

    @KafkaListener(topics = TopicNames.SHOT_META, groupId = "${app.kafka.group-id}")
    public void onShotMeta(ConsumerRecord<String, String> record, Acknowledgment ack) {
        processor.processRecord(record, ack, projectionService::handleShotMeta);
    }

    @KafkaListener(topics = TopicNames.SIGNAL_CATALOG, groupId = "${app.kafka.group-id}")
    public void onSignalCatalog(ConsumerRecord<String, String> record, Acknowledgment ack) {
        processor.processRecord(record, ack, projectionService::handleSignalCatalog);
    }
}
